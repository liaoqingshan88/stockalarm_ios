package alarm.stock;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * 多端同步 HTTP 客户端
 *
 * 负责与服务端 /api/sync 系列接口通信，token 保存到 SharedPreferences。
 * 所有方法均为同步阻塞调用，必须在子线程中执行（由 SyncManager 调度）。
 *
 * 服务端基地址（与 DeviceReporter 一致：主域名 + 备用 IP）：
 *   http://www.yanhexing.com:9000   （可能被 ICP 拦截）
 *   http://47.107.180.174:9000      （备用）
 */
public class SyncClient {

    private static final String TAG = "SyncClient";

    // 服务端地址（主/备用，自动切换）
    public static final String API_BASE_URL_PRIMARY   = "http://www.yanhexing.com:9000";
    public static final String API_BASE_URL_FALLBACK  = "http://47.107.180.174:9000";

    private static final String PREF_PHONE = "sync_phone";
    private static final String PREF_TOKEN = "sync_token";
    private static final String PREF_LOGGED_IN = "sync_logged_in";

    private final Context context;
    private String baseUrl = API_BASE_URL_PRIMARY;

    // 会话失效（HTTP 401）回调：登录态失效时由 SyncManager 注册，用于通知 UI 引导重新登录
    private volatile Runnable onUnauthorized;

    public SyncClient(Context context) {
        this.context = context.getApplicationContext();
    }

    // ==================== 登录状态持久化 ====================

    public void saveToken(String phone, String token) {
        SharedPreferences.Editor ed = PreferenceManager.getDefaultSharedPreferences(context).edit();
        ed.putString(PREF_PHONE, phone);
        ed.putString(PREF_TOKEN, token);
        ed.putBoolean(PREF_LOGGED_IN, true);
        ed.apply();
    }

    public void clearToken() {
        SharedPreferences.Editor ed = PreferenceManager.getDefaultSharedPreferences(context).edit();
        ed.remove(PREF_PHONE);
        ed.remove(PREF_TOKEN);
        ed.putBoolean(PREF_LOGGED_IN, false);
        ed.apply();
    }

    public boolean isLoggedIn() {
        return PreferenceManager.getDefaultSharedPreferences(context).getBoolean(PREF_LOGGED_IN, false);
    }

    /**
     * 注册会话失效（401）回调。
     * 当请求携带 token 却收到 HTTP 401 时，SyncClient 会自动清除本地登录态
     * （sync_phone / sync_token / sync_logged_in），并触发该回调，
     * 用于弹出重新登录引导对话框。
     */
    public void setOnUnauthorized(Runnable r) {
        this.onUnauthorized = r;
    }

    public String getToken() {
        return PreferenceManager.getDefaultSharedPreferences(context).getString(PREF_TOKEN, "");
    }

    public String getPhone() {
        return PreferenceManager.getDefaultSharedPreferences(context).getString(PREF_PHONE, "");
    }

    // ==================== 认证接口 ====================

    /**
     * 注册。初始密码 123456。
     */
    public String register(String phone) throws Exception {
        JSONObject body = new JSONObject();
        body.put("phone", phone);
        JSONObject resp = post("/api/sync/register", body, null);
        if (!resp.optBoolean("success", false)) {
            throw new SyncException(resp.optString("error", "注册失败"));
        }
        saveToken(phone, resp.optString("token"));
        return resp.optString("message");
    }

    /**
     * 登录。成功后保存 token。
     */
    public String login(String phone, String password) throws Exception {
        JSONObject body = new JSONObject();
        body.put("phone", phone);
        body.put("password", password);
        JSONObject resp = post("/api/sync/login", body, null);
        if (!resp.optBoolean("success", false)) {
            throw new SyncException(resp.optString("error", "登录失败"));
        }
        saveToken(phone, resp.optString("token"));
        return resp.optString("message");
    }

    /**
     * 登出：清除本地 token。
     */
    public void logout() {
        clearToken();
    }

    /**
     * 修改密码。成功后需重新登录。
     */
    public String changePassword(String oldPassword, String newPassword) throws Exception {
        JSONObject body = new JSONObject();
        body.put("oldPassword", oldPassword);
        body.put("newPassword", newPassword);
        JSONObject resp = post("/api/sync/change-password", body, getToken());
        if (!resp.optBoolean("success", false)) {
            throw new SyncException(resp.optString("error", "修改密码失败"));
        }
        clearToken();
        return resp.optString("message");
    }

