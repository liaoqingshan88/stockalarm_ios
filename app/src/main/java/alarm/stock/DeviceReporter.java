package alarm.stock;

import android.content.Context;
import android.os.Build;
import android.provider.Settings;
import android.util.Log;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/**
 * 设备信息上报工具
 * 用于收集设备信息并上报到服务器
 */
public class DeviceReporter {
    private static final String TAG = "DeviceReporter";
    
    // API 服务器地址
    private static final String API_BASE_URL = "http://www.yanhexing.com:9000";
    private static final String API_REPORT_URL = API_BASE_URL + "/api/device/report";
    
    // 备用地址
    private static final String API_BASE_URL_FALLBACK = "http://47.107.180.174:9000";
    private static final String API_REPORT_URL_FALLBACK = API_BASE_URL_FALLBACK + "/api/device/report";
    
    /**
     * 获取设备唯一ID (Android ID)
     */
    public static String getDeviceId(Context context) {
        try {
            return Settings.Secure.getString(
                context.getContentResolver(),
                Settings.Secure.ANDROID_ID
            );
        } catch (Exception e) {
            Log.e(TAG, "获取设备ID失败", e);
            return "unknown";
        }
    }
    
    /**
     * 获取设备制造商
     */
    public static String getManufacturer() {
        return Build.MANUFACTURER != null ? Build.MANUFACTURER : "unknown";
    }
    
    /**
     * 获取设备型号
     */
    public static String getModel() {
        return Build.MODEL != null ? Build.MODEL : "unknown";
    }
    
    /**
     * 获取Android版本
     */
    public static String getAndroidVersion() {
        return Build.VERSION.RELEASE != null ? Build.VERSION.RELEASE : "unknown";
    }
    
    /**
     * 获取APP版本
     */
    public static String getAppVersion(Context context) {
        try {
            return context.getPackageManager()
                    .getPackageInfo(context.getPackageName(), 0)
                    .versionName;
        } catch (Exception e) {
            Log.e(TAG, "获取APP版本失败", e);
            return "unknown";
        }
    }
    
    /**
     * 异步上报设备信息
     */
    public static void reportAsync(final Context context) {
        new Thread(() -> {
            try {
                report(context, false);
            } catch (Exception e) {
                Log.e(TAG, "设备上报失败（主地址），尝试备用地址", e);
                try {
                    report(context, true);
                } catch (Exception ex) {
                    Log.e(TAG, "设备上报失败（备用地址）", ex);
                }
            }
        }).start();
    }
    
    /**
     * 上报设备信息
     * @param context 上下文
     * @param useFallback 是否使用备用地址
     */
    private static void report(Context context, boolean useFallback) throws Exception {
        String reportUrl = useFallback ? API_REPORT_URL_FALLBACK : API_REPORT_URL;
        
        // 构建上报数据
        JSONObject data = new JSONObject();
        data.put("deviceId", getDeviceId(context));
        data.put("deviceInfo", getManufacturer() + " " + getModel());
        data.put("appVersion", getAppVersion(context));
        data.put("androidVersion", getAndroidVersion());
        data.put("manufacturer", getManufacturer());
        data.put("model", getModel());
        
        // 发送 POST 请求
        URL url = new URL(reportUrl);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
        conn.setDoOutput(true);
        conn.setConnectTimeout(10000);
        conn.setReadTimeout(10000);
        
        // 发送数据
        try (OutputStream os = conn.getOutputStream()) {
            byte[] input = data.toString().getBytes(StandardCharsets.UTF_8);
            os.write(input, 0, input.length);
        }
        
        // 读取响应
        int responseCode = conn.getResponseCode();
        if (responseCode == HttpURLConnection.HTTP_OK) {
            BufferedReader br = new BufferedReader(
                new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8)
            );
            StringBuilder response = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) {
                response.append(line);
            }
            br.close();
            
            JSONObject result = new JSONObject(response.toString());
            if (result.optBoolean("success", false)) {
                Log.i(TAG, "设备信息上报成功: " + reportUrl);
            } else {
                Log.w(TAG, "设备信息上报失败: " + result.optString("error"));
            }
        } else {
            throw new Exception("HTTP错误: " + responseCode);
        }
        
        conn.disconnect();
    }
}
