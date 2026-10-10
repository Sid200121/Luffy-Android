package com.sid.luffy;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.widget.RemoteViews;

/** Home-screen widget: Luffy's face, his last message, and a mute button. */
public class LuffyWidgetProvider extends AppWidgetProvider {

    @Override
    public void onUpdate(Context context, AppWidgetManager manager, int[] appWidgetIds) {
        RemoteViews views = build(context);
        for (int id : appWidgetIds) {
            manager.updateAppWidget(id, views);
        }
    }

    /** Redraws every placed Luffy widget from the saved state. */
    static void refresh(Context context) {
        AppWidgetManager manager = AppWidgetManager.getInstance(context);
        int[] ids = manager.getAppWidgetIds(new ComponentName(context, LuffyWidgetProvider.class));
        if (ids == null || ids.length == 0) return;
        RemoteViews views = build(context);
        for (int id : ids) {
            manager.updateAppWidget(id, views);
        }
    }

    private static int faceFor(String mood) {
        if ("sleepy".equals(mood)) return R.drawable.av_sleepy;
        if ("focus".equals(mood)) return R.drawable.av_focus;
        if ("touched".equals(mood)) return R.drawable.av_touched;
        return R.drawable.av_happy;
    }

    private static RemoteViews build(Context c) {
        SharedPreferences sp = c.getSharedPreferences("luffy", Context.MODE_PRIVATE);
        String mood = sp.getString("mood", "happy");
        String text = sp.getString("text", "");
        boolean muted = sp.getBoolean("muted", true);

        RemoteViews rv = new RemoteViews(c.getPackageName(), R.layout.widget_luffy);
        rv.setImageViewResource(R.id.w_face, faceFor(mood));
        rv.setTextViewText(R.id.w_sub, muted ? "Muted" : "Voice on");
        rv.setTextViewText(R.id.w_text, text == null || text.isEmpty() ? "Tap to talk to Luffy" : text);
        rv.setImageViewResource(R.id.w_mute, muted ? R.drawable.ic_vol_off : R.drawable.ic_vol_on);
        rv.setOnClickPendingIntent(R.id.w_root, service(c, OverlayService.ACTION_EXPAND, 1));
        rv.setOnClickPendingIntent(R.id.w_mute, service(c, OverlayService.ACTION_TOGGLE_MUTE, 2));
        return rv;
    }

    private static PendingIntent service(Context c, String action, int requestCode) {
        Intent i = new Intent(c, OverlayService.class).setAction(action);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE;
        if (Build.VERSION.SDK_INT >= 26) {
            return PendingIntent.getForegroundService(c, requestCode, i, flags);
        }
        return PendingIntent.getService(c, requestCode, i, flags);
    }
}