    // ==================== 股票 ====================

    /**
     * 获取用户全部股票（含 clear_stock 版本号）。
     */
    public SyncStockResult getStocks() throws Exception {
        JSONObject resp = get("/api/sync/stocks", getToken());
        if (!resp.optBoolean("success", false)) {
            throw new SyncException(resp.optString("error", "获取股票失败"));
        }
        JSONArray arr = resp.optJSONArray("stocks");
        List<SyncStock> list = new ArrayList<>();
        if (arr != null) {
            for (int i = 0; i < arr.length(); i++) {
                list.add(SyncStock.fromJson(arr.optJSONObject(i)));
            }
        }
        SyncStockResult result = new SyncStockResult();
        result.stocks = list;
        result.clearStock = resp.optInt("clear_stock", 0);
        return result;
    }

    /**
     * 清空当前用户全部同步数据（服务器端），返回最新 clear_stock 版本号。
     */
    public int clearStock() throws Exception {
        JSONObject body = new JSONObject();
        JSONObject resp = post("/api/sync/clear_stock", body, getToken());
        if (!resp.optBoolean("success", false)) {
            throw new SyncException(resp.optString("error", "清空股票失败"));
        }
        return resp.optInt("clear_stock", 0);
    }

    /**
     * 上传/合并股票，返回合并后的完整列表。
     */
    public List<SyncStock> uploadStocks(List<SyncStock> stocks) throws Exception {
        JSONObject body = new JSONObject();
        JSONArray arr = new JSONArray();
        for (SyncStock s : stocks) {
            arr.put(s.toJson());
        }
        body.put("stocks", arr);
        JSONObject resp = post("/api/sync/stocks", body, getToken());
        if (!resp.optBoolean("success", false)) {
            throw new SyncException(resp.optString("error", "上传股票失败"));
        }
        JSONArray result = resp.optJSONArray("stocks");
        List<SyncStock> list = new ArrayList<>();
        if (result != null) {
            for (int i = 0; i < result.length(); i++) {
                list.add(SyncStock.fromJson(result.optJSONObject(i)));
            }
        }
        return list;
    }

    /**
     * 删除单只股票。
     */
    public void deleteStock(String code) throws Exception {
        JSONObject resp = delete("/api/sync/stocks/" + code, getToken());
        if (!resp.optBoolean("success", false)) {
            throw new SyncException(resp.optString("error", "删除股票失败"));
        }
    }

    // ==================== MA 均线数据 ====================

    /**
     * 获取 MA 数据。
     */
    public List<SyncMaPrice> getMaPrices() throws Exception {
        JSONObject resp = get("/api/sync/ma-prices", getToken());
        if (!resp.optBoolean("success", false)) {
            throw new SyncException(resp.optString("error", "获取MA数据失败"));
        }
        JSONArray arr = resp.optJSONArray("maPrices");
        List<SyncMaPrice> list = new ArrayList<>();
        if (arr != null) {
            for (int i = 0; i < arr.length(); i++) {
                list.add(SyncMaPrice.fromJson(arr.optJSONObject(i)));
            }
        }
        return list;
    }

    /**
     * 上传 MA 数据。
     */
    public void uploadMaPrices(List<SyncMaPrice> maPrices) throws Exception {
        JSONObject body = new JSONObject();
        JSONArray arr = new JSONArray();
        for (SyncMaPrice m : maPrices) {
            arr.put(m.toJson());
        }
        body.put("maPrices", arr);
        JSONObject resp = post("/api/sync/ma-prices", body, getToken());
        if (!resp.optBoolean("success", false)) {
            throw new SyncException(resp.optString("error", "上传MA数据失败"));
        }
    }

    // ==================== 报警日志 ====================

    /**
     * 获取报警日志。
     */
    public List<SyncAlarmLog> getAlarmLogs() throws Exception {
        JSONObject resp = get("/api/sync/alarm-logs", getToken());
        if (!resp.optBoolean("success", false)) {
            throw new SyncException(resp.optString("error", "获取报警日志失败"));
        }
        JSONArray arr = resp.optJSONArray("alarmLogs");
        List<SyncAlarmLog> list = new ArrayList<>();
        if (arr != null) {
            for (int i = 0; i < arr.length(); i++) {
                list.add(SyncAlarmLog.fromJson(arr.optJSONObject(i)));
            }
        }
        return list;
    }

