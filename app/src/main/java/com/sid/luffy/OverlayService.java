package com.sid.luffy;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.Icon;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.provider.Settings;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.webkit.JavascriptInterface;
import android.webkit.PermissionRequest;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Keeps Luffy alive as a floating bubble above other apps.
 * Tap the bubble to open a chat panel (a WebView running the Luffy page).
 */
public class OverlayService extends Service {

    static final String ACTION_START = "com.sid.luffy.START";
    static final String ACTION_EXPAND = "com.sid.luffy.EXPAND";
    static final String ACTION_COLLAPSE = "com.sid.luffy.COLLAPSE";
    static final String ACTION_TOGGLE_MUTE = "com.sid.luffy.TOGGLE_MUTE";
    static final String ACTION_STOP = "com.sid.luffy.STOP";

    private static final String CHANNEL = "luffy_overlay";
    private static final int NOTIF_ID = 1;
    private static final String HOST = "appassets.androidplatform.net";
    // ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE (Android 14+)
    private static final int FGS_SPECIAL_USE = 0x40000000;

    private final Handler ui = new Handler(Looper.getMainLooper());
    private WindowManager wm;
    private SharedPreferences sp;

    private FrameLayout bubble;
    private ImageView face;
    private ImageView badge;
    private WindowManager.LayoutParams bubbleLp;

    private TextView toast;
    private WindowManager.LayoutParams toastLp;
    private boolean toastAttached = false;
    private final Runnable hideToastRunnable = new Runnable() {
        @Override
        public void run() {
            hideToastNow();
        }
    };

    private PanelLayout panel;
    private WebView web;
    private WindowManager.LayoutParams panelLp;

    private boolean built = false;
    private boolean expanded = false;

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        wm = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
        sp = getSharedPreferences("luffy", Context.MODE_PRIVATE);
        createChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // Must be called quickly after startForegroundService().
        startAsForeground();

        String action = intent != null ? intent.getAction() : ACTION_START;

        if (ACTION_STOP.equals(action)) {
            stopSelf();
            return START_NOT_STICKY;
        }

        if (!Settings.canDrawOverlays(this)) {
            if (ACTION_TOGGLE_MUTE.equals(action)) {
                boolean muted = !sp.getBoolean("muted", true);
                sp.edit().putBoolean("muted", muted).apply();
                LuffyWidgetProvider.refresh(this);
            }
            stopSelf();
            return START_NOT_STICKY;
        }

        if (!built) {
            buildViews();
            built = true;
        }

