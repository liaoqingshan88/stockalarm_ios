package alarm.stock;

import android.app.AlarmManager;
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
import android.os.SystemClock;
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
 *    本服务也能拉取行情、检测预警条件，命中后直接发系统通知。
 *
 * 【省电调度】(v3.2.35 起)
 * 交易时间内(工作日 9:30-11:30、13:00-15:00；含港股时上午延至12:00、下午延至16:00，
 * 其中 11:30-12:00、15:00-16:00 只查港股)：
 *   - 每 5 秒做一次预警检查（保持灵敏），持有 WakeLock 防止休眠中断。
 *   - 后台同步保持 10 秒高频（跨端增删改及时生效）。
 * 非交易时间(周末/午休/夜间/节假日)：
 *   - 停止 5 秒高频预警检查与价格获取（价格不变，无需检查）。
 *   - 释放 WakeLock，允许 CPU/系统休眠省电。
 *   - 后台同步拉长到 30 分钟一次，仅用于接收 PC 端新增/修改/删除股票，保证次日开盘前数据最新。
 *   - 用 AlarmManager.setAndAllowWhileIdle 定时唤醒到「下一交易日开盘」前，自动恢复高频。
 */
public class StockAlertService extends Service {

    private static final String TAG = "StockAlertService";
    private static final int NOTIFICATION_ID = 10001;
    private static final String CHANNEL_ID = "stock_alert_foreground";

    // 交易时间：后台预警循环间隔（5 秒，保持灵敏）
    private static final long CHECK_INTERVAL_MS = 5000L;
    // 非交易时间：后台同步间隔（30 分钟）
    private static final long IDLE_SYNC_INTERVAL_MS = 30L * 60L * 1000L;
    // 下一个交易日开盘前提前唤醒的提前量（2 分钟），留有余量恢复高频
    private static final long PRE_RESUME_LEAD_MS = 2L * 60L * 1000L;
    // 省电闹钟请求码
    private static final int ALARM_RESUME_REQ = 20001;

    private final AtomicBoolean checking = new AtomicBoolean(false);
    private Handler loopHandler;
    private Runnable loopRunnable;
    private ExecutorService checkExecutor;
    private StockMng stockMng;
    private MaPriceMng maMng;
    private MaAlarmLogMng maAlarmLogMng;
    private StockSQLite sqlite;
    private SyncManager syncManager;
    private PowerManager.WakeLock wakeLock;

    private static boolean running = false;
    public static boolean isRunning() { return running; }

    // 当前是否处于高频交易模式（用于日志与避免重复切换）
    private volatile boolean highFreqMode = false;

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