    /**
     * 上传报警日志（按 id 去重）。
     */
    public void uploadAlarmLogs(List<SyncAlarmLog> logs) throws Exception {
        JSONObject body = new JSONObject();
        JSONArray arr = new JSONArray();
        for (SyncAlarmLog l : logs) {
            arr.put(l.toJson());
        }
        body.put("alarmLogs", arr);
        JSONObject resp = post("/api/sync/alarm-logs", body, getToken());
        if (!resp.optBoolean("success", false)) {
            throw new SyncException(resp.optString("error", "上传报警日志失败"));
        }
    }

    // ==================== 底层 HTTP ====================

    private JSONObject get(String path, String token) throws Exception {
        return request("GET", path, null, token);
    }

    private JSONObject post(String path, JSONObject body, String token) throws Exception {
        return request("POST", path, body, token);
    }

    private JSONObject delete(String path, String token) throws Exception {
        return request("DELETE", path, null, token);
    }

    private JSONObject request(String method, String path, JSONObject body, String token) throws Exception {
        Exception lastErr = null;
        String[] urls = { baseUrl, };
        // 确保备用地址也尝试
        String primary = API_BASE_URL_PRIMARY, fallback = API_BASE_URL_FALLBACK;
        String[] candidates;
        if (primary.equals(baseUrl)) {
            candidates = new String[]{ primary, fallback };
        } else {
            candidates = new String[]{ baseUrl, primary, fallback };
        }

        for (String u : candidates) {
            try {
                JSONObject r = doRequest(method, u + path, body, token);
                baseUrl = u; // 记住最近成功的基地址，下次优先
                return r;
            } catch (SyncClient.SyncUnauthorizedException e) {
                // 401 是确定性鉴权失败（token 失效），与网络/服务器地址无关，
                // 不应回退到另一台主机重试，直接抛出并触发重新登录流程。
                throw e;
            } catch (Exception e) {
                lastErr = e;
                Log.w(TAG, "请求失败(" + u + "): " + e.getMessage());
            }
        }
        throw lastErr != null ? lastErr : new SyncException("网络请求失败");
    }

    private JSONObject doRequest(String method, String urlStr, JSONObject body, String token) throws Exception {
        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        try {
            conn.setRequestMethod(method);
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(10000);
            conn.setRequestProperty("Accept", "application/json");
            conn.setRequestProperty("Connection", "close");

            // token 认证
            if (token != null && token.length() > 0) {
                conn.setRequestProperty("Authorization", "Bearer " + token);
            }

            if (body != null) {
                conn.setDoOutput(true);
                conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
                byte[] data = body.toString().getBytes(StandardCharsets.UTF_8);
                // 避免 body 过大
                if (data.length > 2 * 1024 * 1024) {
                    throw new SyncException("数据过大，无法上传");
                }
                try (OutputStream os = conn.getOutputStream()) {
                    os.write(data);
                    os.flush();
                }
            }

            int code = conn.getResponseCode();
            InputStream is = (code >= 200 && code < 300) ? conn.getInputStream() : conn.getErrorStream();
            StringBuilder sb = new StringBuilder();
            if (is != null) {
                try (BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = br.readLine()) != null) {
                        sb.append(line);
                    }
                }
            }

            String respStr = sb.toString().trim();
            if (code < 200 || code >= 300) {
                if (code == 401 && token != null && token.length() > 0) {
                    // token 失效/与服务器不匹配：自动清除本地登录态，并通知 UI 引导重新登录
                    clearToken();
                    Log.w(TAG, "HTTP 401：登录态失效，已清除本地登录信息，触发重新登录引导");
                    if (onUnauthorized != null) {
                        try {
                            onUnauthorized.run();
                        } catch (Exception e) {
                            Log.w(TAG, "401 重新登录回调异常: " + e.getMessage());
                        }
                    }
                }
                // 401 = token 失效/密码错；404 = 未注册；409 = 已注册
                String msg = "HTTP " + code;
                if (respStr.startsWith("{")) {
                    try {
                        JSONObject j = new JSONObject(respStr);
                        if (j.has("error")) msg = j.optString("error");
                    } catch (Exception ignore) {}
                }
                if (code == 401) {
                    throw new SyncUnauthorizedException(msg);
                }
                throw new SyncException(msg);
            }

