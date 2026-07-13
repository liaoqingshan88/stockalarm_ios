package alarm.stock;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

/**
 * WorkManager 兜底检查：即使前台服务被杀，也能定期唤醒检查。
 * 周期：15分钟（WorkManager 最小周期）
 * 
 * 注意：这里只做轻量检查，核心预警逻辑仍在 Activity 的 Handler 中。
 * 如果发现服务未运行，会尝试重新启动服务。
 */
public class StockCheckWorker extends Worker {

    private static final String TAG = "StockCheckWorker";

    public StockCheckWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        Log.d(TAG, "WorkManager 兜底检查触发");
        try {
            // 确保前台服务在运行
            StockAlertService.start(getApplicationContext());
            return Result.success();
        } catch (Exception e) {
            Log.e(TAG, "兜底检查失败: " + e.getMessage(), e);
            return Result.retry();
        }
    }
}
