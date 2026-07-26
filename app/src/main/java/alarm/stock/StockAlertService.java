package alarm.stock;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

/**
 * 前台服务：保持股票预警应用在后台持续运行。
 * 
 * 原理：
 * 1. 显示一个常驻通知栏的通知（系统几乎不会杀前台服务）
 * 2. START_STICKY 确保被杀后自动重启
 * 3. Activity 退到后台时服务继续保活
 */
public class StockAlertService extends Service {

    private static final String TAG = "StockAlertService";
    private static final int NOTIFICATION_ID = 10001;
    private static final String CHANNEL_ID = "stock_alert_foreground";

    /**
     * 启动前台服务（供外部调用）
     */
    public static void start(Context context) {
        Intent intent = new Intent(context, StockAlertService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent);
        } else {
            context.startService(intent);
        }
    }

    /**
     * 停止前台服务
     */
    public static void stop(Context context) {
        Intent intent = new Intent(context, StockAlertService.class);
        context.stopService(intent);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "服务创建");
        startForeground(NOTIFICATION_ID, buildNotification("股票预警运行中"));
        
        // 上报设备信息到服务器
        DeviceReporter.reportAsync(this);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "服务启动");
        // START_STICKY: 被系统杀死后会自动重启
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        DeviceReporter.reportAsync(this);
        super.onDestroy();
        Log.d(TAG, "服务销毁");
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    /**
     * 更新通知内容（可供外部调用更新状态）
     */
    public void updateNotification(String text) {
        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) {
            nm.notify(NOTIFICATION_ID, buildNotification(text));
        }
    }

    private Notification buildNotification(String text) {
        // 确保通知渠道存在（Android 8.0+）
        createNotificationChannelIfNeeded();

        // 点击通知打开主界面
        Intent appIntent = new Intent(this, AlarmActivity.class);
        appIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        PendingIntent contentIntent = PendingIntent.getActivity(
            this, 0, appIntent,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        return new NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentIntent(contentIntent)
            .setOngoing(true)  // 不可滑动清除
            .setPriority(NotificationCompat.PRIORITY_LOW)  // 低优先级，不打扰用户
            .build();
    }

    private void createNotificationChannelIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm != null && nm.getNotificationChannel(CHANNEL_ID) == null) {
                NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "股票预警保活",
                    NotificationManager.IMPORTANCE_LOW  // 低重要级，不发出声音
                );
                channel.setDescription("保持股票预警在后台运行");
                channel.setShowBadge(false);
                nm.createNotificationChannel(channel);
            }
        }
    }
}
