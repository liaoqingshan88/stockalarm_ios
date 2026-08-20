package alarm.stock;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

/**
 * 开机自启：设备重启后自动拉起前台预警服务，保证预警不中断。
 */
public class BootReceiver extends BroadcastReceiver {

    private static final String TAG = "BootReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        try {
            String action = intent != null ? intent.getAction() : null;
            if (Intent.ACTION_BOOT_COMPLETED.equals(action)
                    || "android.intent.action.QUICKBOOT_POWERON".equals(action)
                    || "com.htc.intent.action.QUICKBOOT_POWERON".equals(action)) {
                Log.d(TAG, "设备重启，拉起预警服务");
                StockAlertService.start(context);
            }
        } catch (Exception e) {
            Log.e(TAG, "开机自启失败: " + e.getMessage(), e);
        }
    }
}