        if (ACTION_EXPAND.equals(action)) {
            expand();
        } else if (ACTION_COLLAPSE.equals(action)) {
            collapse();
        } else if (ACTION_TOGGLE_MUTE.equals(action)) {
            toggleMute();
        }
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        ui.removeCallbacksAndMessages(null);
        try {
            if (toastAttached) wm.removeView(toast);
        } catch (Exception ignored) {
        }
        try {
            if (bubble != null) wm.removeView(bubble);
        } catch (Exception ignored) {
        }
        try {
            if (panel != null) wm.removeView(panel);
        } catch (Exception ignored) {
        }
        if (web != null) {
            web.destroy();
            web = null;
        }
        super.onDestroy();
    }

    // ------------------------------------------------------------------ notification

    private void createChannel() {
        NotificationChannel ch = new NotificationChannel(CHANNEL, "Luffy", NotificationManager.IMPORTANCE_LOW);
        ch.setDescription("Keeps Luffy available on your screen");
        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        nm.createNotificationChannel(ch);
    }

    private PendingIntent actionIntent(String action, int rc) {
        Intent i = new Intent(this, OverlayService.class).setAction(action);
        return PendingIntent.getService(this, rc, i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private Notification buildNotification() {
        boolean muted = sp.getBoolean("muted", true);
        Notification.Action muteAction = new Notification.Action.Builder(
                Icon.createWithResource(this, muted ? R.drawable.ic_vol_off : R.drawable.ic_vol_on),
                muted ? "Unmute" : "Mute",
                actionIntent(ACTION_TOGGLE_MUTE, 11)).build();
        Notification.Action stopAction = new Notification.Action.Builder(
                Icon.createWithResource(this, R.drawable.ic_stat),
                "Stop Luffy",
                actionIntent(ACTION_STOP, 12)).build();
        return new Notification.Builder(this, CHANNEL)
                .setSmallIcon(R.drawable.ic_stat)
                .setContentTitle("Luffy is on your screen")
                .setContentText(muted ? "Muted. Tap to chat." : "Voice on. Tap to chat.")
                .setContentIntent(actionIntent(ACTION_EXPAND, 10))
                .setOngoing(true)
                .addAction(muteAction)
                .addAction(stopAction)
                .build();
    }

    private void startAsForeground() {
        Notification n = buildNotification();
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIF_ID, n, FGS_SPECIAL_USE);
        } else {
            startForeground(NOTIF_ID, n);
        }
    }

    private void refreshNotification() {
        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        nm.notify(NOTIF_ID, buildNotification());
    }

    // ------------------------------------------------------------------ helpers

    private int dp(int v) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, getResources().getDisplayMetrics());
    }

    private DisplayMetrics metrics() {
        return getResources().getDisplayMetrics();
    }

    private static int faceFor(String mood) {
        if ("sleepy".equals(mood)) return R.drawable.av_sleepy;
        if ("focus".equals(mood)) return R.drawable.av_focus;
        if ("touched".equals(mood)) return R.drawable.av_touched;
        return R.drawable.av_happy;
    }

    private static String mimeFor(String path) {
        String p = path.toLowerCase();
        if (p.endsWith(".html")) return "text/html";
        if (p.endsWith(".js")) return "application/javascript";
        if (p.endsWith(".css")) return "text/css";
        if (p.endsWith(".png")) return "image/png";
        if (p.endsWith(".webp")) return "image/webp";
        if (p.endsWith(".json") || p.endsWith(".webmanifest")) return "application/json";
        return "application/octet-stream";
    }

    // ------------------------------------------------------------------ views

    private void buildViews() {
        final int bubbleSize = dp(64);
        DisplayMetrics dm = metrics();

        // ---- floating bubble
        bubble = new FrameLayout(this);
        face = new ImageView(this);
        face.setScaleType(ImageView.ScaleType.CENTER_CROP);
        GradientDrawable faceBg = new GradientDrawable();
        faceBg.setShape(GradientDrawable.OVAL);
        faceBg.setColor(0xFF202A36);
        faceBg.setStroke(dp(2), 0xFF8FB8C9);
        face.setBackground(faceBg);
        face.setClipToOutline(true);
        face.setImageResource(faceFor(sp.getString("mood", "happy")));
        bubble.addView(face, new FrameLayout.LayoutParams(bubbleSize, bubbleSize));

        badge = new ImageView(this);
        badge.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        int bp = dp(5);
        badge.setPadding(bp, bp, bp, bp);
        GradientDrawable badgeBg = new GradientDrawable();
        badgeBg.setShape(GradientDrawable.OVAL);
        badgeBg.setColor(0xFF18202A);
        badgeBg.setStroke(dp(1), 0xFF2C3848);
        badge.setBackground(badgeBg);
        FrameLayout.LayoutParams blp = new FrameLayout.LayoutParams(dp(26), dp(26), Gravity.BOTTOM | Gravity.END);
        bubble.addView(badge, blp);
        updateMuteUi();

        bubbleLp = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT);
        bubbleLp.gravity = Gravity.TOP | Gravity.START;
        bubbleLp.x = sp.getInt("bx", dm.widthPixels - bubbleSize - dp(8));
        bubbleLp.y = sp.getInt("by", (int) (dm.heightPixels * 0.55f));
        bubble.setOnTouchListener(new BubbleTouch(bubbleSize));
        wm.addView(bubble, bubbleLp);

        // ---- speech toast (created on demand)
        toast = new TextView(this);
        toast.setTextColor(0xFFE4EAF0);
        toast.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        toast.setMaxWidth(dp(240));
        toast.setMaxLines(4);
        toast.setEllipsize(android.text.TextUtils.TruncateAt.END);
        toast.setPadding(dp(12), dp(10), dp(12), dp(10));
        GradientDrawable toastBg = new GradientDrawable();
        toastBg.setColor(0xFF18202A);
        toastBg.setStroke(dp(1), 0xFF2C3848);
        toastBg.setCornerRadius(dp(14));
        toast.setBackground(toastBg);
        toast.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                expand();
            }
        });
        toastLp = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT);

        // ---- chat panel (WebView), kept alive but collapsed to 1x1 when hidden
        panel = new PanelLayout(this);
        GradientDrawable panelBg = new GradientDrawable();
        panelBg.setColor(0xFF18202A);
        panelBg.setCornerRadius(dp(20));
        panel.setBackground(panelBg);
        panel.setClipToOutline(true);

        web = new WebView(this);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setAllowFileAccess(false);
        s.setAllowContentAccess(false);
        web.setBackgroundColor(0xFF18202A);
        web.addJavascriptInterface(new Bridge(), "LuffyNative");
        web.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onPermissionRequest(final PermissionRequest request) {
                ui.post(new Runnable() {
                    @Override
                    public void run() {
                        if (checkSelfPermission(android.Manifest.permission.RECORD_AUDIO)
                                == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                            request.grant(request.getResources());
                        } else {
                            request.deny();
                        }
                    }
                });
            }
        });
        web.setWebViewClient(new WebViewClient() {
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest req) {
                Uri u = req.getUrl();
                if (HOST.equals(u.getHost())) {
                    String path = u.getPath();
                    if (path == null || path.equals("/") || path.isEmpty()) path = "/index.html";
                    try {
                        InputStream in = getAssets().open(path.substring(1));
                        return new WebResourceResponse(mimeFor(path), "UTF-8", in);
                    } catch (IOException e) {
                        return new WebResourceResponse("text/plain", "UTF-8", 404, "Not Found", null,
                                new ByteArrayInputStream(new byte[0]));
                    }
                }
                return null;
            }
        });
        panel.addView(web, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        panelLp = new WindowManager.LayoutParams(
                1, 1,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                hiddenFlags(),
                PixelFormat.TRANSLUCENT);
        panelLp.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
        panelLp.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE;
        wm.addView(panel, panelLp);
        panel.setAlpha(0f);

        web.loadUrl("https://" + HOST + "/index.html");
    }

    private int hiddenFlags() {
        return WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL;
    }

    private int shownFlags() {
        return WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                | WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH;
    }

    // ------------------------------------------------------------------ expand / collapse

    private void expand() {
        if (!built || expanded) return;
        expanded = true;
        hideToastNow();
        DisplayMetrics dm = metrics();
        panelLp.width = Math.min(dm.widthPixels - dp(16), dp(480));
        panelLp.height = Math.min((int) (dm.heightPixels * 0.80f), dp(720));
        panelLp.y = dp(12);
        panelLp.flags = shownFlags();
        panel.setAlpha(1f);
        wm.updateViewLayout(panel, panelLp);
        bubble.setVisibility(View.GONE);
        web.requestFocus();
    }

    private void collapse() {
        if (!built || !expanded) return;
        expanded = false;
        try {
            InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            imm.hideSoftInputFromWindow(web.getWindowToken(), 0);
        } catch (Exception ignored) {
        }
        panelLp.width = 1;
        panelLp.height = 1;
        panelLp.flags = hiddenFlags();
        panel.setAlpha(0f);
        wm.updateViewLayout(panel, panelLp);
        bubble.setVisibility(View.VISIBLE);
    }

    // ------------------------------------------------------------------ mute

    private void updateMuteUi() {
        boolean muted = sp.getBoolean("muted", true);
        if (badge != null) {
            badge.setImageResource(muted ? R.drawable.ic_vol_off : R.drawable.ic_vol_on);
        }
        refreshNotification();
    }

    private void toggleMute() {
        boolean muted = !sp.getBoolean("muted", true);
        sp.edit().putBoolean("muted", muted).apply();
        updateMuteUi();
        LuffyWidgetProvider.refresh(this);
        if (web != null) {
            web.evaluateJavascript("window.__nativeMute&&window.__nativeMute(" + muted + ")", null);
        }
    }

    // ------------------------------------------------------------------ toast

    private void showToast(String text) {
        if (expanded || text == null || text.trim().isEmpty() || bubble == null) return;
        String t = text.length() > 160 ? text.substring(0, 157) + "\u2026" : text;
        toast.setText(t);
        DisplayMetrics dm = metrics();
        boolean bubbleOnRight = bubbleLp.x + dp(32) > dm.widthPixels / 2;
        if (bubbleOnRight) {
            toastLp.gravity = Gravity.TOP | Gravity.END;
            toastLp.x = dm.widthPixels - bubbleLp.x + dp(8);
        } else {
            toastLp.gravity = Gravity.TOP | Gravity.START;
            toastLp.x = bubbleLp.x + dp(64) + dp(8);
        }
        toastLp.y = bubbleLp.y + dp(6);
        try {
            if (toastAttached) {
                wm.updateViewLayout(toast, toastLp);
            } else {
                wm.addView(toast, toastLp);
                toastAttached = true;
            }
        } catch (Exception ignored) {
        }
        ui.removeCallbacks(hideToastRunnable);
        ui.postDelayed(hideToastRunnable, 7000);
    }

    private void hideToastNow() {
        ui.removeCallbacks(hideToastRunnable);
        if (toastAttached) {
            try {
                wm.removeView(toast);
            } catch (Exception ignored) {
            }
            toastAttached = false;
        }
    }

    // ------------------------------------------------------------------ touch + layout classes

    /** Drags the bubble; a tap opens the panel, a tap on the speaker badge toggles mute. */
    private class BubbleTouch implements View.OnTouchListener {
        private final int size;
        private final int slop = ViewConfiguration.get(OverlayService.this).getScaledTouchSlop();
        private float downX, downY;
        private int startX, startY;
        private boolean moved;

        BubbleTouch(int size) {
            this.size = size;
        }

        @Override
        public boolean onTouch(View v, MotionEvent e) {
            DisplayMetrics dm = metrics();
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    startX = bubbleLp.x;
                    startY = bubbleLp.y;
                    downX = e.getRawX();
                    downY = e.getRawY();
                    moved = false;
                    return true;
                case MotionEvent.ACTION_MOVE: {
                    float mx = e.getRawX() - downX;
                    float my = e.getRawY() - downY;
                    if (!moved && Math.hypot(mx, my) > slop) moved = true;
                    if (moved) {
                        hideToastNow();
                        bubbleLp.x = clamp((int) (startX + mx), 0, dm.widthPixels - size);
                        bubbleLp.y = clamp((int) (startY + my), 0, dm.heightPixels - size);
                        wm.updateViewLayout(bubble, bubbleLp);
                    }
                    return true;
                }
                case MotionEvent.ACTION_UP:
                    if (!moved) {
                        float x = e.getX();
                        float y = e.getY();
                        if (x >= badge.getLeft() && x <= badge.getRight() && y >= badge.getTop() && y <= badge.getBottom()) {
                            toggleMute();
                        } else {
                            expand();
                        }
                    } else {
                        bubbleLp.x = (bubbleLp.x + size / 2 < dm.widthPixels / 2) ? 0 : dm.widthPixels - size;
                        wm.updateViewLayout(bubble, bubbleLp);
                        sp.edit().putInt("bx", bubbleLp.x).putInt("by", bubbleLp.y).apply();
                    }
                    return true;
                default:
                    return false;
            }
        }
    }

    private static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    /** Panel container: Back key and a tap outside both collapse it. */
    private class PanelLayout extends FrameLayout {
        PanelLayout(Context c) {
            super(c);
        }

        @Override
        public boolean dispatchKeyEvent(KeyEvent event) {
            if (event.getKeyCode() == KeyEvent.KEYCODE_BACK) {
                if (event.getAction() == KeyEvent.ACTION_UP) collapse();
                return true;
            }
            return super.dispatchKeyEvent(event);
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            if (event.getAction() == MotionEvent.ACTION_OUTSIDE) {
                collapse();
                return true;
            }
            return super.onTouchEvent(event);
        }
    }

    // ------------------------------------------------------------------ JS bridge

    /** Methods the Luffy page calls through window.LuffyNative. */
    private class Bridge {
        @JavascriptInterface
        public boolean isMuted() {
            return sp.getBoolean("muted", true);
        }

        @JavascriptInterface
        public void setMuted(final boolean muted) {
            sp.edit().putBoolean("muted", muted).apply();
            ui.post(new Runnable() {
                @Override
                public void run() {
                    updateMuteUi();
                    LuffyWidgetProvider.refresh(OverlayService.this);
                }
            });
        }

        /** False when muted, or while a call or meeting is using the audio system. */
        @JavascriptInterface
        public boolean canSpeak() {
            if (sp.getBoolean("muted", true)) return false;
            AudioManager am = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
            int mode = am.getMode();
            return mode != AudioManager.MODE_IN_CALL
                    && mode != AudioManager.MODE_IN_COMMUNICATION
                    && mode != AudioManager.MODE_RINGTONE;
        }

        @JavascriptInterface
        public void setMood(final String mood) {
            sp.edit().putString("mood", mood).apply();
            ui.post(new Runnable() {
                @Override
                public void run() {
                    if (face != null) face.setImageResource(faceFor(mood));
                    LuffyWidgetProvider.refresh(OverlayService.this);
                }
            });
        }

        /** Luffy said something: remember it for the widget and show a preview if collapsed. */
        @JavascriptInterface
        public void say(final String text) {
            sp.edit().putString("text", text).apply();
            ui.post(new Runnable() {
                @Override
                public void run() {
                    LuffyWidgetProvider.refresh(OverlayService.this);
                    showToast(text);
                }
            });
        }

        @JavascriptInterface
        public void collapse() {
            ui.post(new Runnable() {
                @Override
                public void run() {
                    OverlayService.this.collapse();
                }
            });
        }

        @JavascriptInterface
        public boolean isExpanded() {
            return expanded;
        }
    }
}