            // 空响应
            if (respStr.length() == 0) {
                return new JSONObject();
            }
            return new JSONObject(respStr);
        } finally {
            conn.disconnect();
        }
    }

    // ==================== 数据模型 ====================

    /** 获取股票的返回值：股票列表 + clear_stock 版本号 */
    public static class SyncStockResult {
        public List<SyncStock> stocks;
        public int clearStock; // 服务器端 clear_stock 版本号
    }

    /** 同步股票记录（对应服务端字段） */
    public static class SyncStock {
        public String code;
        public String name;
        public float price_h;
        public float price_l;
        public int rise;
        public int drop;
        public int valid;      // 1/0
        public long last_modified; // 毫秒时间戳

        public JSONObject toJson() {
            JSONObject o = new JSONObject();
            try {
                o.put("code", code == null ? "" : code);
                o.put("name", name == null ? "" : name);
                o.put("price_h", price_h);
                o.put("price_l", price_l);
                o.put("rise", rise);
                o.put("drop", drop);
                o.put("valid", valid);
                o.put("last_modified", last_modified);
            } catch (Exception e) {
                Log.e(TAG, "SyncStock.toJson error", e);
            }
            return o;
        }

        public static SyncStock fromJson(JSONObject o) {
            SyncStock s = new SyncStock();
            if (o == null) return s;
            s.code = o.optString("code");
            s.name = o.optString("name");
            s.price_h = (float) o.optDouble("price_h", 0);
            s.price_l = (float) o.optDouble("price_l", 0);
            s.rise = o.optInt("rise", 0);
            s.drop = o.optInt("drop", 0);
            s.valid = o.optInt("valid", 1);
            s.last_modified = o.optLong("last_modified", 0);
            return s;
        }
    }

    /** 同步 MA 数据 */
    public static class SyncMaPrice {
        public String code;
        public int period;
        public float price;
        public long last_modified;

        public JSONObject toJson() {
            JSONObject o = new JSONObject();
            try {
                o.put("code", code == null ? "" : code);
                o.put("period", period);
                o.put("price", price);
                o.put("last_modified", last_modified);
            } catch (Exception e) {
                Log.e(TAG, "SyncMaPrice.toJson error", e);
            }
            return o;
        }

        public static SyncMaPrice fromJson(JSONObject o) {
            SyncMaPrice m = new SyncMaPrice();
            if (o == null) return m;
            m.code = o.optString("code");
            m.period = o.optInt("period", 0);
            m.price = (float) o.optDouble("price", 0);
            m.last_modified = o.optLong("last_modified", 0);
            return m;
        }
    }

    /** 同步报警日志 */
    public static class SyncAlarmLog {
        public String id;
        public String type;
        public String content;
        public String code;
        public String create_time;

        public JSONObject toJson() {
            JSONObject o = new JSONObject();
            try {
                o.put("id", id == null ? "" : id);
                o.put("type", type == null ? "alarm" : type);
                o.put("content", content == null ? "" : content);
                o.put("code", code == null ? "" : code);
                o.put("create_time", create_time == null ? "" : create_time);
            } catch (Exception e) {
                Log.e(TAG, "SyncAlarmLog.toJson error", e);
            }
            return o;
        }

        public static SyncAlarmLog fromJson(JSONObject o) {
            SyncAlarmLog l = new SyncAlarmLog();
            if (o == null) return l;
            l.id = o.optString("id");
            l.type = o.optString("type");
            l.content = o.optString("content");
            l.code = o.optString("code");
            l.create_time = o.optString("create_time");
            return l;
        }
    }

    /** 同步相关异常 */
    public static class SyncException extends Exception {
        public SyncException(String msg) {
            super(msg);
        }
    }

    /** 登录态失效异常（HTTP 401） */
    public static class SyncUnauthorizedException extends SyncException {
        public SyncUnauthorizedException(String msg) {
            super(msg);
        }
    }
}
