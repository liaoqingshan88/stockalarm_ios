package alarm.stock;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 前台服务：保持股票预警应用在后台持续运行。
 *
 * 核心作用：
 * 1. 显示一个常驻通知栏的通知（系统几乎不会杀前台服务）
 * 2. START_STICKY 确保被杀后自动重启
 * 3. 内置独立的后台预警循环：即使 AlarmActivity 被系统回收，
 *    本服务也能每 5 秒拉取行情、检测预警条件，命中后直接发系统通知。
 * 4. 持有 WakeLock，防止轮询期间系统休眠导致预警中断。
 */
public class StockAlertService extends Service {

    private static final String TAG = "StockAlertService";
    private static final int NOTIFICATION_ID = 10001;
    private static final String CHANNEL_ID = "stock_alert_foreground";

    // 后台预警循环
    private static final long CHECK_INTERVAL_MS = 5000L; // 5 秒一次
    private final AtomicBoolean checking = new AtomicBoolean(false);
    private Handler loopHandler;
    private Runnable loopRunnable;
    private ExecutorService checkExecutor;
    private StockMng stockMng;
    private MaPriceMng maMng;
    private MaAlarmLogMng maAlarmLogMng;
    private StockSQLite sqlite;
    private SyncManager syncManager; // 后台同步轮询（问题3：活动页销毁/应用退到后台时仍持续拉取服务器变更，含另一端删除）
    private PowerManager.WakeLock wakeLock;

    private static boolean running = false;
    public static boolean isRunning() { return running; }

    /**
     * 启动前台服务（供外部调用）
     */
    public static void start(Context context) {
        Intent intent = new Intent(context, StockAlertService.class);
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent);
            } else {
                context.startService(intent);
            }
        } catch (Exception e) {
            Log.e(TAG, "启动服务失败: " + e.getMessage(), e);
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
        running = true;
        startForeground(NOTIFICATION_ID, buildNotification("股票预警运行中"));
        acquireWakeLock();
        initManagers();
        startCheckLoop();

        // 上报设备信息到服务器
        DeviceReporter.reportAsync(this);
    }

    private void initManagers() {
        try {
            sqlite = new StockSQLite(this);
            stockMng = new StockMng(sqlite);
            maMng = new MaPriceMng(sqlite);
            maAlarmLogMng = new MaAlarmLogMng(sqlite);
            PeakValleyTracker.setShlLogMng(new ShlAlarmLogMng(sqlite));

            // 问题3：把多端同步轮询搬到前台服务中，保证退到后台/活动页被回收时
            // 手机仍持续拉取服务器变更（另一端新增、修改、删除股票，含墓碑删除），
            // 否则同步轮询只挂在 AlarmActivity，页面一退后台就不再拉取，
            // 导致“电脑删除股票 → 手机端不消失”。
            try {
                syncManager = new SyncManager(this);
                if (syncManager.getClient().isLoggedIn()) {
                    syncManager.startPeriodicSync();
                    Log.i(TAG, "前台服务已启动后台同步轮询(拉取服务器变更)");
                }
            } catch (Exception e) {
                Log.e(TAG, "初始化后台同步失败: " + e.getMessage(), e);
                syncManager = null;
            }
        } catch (Exception e) {
            Log.e(TAG, "初始化管理器失败: " + e.getMessage(), e);
        }
    }

    private void acquireWakeLock() {
        try {
            PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
            if (pm != null) {
                wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "stock:alert");
                wakeLock.setReferenceCounted(false);
                wakeLock.acquire();
            }
        } catch (Exception e) {
            Log.e(TAG, "获取WakeLock失败: " + e.getMessage(), e);
        }
    }

    private void releaseWakeLock() {
        try {
            if (wakeLock != null && wakeLock.isHeld()) {
                wakeLock.release();
            }
        } catch (Exception e) {
            Log.e(TAG, "释放WakeLock失败: " + e.getMessage(), e);
        }
        wakeLock = null;
    }

    private void startCheckLoop() {
        loopHandler = new Handler(Looper.getMainLooper());
        checkExecutor = Executors.newSingleThreadExecutor();
        loopRunnable = new Runnable() {
            @Override
            public void run() {
                if (!running) return;
                runSingleCheck();
                if (loopHandler != null) {
                    loopHandler.postDelayed(this, CHECK_INTERVAL_MS);
                }
            }
        };
        loopHandler.postDelayed(loopRunnable, 1000);
    }

    /** 单次检测：交易时间内检查预警，命中发通知 */
    private void runSingleCheck() {
        if (checking.getAndSet(true)) return; // 防止重入
        try {
            if (stockMng == null) return;
            if (!StockTime.IsTradeTime()) return; // 非交易时间跳过

            final StockMng m = stockMng;
            final MaPriceMng mm = maMng;
            final MaAlarmLogMng ma = maAlarmLogMng;
            final Context ctx = getApplicationContext();
            checkExecutor.execute(new Runnable() {
                @Override
                public void run() {
                    try {
                        Compare.checkAndNotify(ctx, m, mm, ma);
                    } catch (Exception e) {
                        Log.e(TAG, "后台检查异常: " + e.getMessage(), e);
                    } finally {
                        checking.set(false);
                    }
                }
            });
        } catch (Exception e) {
            Log.e(TAG, "runSingleCheck异常: " + e.getMessage(), e);
            checking.set(false);
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "服务启动");
        if (!running) {
            running = true;
            startForeground(NOTIFICATION_ID, buildNotification("股票预警运行中"));
            acquireWakeLock();
            initManagers();
            startCheckLoop();
        }
        // START_STICKY: 被系统杀死后会自动重启
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        running = false;
        if (loopHandler != null) {
            loopHandler.removeCallbacksAndMessages(null);
            loopHandler = null;
        }
        if (checkExecutor != null) {
            checkExecutor.shutdownNow();
            checkExecutor = null;
        }
        // 问题3：停止后台同步轮询，避免资源泄漏
        if (syncManager != null) {
            try {
                syncManager.stopPeriodicSync();
            } catch (Exception ignore) {}
            syncManager = null;
        }
        releaseWakeLock();
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
