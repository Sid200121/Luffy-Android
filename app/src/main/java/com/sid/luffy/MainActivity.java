package com.sid.luffy;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

/** Simple setup screen: grant the permissions, then start Luffy. */
public class MainActivity extends Activity {

    private static final int REQ_MIC = 1;
    private static final int REQ_NOTIF = 2;

    private TextView status;

    private int dp(int v) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, getResources().getDisplayMetrics());
    }

    private Button button(String label, View.OnClickListener l, boolean primary) {
        Button b = new Button(this);
        b.setText(label);
        b.setAllCaps(false);
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        b.setTextColor(primary ? 0xFF10202A : 0xFFE4EAF0);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(primary ? 0xFF8FB8C9 : 0xFF202A36);
        bg.setCornerRadius(dp(14));
        b.setBackground(bg);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(52));
        lp.topMargin = dp(10);
        b.setLayoutParams(lp);
        b.setOnClickListener(l);
        return b;
    }

    private TextView text(String s, int sp, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        t.setLineSpacing(0, 1.25f);
        if (bold) t.setTypeface(t.getTypeface(), android.graphics.Typeface.BOLD);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(10);
        t.setLayoutParams(lp);
        return t;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(0xFF0F141B);
        getWindow().setNavigationBarColor(0xFF0F141B);

        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(0xFF0F141B);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(22), dp(40), dp(22), dp(40));
        scroll.addView(col);

        android.widget.ImageView face = new android.widget.ImageView(this);
        face.setImageResource(R.drawable.av_happy);
        LinearLayout.LayoutParams flp = new LinearLayout.LayoutParams(dp(110), dp(110));
        flp.gravity = Gravity.CENTER_HORIZONTAL;
        face.setLayoutParams(flp);
        col.addView(face);

        TextView title = text("Luffy", 30, 0xFFE4EAF0, true);
        title.setGravity(Gravity.CENTER_HORIZONTAL);
        col.addView(title);
        TextView sub = text("Your buddy, floating above every app.", 15, 0xFF8FA0B2, false);
        sub.setGravity(Gravity.CENTER_HORIZONTAL);
        col.addView(sub);

        status = text("", 15, 0xFFE4EAF0, false);
        col.addView(status);

        col.addView(button("1. Allow floating over other apps", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent i = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + getPackageName()));
                startActivity(i);
            }
        }, false));
        col.addView(button("2. Allow the microphone", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, REQ_MIC);
            }
        }, false));
        col.addView(button("3. Allow notifications", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (Build.VERSION.SDK_INT >= 33) {
                    requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQ_NOTIF);
                } else {
                    Toast.makeText(MainActivity.this, "Not needed on this Android version", Toast.LENGTH_SHORT).show();
                }
            }
        }, false));
        col.addView(button("4. Keep Luffy running (battery)", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    startActivity(new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                            Uri.parse("package:" + getPackageName())));
                } catch (Exception e) {
                    startActivity(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS));
                }
            }
        }, false));

        col.addView(button("Start Luffy", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startLuffy();
            }
        }, true));
        col.addView(button("Open Luffy's chat", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!Settings.canDrawOverlays(MainActivity.this)) {
                    Toast.makeText(MainActivity.this, "Allow floating over other apps first", Toast.LENGTH_LONG).show();
                    return;
                }
                startService(OverlayService.ACTION_EXPAND);
            }
        }, false));
        col.addView(button("Stop Luffy", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startService(OverlayService.ACTION_STOP);
            }
        }, false));

        col.addView(text("Add the home-screen widget: touch and hold an empty spot on your home screen, "
                + "tap Widgets, find Luffy, and drag him out.", 14, 0xFF8FA0B2, false));
        col.addView(text("Luffy starts muted. Use the speaker button on his bubble or widget to unmute. "
                + "He stays silent on its own while you are in a call or meeting.", 14, 0xFF8FA0B2, false));

        setContentView(scroll);
    }

    private void startService(String action) {
        Intent i = new Intent(this, OverlayService.class).setAction(action);
        if (Build.VERSION.SDK_INT >= 26) {
            startForegroundService(i);
        } else {
            startService(i);
        }
    }

    private void startLuffy() {
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "Allow floating over other apps first (step 1)", Toast.LENGTH_LONG).show();
            return;
        }
        startService(OverlayService.ACTION_START);
        Toast.makeText(this, "Luffy is on your screen", Toast.LENGTH_SHORT).show();
        moveTaskToBack(true);
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateStatus();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        updateStatus();
    }

    private static String mark(boolean ok) {
        return ok ? "\u2713 " : "\u2717 ";
    }

    private void updateStatus() {
        boolean overlay = Settings.canDrawOverlays(this);
        boolean mic = checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED;
        boolean notif = Build.VERSION.SDK_INT < 33
                || checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED;
        status.setText(mark(overlay) + "Float over other apps\n"
                + mark(mic) + "Microphone\n"
                + mark(notif) + "Notifications");
    }
}
