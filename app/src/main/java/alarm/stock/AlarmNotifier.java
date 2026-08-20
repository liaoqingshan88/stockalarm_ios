package alarm.stock;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.media.AudioAttributes;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;

/**
 * 预警通知统一发送工具。
 * 供 Activity 的前台 UI 预警和 StockAlertService 的后台保活预警共用，
 * 确保两处通知行为一致（铃声/振动/点击跳转）。
 */
public class AlarmNotifier {

    private static final int NOTIFICATION_ID_ALARM = 0;

    /** 发送预警通知，点击后回到主界面 */
    public static void notifyAlarm(Context ctx, String msg) {
        try {
            Context app = ctx.getApplicationContext();
            String appName = app.getString(R.string.app_name);
            NotificationManager nm = (NotificationManager) app.getSystemService(Context.NOTIFICATION_SERVICE);

            // 点击通知回到主界面（AlarmActivity）
            Intent appIntent = new Intent(Intent.ACTION_MAIN);
            appIntent.addCategory(Intent.CATEGORY_LAUNCHER);
            appIntent.setComponent(new ComponentName(
                    app.getPackageName(),
                    alarm.stock.AlarmActivity.class.getName()));
            appIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            PendingIntent contentIntent = PendingIntent.getActivity(
                    app, 0, appIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

            // 铃声/振动设置（与 Activity 原有逻辑一致）
            Uri ringtoneUri = SettingsActivity.getRingtoneUri(app);
            if (ringtoneUri == null) {
                ringtoneUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
            }
            boolean vibrateEnabled = SettingsActivity.getVibrateEnabled(app);

            Notification.Builder builder;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                String channelSuffix = (ringtoneUri != null) ? String.valueOf(ringtoneUri.toString().hashCode()) : "default";
                String channelId = "stock_alarm_channel_" + channelSuffix + "_" + (vibrateEnabled ? "v1" : "v0");
                NotificationChannel channel = nm.getNotificationChannel(channelId);
                if (channel == null) {
                    channel = new NotificationChannel(channelId, "股票预警", NotificationManager.IMPORTANCE_HIGH);
                }
                channel.enableVibration(vibrateEnabled);
                if (ringtoneUri != null) {
                    AudioAttributes attributes = new AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build();
                    channel.setSound(ringtoneUri, attributes);
                }
                nm.createNotificationChannel(channel);
                builder = new Notification.Builder(app, channelId);
            } else {
                builder = new Notification.Builder(app);
            }

            builder.setSmallIcon(R.drawable.ic_launcher)
                    .setContentTitle(appName)
                    .setContentText(msg)
                    .setContentIntent(contentIntent)
                    .setAutoCancel(true);

            int defaults = 0;
            if (vibrateEnabled) {
                defaults |= Notification.DEFAULT_VIBRATE;
            }
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
                if (ringtoneUri != null) {
                    builder.setSound(ringtoneUri);
                } else {
                    defaults |= Notification.DEFAULT_SOUND;
                }
            }
            builder.setDefaults(defaults);

            nm.notify(NOTIFICATION_ID_ALARM, builder.build());
        } catch (Exception e) {
            android.util.Log.e("AlarmNotifier", "notify error: " + e.getMessage(), e);
        }
    }
}
