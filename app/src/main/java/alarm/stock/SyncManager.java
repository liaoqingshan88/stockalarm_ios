package alarm.stock;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.preference.PreferenceManager;
import android.util.Log;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 多端同步协调器
 *
 * 职责：
 *  1. 登录后全量同步：本地无数据 -> 从服务器下载全量；本地有数据 -> 上传合并后再下载覆盖
 *  2. 本地股票增/改/删后自动上传
 *  3. 每 10 秒定时从服务器拉取合并（另一端修改会自动下载）
 *  4. 冲突解决：以服务端（已按 last_modified 合并）返回的完整列表为准，写回本地
 *
 * last_modified 持久化：由于本地 stock_info 表没有该字段，为每个 code 单独存
 * 在 SharedPreferences（sync_ts:code -> 毫秒时间戳），保证冲突合并正确。
 */
public class SyncManager {

    private static final String TAG = "SyncManager";
    private static final String PREF_TS_PREFIX = "sync_ts:";
    // 本地已见到的服务器 clear_stock 版本号（用于跨端清空感知）
    private static final String PREF_CLEAR_STOCK = "clear_stock";
    // 服务器已软删除(delleted:true 墓碑)的 code 集合：用于全量上传时过滤，防止复活已删除股票
    private final java.util.Set<String> serverDeletedCodes = new java.util.HashSet<>();

    private final Context context;
    private final SyncClient client;
    private final StockSQLite sqlite;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final AtomicBoolean syncing = new AtomicBoolean(false);

    // 10 秒轮询间隔（从 30 秒调小，提升跨端同步时效；首次触发与循环共用此常量）
    private static final long POLL_INTERVAL_MS = 10_000L;