    /**
     * 触发一次调度判断（供 AlarmReceiver 唤醒后调用）。
     * 若服务正在运行，则立即让调度器根据当前时间重新决定高频/低频模式。
     */
    public static void tickSchedule(Context context) {
        try {
            // 服务已在运行（进程存在）→ 由 onStartCommand 走调度；否则 start() 会拉起，
            // 拉起后同样走调度。这里直接调用 start 并派发一个即时调度信号。
            // 若服务已运行，start() 会触达 onStartCommand → scheduleTick()。
            start(context);
        } catch (Exception e) {
            Log.e(TAG, "tickSchedule失败: " + e.getMessage(), e);
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "服务创建");
        running = true;
        startForeground(NOTIFICATION_ID, buildNotification("股票预警运行中"));
        initManagers();
        // 开始自适应调度（内部根据交易时间决定高频/低频 + WakeLock）
        startScheduler();

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

            // 多端同步轮询：交易时间高频（10s），非交易时间由调度器调长(30min)
            try {
                syncManager = new SyncManager(this);
                if (syncManager.getClient().isLoggedIn()) {
                    syncManager.startPeriodicSync(SyncManager.DEFAULT_POLL_INTERVAL_MS);
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
                Log.d(TAG, "WakeLock 已持有(交易时间)");
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
        Log.d(TAG, "WakeLock 已释放(非交易时间，允许休眠省电)");
    }

    /**
     * 启动自适应调度器。
     * 调度器每次 tick：
     *   - 交易时间 → 高频模式（5秒预警 + WakeLock + 10秒同步）
     *   - 非交易时间 → 低频模式（停止预警循环 + 释放WakeLock + 30分钟同步 + AlarmManager 唤醒）
     */
    private void startScheduler() {
        loopHandler = new Handler(Looper.getMainLooper());
        checkExecutor = Executors.newSingleThreadExecutor();

        loopRunnable = new Runnable() {
            @Override
            public void run() {
                if (!running) return;
                dispatchByTime();
            }
        };

        // 启动后 1 秒调度一次（尽快进入正确模式）
        loopHandler.postDelayed(loopRunnable, 1000);
    }

    /** 根据当前时间分派：进入高频或低频模式 */
    private void dispatchByTime() {
        try {
            boolean hasHk = hasHkStock();
            if (StockTime.IsTradeTime(hasHk)) {
                enterHighFreqMode(hasHk);
                // 高频模式下：5 秒后再次调度（每次调度做一次预警检查，保持 5 秒节奏）
                if (loopHandler != null) {
                    loopHandler.postDelayed(loopRunnable, CHECK_INTERVAL_MS);
                }
            } else {
                enterLowFreqMode(hasHk);
                // 非交易时间：调度器无需高频空转；由 AlarmManager 或低频同步唤醒。
                // 此处不立即再调度，交给 AlarmManager 的定时唤醒。
            }
        } catch (Exception e) {
            Log.e(TAG, "调度异常: " + e.getMessage(), e);
            // 异常时保守地按 5 秒重试调度
            if (loopHandler != null) {
                loopHandler.postDelayed(loopRunnable, CHECK_INTERVAL_MS);
            }
        }
    }

    /** 是否含港股（任意激活股票代码为港股） */
    private boolean hasHkStock() {
        try {
            if (stockMng == null) return false;
            String[] codes = stockMng.GetCodes();
            if (codes == null) return false;
            for (String c : codes) {
                if (StockTime.isHkStock(c)) return true;
            }
        } catch (Exception e) {
            Log.e(TAG, "判断港股失败: " + e.getMessage(), e);
        }
        return false;
    }

    /** 进入交易时间高频模式：持有 WakeLock + 5 秒预警检查 + 10 秒同步 */
    private void enterHighFreqMode(boolean hasHk) {
        if (!highFreqMode) {
            highFreqMode = true;
            Log.i(TAG, "进入交易时间高频预警模式" + (hasHk ? "(含港股)" : ""));
            acquireWakeLock();
            updateNotification(StockTime.GetTime() + " 预警运行中");
            // 调高同步频率：10 秒
            if (syncManager != null) {
                try { syncManager.setPollInterval(SyncManager.DEFAULT_POLL_INTERVAL_MS); } catch (Exception ignore) {}
            }
            // 取消省电唤醒闹钟（避免残留）
            cancelResumeAlarm();
        }
        // 做一次预警检查（港股延长时段只查港股）
        runSingleCheck(hasHk);
    }

    /** 进入非交易时间低频模式：释放 WakeLock + 停止高频检查 + 30分钟同步 + AlarmManager 唤醒 */
    private void enterLowFreqMode(boolean hasHk) {
        if (highFreqMode) {
            highFreqMode = false;
            Log.i(TAG, "进入非交易低频省电模式");
            updateNotification("非交易时间（省电模式）" + (hasHk? "(含港股)":""));
        }
        // 释放 WakeLock，允许 CPU 休眠
        releaseWakeLock();
        // 同步拉长到 30 分钟（仅接收 PC 端改动）
        if (syncManager != null) {
            try { syncManager.setPollInterval(IDLE_SYNC_INTERVAL_MS); } catch (Exception ignore) {}
        }
        // 安排 AlarmManager 到下一交易日开盘前唤醒
        armResumeAlarm(hasHk);
    }

    /**
     * 用 AlarmManager 定时唤醒到下一交易日开盘前。
     * setAndAllowWhileIdle 在 Doze 模式下也能触发，既省电又可靠。
     */
    private void armResumeAlarm(boolean hasHk) {
        try {
            long untilNext = StockTime.millisUntilNextTradeStart(StockTime.IsTradeTime(hasHk));
            if (untilNext <= 0) {
                // 已在交易时间（异常分支），直接高频
                if (loopHandler != null) loopHandler.postDelayed(loopRunnable, CHECK_INTERVAL_MS);
                return;
            }
            long targetAt = System.currentTimeMillis() + Math.max(untilNext - PRE_RESUME_LEAD_MS, 30_000L);

            AlarmManager am = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
            if (am == null) return;

            Intent intent = new Intent(this, AlarmReceiver.class);
            intent.setAction(AlarmReceiver.ACTION_RESUME);
            PendingIntent pi = PendingIntent.getBroadcast(this, ALARM_RESUME_REQ, intent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, targetAt, pi);
            } else {
                am.set(AlarmManager.RTC_WAKEUP, targetAt, pi);
            }
            Log.i(TAG, "已安排省电闹钟：约 " + (Math.max(untilNext - PRE_RESUME_LEAD_MS, 30_000L) / 60000)
                    + " 分钟后恢复高频预警");
        } catch (Exception e) {
            Log.e(TAG, "安排省电闹钟失败: " + e.getMessage(), e);
            // 保险：用 30 分钟低频兜底，避免完全无调度
            if (loopHandler != null) {
                loopHandler.postDelayed(loopRunnable, IDLE_SYNC_INTERVAL_MS);
            }
        }
    }

    private void cancelResumeAlarm() {
        try {
            AlarmManager am = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
            if (am == null) return;
            Intent intent = new Intent(this, AlarmReceiver.class);
            intent.setAction(AlarmReceiver.ACTION_RESUME);
            PendingIntent pi = PendingIntent.getBroadcast(this, ALARM_RESUME_REQ, intent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            am.cancel(pi);
        } catch (Exception e) {
            Log.e(TAG, "取消省电闹钟失败: " + e.getMessage(), e);
        }
    }

    /** 单次预警检查：交易时间内检查预警，命中发通知；港股延长时段只查港股 */
    private void runSingleCheck(final boolean hasHk) {
        if (checking.getAndSet(true)) return; // 防止重入
        try {
            if (stockMng == null) { checking.set(false); return; }

            final StockMng m = stockMng;
            final MaPriceMng mm = maMng;
            final MaAlarmLogMng ma = maAlarmLogMng;
            final Context ctx = getApplicationContext();
            final String[] codes = activeTradeCodes(hasHk); // 当前应检查的股票代码（港股延长时段只含港股）

            checkExecutor.execute(new Runnable() {
                @Override
                public void run() {
                    try {
                        Compare.checkAndNotify(ctx, m, mm, ma, codes);
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

    /**
     * 计算当前应检查的激活股票代码。
     * 若处于港股延长时段(11:30-12:00、15:00-16:00)且含港股，则只返回港股代码；
     * 否则返回全部激活股票。
     */
    private String[] activeTradeCodes(boolean hasHk) {
        try {
            String[] all = stockMng.GetCodes();
            if (all == null || all.length == 0) return all;

            if (!hasHk) return all; // 无港股，无需过滤

            int cur = StockTime.GetCurTime();
            boolean hkExtension = // 港股延长时段：A股已收盘而港股仍交易
                    (cur > StockTime.GetSecond(11,30,0) && cur <= StockTime.GetSecond(12,0,0))
                    || (cur > StockTime.GetSecond(15,0,0) && cur <= StockTime.GetSecond(16,0,0));

            if (!hkExtension) return all; // 不在港股延长时段，全部检查

            // 港股延长时段：只查港股
            java.util.ArrayList<String> hkCodes = new java.util.ArrayList<String>();
            for (String c : all) {
                if (StockTime.isHkStock(c)) hkCodes.add(c);
            }
            return hkCodes.toArray(new String[hkCodes.size()]);
        } catch (Exception e) {
            Log.e(TAG, "activeTradeCodes异常: " + e.getMessage(), e);
            return stockMng.GetCodes();
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "服务启动");
        if (!running) {
            running = true;
            startForeground(NOTIFICATION_ID, buildNotification("股票预警运行中"));
            initManagers();
            startScheduler();
        } else {
            // 服务已在运行：可能是 AlarmReceiver 唤醒触达，立即重新调度一次
            if (loopHandler != null) {
                loopHandler.removeCallbacks(loopRunnable);
                loopHandler.postDelayed(loopRunnable, 100);
            }
        }
        // START_STICKY: 被系统杀死后会自动重启
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        running = false;
        highFreqMode = false;
        if (loopHandler != null) {
            loopHandler.removeCallbacksAndMessages(null);
            loopHandler = null;
        }
        if (checkExecutor != null) {
            checkExecutor.shutdownNow();
            checkExecutor = null;
        }
        if (syncManager != null) {
            try { syncManager.stopPeriodicSync(); } catch (Exception ignore) {}
            syncManager = null;
        }
        releaseWakeLock();
        cancelResumeAlarm();
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
        try {
            NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm != null) {
                nm.notify(NOTIFICATION_ID, buildNotification(text));
            }
        } catch (Exception e) {
            Log.e(TAG, "更新通知失败: " + e.getMessage(), e);
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
