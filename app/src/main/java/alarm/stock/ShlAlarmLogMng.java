package alarm.stock;

import java.util.List;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import android.util.Log;

/**
 * 次高低点报警日志管理。
 * 记录每次次高/次低报警到数据库中，用于历史查询。
 */
public class ShlAlarmLogMng {
    private static final String TAG = "ShlAlarmLog";
    private static final int DB_VERSION = 1;
    
    private StockSQLite db;
    
    public ShlAlarmLogMng(StockSQLite db) {
        this.db = db;
    }
    
    /**
     * 记录次高低点报警。
     */
    public void recordAlarm(String code, String name, String direction, float price, float amplitude, float dayLow, float dayHigh, int count, int maxCount, String todayStr) {
        try {
            db.recordShlAlarm(code, name, direction, price, amplitude, dayLow, dayHigh, count, maxCount, todayStr);
        } catch (Exception e) {
            Log.e(TAG, "记录报警失败: " + e.getMessage(), e);
        }
    }
    
    /**
     * 查询某段时间内的报警历史。
     */
    public List<ShlAlarmLog> getAlarmHistory(int limit) {
        try {
            // Use queryShlAlarmHistory which returns a Cursor; convert to list
            android.database.Cursor cursor = db.queryShlAlarmHistory(limit);
            List<ShlAlarmLog> result = new ArrayList<ShlAlarmLog>();
            while (cursor.moveToNext()) {
                ShlAlarmLog log = new ShlAlarmLog();
                log.id = cursor.getInt(cursor.getColumnIndexOrThrow("_id"));
                log.code = cursor.getString(cursor.getColumnIndexOrThrow("code"));
                log.name = cursor.getString(cursor.getColumnIndexOrThrow("name"));
                log.direction = cursor.getString(cursor.getColumnIndexOrThrow("direction"));
                log.price = cursor.getFloat(cursor.getColumnIndexOrThrow("price"));
                log.amplitude = cursor.getFloat(cursor.getColumnIndexOrThrow("amplitude"));
                log.dayLow = cursor.getFloat(cursor.getColumnIndexOrThrow("day_low"));
                log.dayHigh = cursor.getFloat(cursor.getColumnIndexOrThrow("day_high"));
                log.count = cursor.getInt(cursor.getColumnIndexOrThrow("alarm_count"));
                log.maxCount = cursor.getInt(cursor.getColumnIndexOrThrow("max_count"));
                log.dateStr = cursor.getString(cursor.getColumnIndexOrThrow("alarm_date"));
                result.add(log);
            }
            cursor.close();
            return result;
        } catch (Exception e) {
            Log.e(TAG, "查询历史失败: " + e.getMessage(), e);
            return new ArrayList<ShlAlarmLog>();
        }
    }
    
    /**
     * 清空历史。
     */
    public void clearHistory() {
        try {
            db.clearShlAlarmHistory();
        } catch (Exception e) {
            Log.e(TAG, "清空历史失败: " + e.getMessage(), e);
        }
    }
    
    /**
     * 次高低点报警日志记录。
     */
    public static class ShlAlarmLog {
        public int id;
        public String code;
        public String name;
        public String direction;  // "high" 或 "low"
        public float price;
        public float amplitude;   // 振幅百分比（如 2.5 表示 2.5%）
        public float dayLow;
        public float dayHigh;
        public int count;
        public int maxCount;
        public String dateStr;    // yyyyMMdd
        public long timestamp;    // 毫秒时间戳
        
        public String getDisplayMessage() {
            if ("high".equals(direction)) {
                return String.format("次高突破(%.1f%%振幅) 价%.2f [%d/%d] 区间%.2f~%.2f", 
                    amplitude, price, count, maxCount, dayLow, dayHigh);
            } else {
                return String.format("次低跌破(%.1f%%振幅) 价%.2f [%d/%d] 区间%.2f~%.2f", 
                    amplitude, price, count, maxCount, dayLow, dayHigh);
            }
        }
        
        public String getDirectionLabel() {
            return "high".equals(direction) ? "次高突破" : "次低跌破";
        }
    }
}
