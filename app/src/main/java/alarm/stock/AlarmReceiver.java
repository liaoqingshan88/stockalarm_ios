package alarm.stock;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

/**
 * 非交易时段省电唤醒接收器。
 *
 * 当应用处于非交易时间（夜间/午休/周末）时，StockAlertService 停止高频循环并释放 WakeLock，
 * 改用 AlarmManager 定时唤醒（setAndAllowWhileIdle，在 Doze 模式下也能触发）。
 * 闹钟到点（下一交易日开盘前）发出广播，本接收器收到后重新拉起前台服务，
 * 让服务根据当前时间自动恢复高频预警检查。
 */
public class AlarmReceiver extends BroadcastReceiver {

    private static final String TAG = "AlarmReceiver";

    /** 闹钟的 action */
    public static final String ACTION_RESUME = "alarm.stock.action.RESUME_TRADE";

    @Override
    public void onReceive(Context context, Intent intent) {
        try {
            String action = intent != null ? intent.getAction() : null;
            if (ACTION_RESUME.equals(action)) {
                Log.d(TAG, "省电闹钟唤醒，恢复预警服务");
                StockAlertService.start(context);
                // 触发一次即时调度（服务若已在运行则直接进入交易时间高频模式）
                StockAlertService.tickSchedule(context);
            }
        } catch (Exception e) {
            Log.e(TAG, "唤醒失败: " + e.getMessage(), e);
        }
    }
}