    // 轮询任务是否已启动
    private boolean pollingStarted = false;
    private final Runnable pollRunnable = new Runnable() {
        @Override
        public void run() {
            // 轮询的拉取/合并涉及网络请求与数据库写操作，必须在子线程执行，
            // 避免在主线程触发 NetworkOnMainThreadException 导致闪退。
            if (client.isLoggedIn() && !syncing.get()) {
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            pullAndMerge();
                        } catch (Exception e) {
                            Log.w(TAG, "轮询同步异常: " + e.getMessage());
                            notifyPullFailure();
                        }
                    }
                }).start();
            }
            handler.postDelayed(this, POLL_INTERVAL_MS);
        }
    };

    // 可选回调（UI 刷新）
    public interface SyncListener {
        void onSyncMessage(String message);
    }
    private SyncListener listener;

    // 会话失效（401）回调：由 Activity 注册，用于弹出重新登录引导
    public interface SessionExpiredListener {
        void onSessionExpired(String message);
    }
    private SessionExpiredListener sessionExpiredListener;

    // 会话失效时待重试的操作（如“修改预警后上传”），重新登录成功后自动重试
    private volatile Runnable pendingRetry;

    public SyncManager(Context context) {
        this.context = context.getApplicationContext();
        this.client = new SyncClient(context);
        this.sqlite = new StockSQLite(context);
        // 注册 401 回调：清除登录态后，通知 UI 引导重新登录（会同时展示重新登录对话框）
        this.client.setOnUnauthorized(new Runnable() {
            @Override
            public void run() {
                handleSessionExpired("登录已失效，请重新登录");
            }
        });
    }

    public void setListener(SyncListener l) {
        this.listener = l;
    }

    public void setSessionExpiredListener(SessionExpiredListener l) {
        this.sessionExpiredListener = l;
    }

    public SyncClient getClient() {
        return client;
    }

    private void postMsg(final String m) {
        handler.post(new Runnable() {
            @Override
            public void run() {
                if (listener != null) listener.onSyncMessage(m);
            }
        });
    }

    // ==================== 登录后全量同步 ====================

    /**
     * 登录成功后同步一次全量数据。
     * 规则：本地无数据 -> 下载全量；本地有数据 -> 先上传合并，再以服务器结果覆盖本地。
     * 需在子线程调用。
     */
    public void syncOnLogin() throws Exception {
        if (!client.isLoggedIn()) return;
        if (!syncing.compareAndSet(false, true)) return;
        try {
            StockMng stockMng = new StockMng(sqlite);
            List<StockMng> local = stockMng.getStocks();

            if (local == null || local.isEmpty()) {
                // 本地无数据 -> 从服务器下载全量
                Log.i(TAG, "本地无数据，从服务器下载全量");
                SyncClient.SyncStockResult result = client.getStocks();
                handleClearStockIfNeeded(result.clearStock);
                applyServerStocks(result.stocks, false);
                // 同步 MA 数据
                List<SyncClient.SyncMaPrice> mas = client.getMaPrices();
                applyServerMas(mas);
                postMsg("已从云端同步 " + (result.stocks != null ? result.stocks.size() : 0) + " 支股票");
            } else {
                // 本地有数据 -> 上传合并，再以服务器结果覆盖本地
                // 但在合并前需先检查服务器 clear_stock：若其它端已清空，本地应先清空再上传，
                // 避免把本地旧数据重新上传回去。
                Log.i(TAG, "本地有数据，上传合并前检查 clear_stock");
                SyncClient.SyncStockResult check = client.getStocks();
                // 刷新服务器已软删除墓碑集合，防止把另一端已删除的股票再传上去复活
                refreshServerDeleted(check.stocks);
                if (handleClearStockIfNeeded(check.clearStock)) {
                    // 其它端已清空，本地已同步清空，重新拉取即可（此时服务器也为空）
                    applyServerStocks(check.stocks, false);
                    List<SyncClient.SyncMaPrice> mas = client.getMaPrices();
                    applyServerMas(mas);
                    postMsg("其它端已清空股票，本地已同步清空");
                } else {
                    // 正常上传合并
                    Log.i(TAG, "本地有数据，上传合并后下载");
                    List<SyncClient.SyncStock> toUpload = collectLocalStocks(stockMng);
                    List<SyncClient.SyncStock> merged = client.uploadStocks(toUpload);
                    applyServerStocks(merged, true);
                    Log.i(TAG, "上传合并完成，共 " + (merged != null ? merged.size() : 0) + " 支");
                    postMsg("同步完成：" + (merged != null ? merged.size() : 0) + " 支股票");
                }
            }
        } finally {
            syncing.set(false);
        }
    }

    // ==================== 修改后自动上传 ====================

    /**
     * 本地股票发生增/改后调用：上传全量，以服务器合并结果覆盖本地。
     * 需在子线程调用。
     */
    public void notifyStocksChanged() throws Exception {
        if (!client.isLoggedIn()) return;
        if (!syncing.compareAndSet(false, true)) return;
        try {
            pendingRetry = new Runnable() {
                @Override
                public void run() {
                    try {
                        notifyStocksChanged();
                    } catch (Exception e) {
                        Log.w(TAG, "重试上传股票失败: " + e.getMessage());
                    }
                }
            };
            StockMng stockMng = new StockMng(sqlite);
            // 上传前先拉取服务器列表，刷新已软删除墓碑集合，防止把另一端已删除的股票再传上去复活
            try {
                SyncClient.SyncStockResult pre = client.getStocks();
                handleClearStockIfNeeded(pre.clearStock);
                refreshServerDeleted(pre.stocks);
            } catch (Exception e) {
                Log.w(TAG, "上传前拉取墓碑失败: " + e.getMessage());
            }
            List<SyncClient.SyncStock> toUpload = collectLocalStocks(stockMng);
            List<SyncClient.SyncStock> merged = client.uploadStocks(toUpload);
            pendingRetry = null; // 成功，清除待重试
            applyServerStocks(merged, true);
            Log.i(TAG, "股票变更已上传，当前 " + (merged != null ? merged.size() : 0) + " 支");
        } catch (SyncClient.SyncUnauthorizedException e) {
            // 登录态失效：已由 SyncClient 清除登录态、SyncManager 触发重新登录引导，
            // pendingRetry 已记录本次修改操作，重新登录成功后由 retryPending() 自动重试。
            throw e;
        } catch (Exception e) {
            pendingRetry = null; // 非鉴权失败（如网络错误），不标记为待重试
            throw e;
        } finally {
            syncing.set(false);
        }
    }

    /**
     * 标记某只股票在本地被真实修改（增/改/启用禁用）。
     * 记录当前时间为该股票的 last_modified，上传时才会携带真实修改时间，
     * 否则 collectLocalStocks 会复用旧的持久化时间戳，导致本端修改在服务端合并时被判定为更旧而被覆盖。
     * 需在主线程（与修改操作的线程一致）调用。
     */
    public void markStockModified(String code) {
        if (code == null || code.isEmpty()) return;
        saveLocalTimestamp(code, System.currentTimeMillis());
    }

    /**
     * 本地删除股票后调用。
     * 需在子线程调用。
     */
    public void notifyStockDeleted(final String code) throws Exception {
        if (!client.isLoggedIn()) return;
        if (!syncing.compareAndSet(false, true)) return;
        try {
            pendingRetry = new Runnable() {
                @Override
                public void run() {
                    try {
                        notifyStockDeleted(code);
                    } catch (Exception e) {
                        Log.w(TAG, "重试删除云端股票失败: " + e.getMessage());
                    }
                }
            };
            client.deleteStock(code);
            pendingRetry = null; // 成功，清除待重试
            removeLocalTimestamp(code);
            // 服务器已软删除，记录墓碑 code，防止后续全量上传把该股票再次上传复活
            serverDeletedCodes.add(code);
            Log.i(TAG, "已删除云端股票(软删墓碑): " + code);
        } catch (SyncClient.SyncUnauthorizedException e) {
            // 登录态失效：已由 SyncClient/SyncManager 处理，pendingRetry 已记录本次删除操作
            throw e;
        } catch (Exception e) {
            pendingRetry = null; // 非鉴权失败，不标记为待重试
            throw e;
        } finally {
            syncing.set(false);
        }
    }

    /**
     * 会话失效处理：通知 UI 引导重新登录。
     * pendingRetry 已在对应同步操作中记录，重新登录成功后由 retryPending() 自动重试。
     */
    private void handleSessionExpired(String message) {
        Log.w(TAG, "会话失效: " + message);
        postMsg("登录已失效，请重新登录");
        if (sessionExpiredListener != null) {
            try {
                sessionExpiredListener.onSessionExpired(message);
            } catch (Exception ignore) {}
        }
    }

    /**
     * 重新登录成功后调用：若有待重试的操作（如修改预警后上传），自动重试。
     * 需在子线程调用。
     */
    public void retryPending() {
        final Runnable r = pendingRetry;
        pendingRetry = null;
        if (r != null) {
            new Thread(r).start();
        }
    }

    /** 是否存在待重试的操作 */
    public boolean hasPendingRetry() {
        return pendingRetry != null;
    }

    // ==================== 定时拉取合并 ====================

    /**
     * 启动 10 秒轮询。重复调用安全。
     */
    public void startPeriodicSync() {
        if (pollingStarted) return;
        if (!client.isLoggedIn()) return;
        pollingStarted = true;
        handler.postDelayed(pollRunnable, POLL_INTERVAL_MS);
        Log.i(TAG, "已启动 " + (POLL_INTERVAL_MS / 1000) + " 秒轮询同步");
    }

    /**
     * 停止轮询。
     */
    public void stopPeriodicSync() {
        if (pollingStarted) {
            handler.removeCallbacks(pollRunnable);
            pollingStarted = false;
        }
    }

    /**
     * 手动拉取一次并合并（供轮询/手动刷新调用）。需在子线程。
     */
    public boolean pullAndMerge() {
        if (!client.isLoggedIn()) return false;
        if (!syncing.compareAndSet(false, true)) return false;
        try {
            SyncClient.SyncStockResult result = client.getStocks();
            handleClearStockIfNeeded(result.clearStock);
            applyServerStocks(result.stocks, true);
            return true;
        } catch (SyncClient.SyncUnauthorizedException e) {
            // 登录态失效：SyncClient 已清除登录态并触发重新登录引导，无需额外处理
            Log.w(TAG, "拉取失败(401,已触发重新登录): " + e.getMessage());
            notifyPullFailure();
            return false;
        } catch (Exception e) {
            Log.w(TAG, "拉取失败: " + e.getMessage());
            notifyPullFailure();
            return false;
        } finally {
            syncing.set(false);
        }
    }

    // 轮询失败给 UI 的节流提示：最多每 60 秒提示一次，避免 10 秒轮询下刷屏
    private volatile long lastPullFailureHint = 0L;
    private static final long PULL_FAILURE_HINT_INTERVAL_MS = 60_000L;

    private void notifyPullFailure() {
        long now = System.currentTimeMillis();
        if (now - lastPullFailureHint < PULL_FAILURE_HINT_INTERVAL_MS) return;
        lastPullFailureHint = now;
        postMsg("同步失败：无法从服务器拉取最新数据，请检查网络或重新登录");
    }

    // ==================== 本地数据收集 ====================

    /**
     * 收集本地全部股票为同步格式（带 last_modified）。
     */
    private List<SyncClient.SyncStock> collectLocalStocks(StockMng stockMng) {
        List<SyncClient.SyncStock> out = new java.util.ArrayList<>();
        List<StockMng> list = stockMng.getStocks();
        if (list == null) return out;
        for (StockMng s : list) {
            String code = s.GetCode();
            // 防复活：服务器已标记软删除(deleted:true 墓碑)的本地股票不要重复上传，
            // 否则会把已删除的股票重新上传到服务器复活。
            if (serverDeletedCodes.contains(code)) {
                Log.i(TAG, "跳过上传：服务器已软删除该股票 " + code);
                continue;
            }
            SyncClient.SyncStock sync = new SyncClient.SyncStock();
            sync.code = code;
            sync.name = s.GetName();
            sync.price_h = s.GetHigh();
            sync.price_l = s.GetLow();
            sync.rise = s.GetRise();
            sync.drop = s.GetDrop();
            sync.valid = s.IsValid() ? 1 : 0;
            sync.last_modified = getLocalTimestamp(code, System.currentTimeMillis());
            out.add(sync);
        }
        return out;
    }

    /**
     * 将服务器股票列表应用到本地 SQLite。
     * @param server 服务器返回的完整列表（已合并）
     * @param preserveLocalTs 是否保留本地已有的 last_modified（用于拉取合并场景；
     *                        全量下载场景传 false 直接用服务器 ts）
     */
    private void applyServerStocks(List<SyncClient.SyncStock> server, boolean preserveLocalTs) {
        if (server == null) return;
        StockMng stockMng = new StockMng(sqlite);
        // 收集服务器上的所有 code，用于清理本地被删除的
        Map<String, SyncClient.SyncStock> serverMap = new HashMap<>();
        // 记录服务器已软删除(deleted:true 墓碑)的 code，供全量上传过滤防复活
        serverDeletedCodes.clear();
        for (SyncClient.SyncStock s : server) {
            if (s != null && s.deleted) {
                serverDeletedCodes.add(s.code);
            } else if (s != null) {
                serverMap.put(s.code, s);
            }
        }

        // 1. 本地股票，若服务器没有（且非墓碑）或该 code 已被服务器软删除(deleted:true) -> 删除本地并清时间戳
        List<StockMng> local = stockMng.getStocks();
        if (local != null) {
            for (StockMng s : local) {
                String code = s.GetCode();
                if (!serverMap.containsKey(code) || serverDeletedCodes.contains(code)) {
                    try {
                        stockMng.Delete(code);
                        removeLocalTimestamp(code);
                    } catch (Exception e) {
                        Log.w(TAG, "删除本地股票失败: " + code, e);
                    }
                }
            }
        }

        // 2. 写入/更新服务器列表
        boolean addedNewStock = false;
        for (SyncClient.SyncStock s : serverMap.values()) {
            try {
                boolean exists = stockExists(stockMng, s.code);
                if (exists) {
                    stockMng.Get(s.code);
                    stockMng.Set(s.code, s.name, s.price_h, s.price_l, s.rise, s.drop, s.valid == 1);
                    stockMng.Update();
                } else {
                    stockMng.Set(s.code, s.name, s.price_h, s.price_l, s.rise, s.drop, s.valid == 1);
                    stockMng.Add();
                    addedNewStock = true; // 本机原先没有、由另一端同步过来的新股票
                }
                // 记录时间戳
                long serverTs = s.last_modified > 0 ? s.last_modified : System.currentTimeMillis();
                long localTs = getLocalTimestamp(s.code, 0);
                if (preserveLocalTs && localTs > 0 && localTs > serverTs) {
                    // 拉取合并场景（preserveLocalTs=true）：本地最近修改过该股票（本地 ts 更新），
                    // 服务器返回的可能仍是旧时间戳，此时不能覆盖本地时间戳，
                    // 否则下次上传会携带被覆盖的旧时间戳，导致本次修改在服务端合并时被判定为更旧而被忽略。
                    Log.i(TAG, "保留本地较新的时间戳: " + s.code + " localTs=" + localTs + " > serverTs=" + serverTs);
                    // 不覆盖，保留本地 localTs
                } else {
                    long ts = serverTs;
                    saveLocalTimestamp(s.code, ts);
                }
            } catch (Exception e) {
                Log.w(TAG, "写入本地股票失败: " + s.code, e);
            }
        }

        // 问题2：同步新增了股票（另一端同步过来的新预警），让后台报警短暂让路，
        // 等 UI 先刷新价格并展示后再报警，避免“收到同步数据立即报警、界面价格却未更新”。
        if (addedNewStock) {
            Compare.markSyncNewStock();
        }
    }

    private boolean stockExists(StockMng m, String code) {
        try {
            m.Get(code);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 根据服务器股票列表刷新“已软删除墓碑”code 集合。
     * 仅用于填充 serverDeletedCodes，不写本地数据库。
     */
    private void refreshServerDeleted(List<SyncClient.SyncStock> server) {
        if (server == null) return;
        serverDeletedCodes.clear();
        for (SyncClient.SyncStock s : server) {
            if (s != null && s.deleted) {
                serverDeletedCodes.add(s.code);
            }
        }
    }

    // ==================== MA 数据应用 ====================

    private void applyServerMas(List<SyncClient.SyncMaPrice> mas) {
        if (mas == null) return;
        MaPriceMng maMng = new MaPriceMng(sqlite);
        // 先清空所有本地 MA，再写入服务器数据（以服务器为权威）
        // 简单起见：逐条覆盖
        for (SyncClient.SyncMaPrice m : mas) {
            float[] cur = maMng.getMaPrices(m.code);
            if (cur != null && m.period > 0) {
                int idx = periodIndex(m.period);
                if (idx >= 0) {
                    cur[idx] = m.price;
                    maMng.saveMaPrices(m.code, cur);
                }
            }
        }
    }

    private int periodIndex(int period) {
        for (int i = 0; i < MaPriceMng.MA_PERIODS.length; i++) {
            if (MaPriceMng.MA_PERIODS[i] == period) return i;
        }
        return -1;
    }

    // ==================== 跨端清空(clear_stock) ====================

    /**
     * 检查服务器 clear_stock：若服务器版本 > 本地已见版本，则说明其它端已执行“清空股票”，
     * 需同步清空本地数据库与时间戳，并把本地已见版本更新为服务器值。
     * @return true 表示发生了清理（其它端已清空并已同步本地）
     */
    private boolean handleClearStockIfNeeded(int serverClearStock) {
        int localSeen = prefs().getInt(PREF_CLEAR_STOCK, 0);
        if (serverClearStock > localSeen) {
            Log.i(TAG, "检测到跨端清空: server clear_stock=" + serverClearStock + " > local=" + localSeen);
            sqlite.clearSyncStocks();   // 清空 stock_info / ma_price / ma_alarm_log
            clearLocalTimestamps();     // 清空本地 last_modified 时间戳
            prefs().edit().putInt(PREF_CLEAR_STOCK, serverClearStock).apply();
            postMsg("其它端已清空股票，本地已同步清空");
            return true;
        }
        return false;
    }

    /**
     * 清空本机全部同步数据（股票/均线/预警日志）并调用服务器接口清空其它端。
     * 供“清空股票-同时清除其它客户端”使用。需在子线程调用。
     * @return 服务器返回的最新 clear_stock 版本号；失败抛异常
     */
    public int clearStocksAcrossClients() throws Exception {
        if (!client.isLoggedIn()) throw new Exception("未登录");
        if (!syncing.compareAndSet(false, true)) throw new Exception("正在同步中，请稍后再试");
        try {
            // 1. 通知服务器清空其它端数据，拿到新版本号
            int newVersion = client.clearStock();
            // 2. 清空本地数据库与时间戳
            sqlite.clearSyncStocks();
            clearLocalTimestamps();
            // 3. 更新本地已见版本号
            prefs().edit().putInt(PREF_CLEAR_STOCK, newVersion).apply();
            Log.i(TAG, "本地清空完成，clear_stock=" + newVersion);
            return newVersion;
        } finally {
            syncing.set(false);
        }
    }

    /** 清空本地所有 last_modified 时间戳 */
    private void clearLocalTimestamps() {
        android.content.SharedPreferences.Editor ed = prefs().edit();
        for (java.util.Map.Entry<String, ?> e : prefs().getAll().entrySet()) {
            if (e.getKey() != null && e.getKey().startsWith(PREF_TS_PREFIX)) {
                ed.remove(e.getKey());
            }
        }
        ed.apply();
    }

    // ==================== last_modified 持久化 ====================

    private SharedPreferences prefs() {
        return PreferenceManager.getDefaultSharedPreferences(context);
    }

    private long getLocalTimestamp(String code, long def) {
        long v = prefs().getLong(PREF_TS_PREFIX + code, 0);
        return v > 0 ? v : def;
    }

    private void saveLocalTimestamp(String code, long ts) {
        prefs().edit().putLong(PREF_TS_PREFIX + code, ts).apply();
    }

    private void removeLocalTimestamp(String code) {
        prefs().edit().remove(PREF_TS_PREFIX + code).apply();
    }

    // ==================== 报警日志上传 ====================

    /**
     * 将本地报警日志上传（简化：合并上传后不去重本地，靠服务端 id 去重）。
     * 需在子线程调用。
     */
    public void uploadLocalAlarmLogs() throws Exception {
        if (!client.isLoggedIn()) return;
        java.util.List<SyncClient.SyncAlarmLog> logs = collectAlarmLogs();
        if (logs == null || logs.isEmpty()) return;
        client.uploadAlarmLogs(logs);
    }

    private java.util.List<SyncClient.SyncAlarmLog> collectAlarmLogs() {
        java.util.List<SyncClient.SyncAlarmLog> out = new java.util.ArrayList<>();
        // 读取通用报警日志表 alarm_log
        try {
            android.database.Cursor c = sqlite.getReadableDatabase().rawQuery(
                "SELECT code, name, message, alarm_type, create_time FROM alarm_log ORDER BY _id DESC LIMIT 100", null);
            while (c.moveToNext()) {
                SyncClient.SyncAlarmLog l = new SyncClient.SyncAlarmLog();
                String name = c.getString(1);
                String code = c.getString(0);
                l.id = "al_" + code + "_" + c.getString(3) + "_" + c.getString(4);
                l.code = code == null ? "" : code;
                l.type = c.getString(3) == null ? "alarm" : c.getString(3);
                l.content = (name == null ? "" : name) + " " + (c.getString(2) == null ? "" : c.getString(2));
                // create_time 是 SQLite DATETIME，转成 ISO
                l.create_time = c.getString(4);
                out.add(l);
            }
            c.close();
        } catch (Exception e) {
            Log.w(TAG, "读取报警日志失败: " + e.getMessage());
        }
        return out;
    }
}
