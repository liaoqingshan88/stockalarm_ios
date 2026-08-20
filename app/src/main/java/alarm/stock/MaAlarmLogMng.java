package alarm.stock;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;

import static alarm.stock.Constants.*;

/**
 * 均线报警日志管理类。
 * 记录每次均线报警的触发，用于当天去重。
 */
import static alarm.stock.Constants.*;

public class MaAlarmLogMng {

    private StockSQLite m_sqlite;

    public MaAlarmLogMng(StockSQLite sqlite) {
        m_sqlite = sqlite;
    }

    /**
     * 检查某只股票在当天是否已经报过某条均线的同方向报警。
     *
     * @param code    股票代码
     * @param period  均线周期（5, 10, 20, 30, 60, 120, 250）
     * @param type    报警类型：1 = 上穿, 2 = 下穿
     * @param dateStr 日期字符串（yyyyMMdd）
     * @return true 表示当天已报过
     */
    public boolean isAlreadyAlarmedToday(String code, int period, int type, String dateStr) {
        SQLiteDatabase db = m_sqlite.getReadableDatabase();
        Cursor cursor = null;
        try {
            cursor = db.query(TABLE_MA_ALARM_LOG,
                    new String[]{MA_ALARM_F_ID},
                    MA_ALARM_F_CODE + "=? AND " +
                    MA_ALARM_F_PERIOD + "=? AND " +
                    MA_ALARM_F_TYPE + "=? AND " +
                    MA_ALARM_F_DATE + "=?",
                    new String[]{code, String.valueOf(period), String.valueOf(type), dateStr},
                    null, null, null);
            return cursor.moveToFirst();
        } catch (Exception e) {
            Log.e("MaAlarmLogMng", "isAlreadyAlarmedToday error: " + e.getMessage(), e);
            return false;
        } finally {
            if (cursor != null) cursor.close();
        }
    }

    /**
     * 记录一次均线报警。
     *
     * @param code    股票代码
     * @param name    股票名称
     * @param period  均线周期
     * @param type    报警类型：1 = 上穿, 2 = 下穿
     * @param maPrice 均线价格
     * @param nowPrice 当前股价
     * @param dateStr 日期字符串（yyyyMMdd）
     */
    public void recordAlarm(String code, String name, int period, int type, float maPrice, float nowPrice, String dateStr) {
        SQLiteDatabase db = m_sqlite.getWritableDatabase();
        try {
            ContentValues values = new ContentValues();
            values.put(MA_ALARM_F_CODE, code);
            values.put("name", name != null ? name : "");
            values.put(MA_ALARM_F_PERIOD, period);
            values.put(MA_ALARM_F_TYPE, type);
            values.put(MA_ALARM_F_MA_PRICE, maPrice);
            values.put(MA_ALARM_F_NOW_PRICE, nowPrice);
            values.put(MA_ALARM_F_DATE, dateStr);
            values.put(MA_ALARM_F_TIME, System.currentTimeMillis());
            db.insert(TABLE_MA_ALARM_LOG, null, values);
        } catch (Exception e) {
            Log.e("MaAlarmLogMng", "recordAlarm error: " + e.getMessage(), e);
        }
    }

    /**
     * 清理指定日期之前的历史日志（不会清理当天的）。
     *
     * @param beforeDateStr 清理该日期之前（含）的日志，格式 yyyyMMdd
     */
    public void cleanOldLogs(String beforeDateStr) {
        SQLiteDatabase db = m_sqlite.getWritableDatabase();
        try {
            int deleted = db.delete(TABLE_MA_ALARM_LOG,
                    MA_ALARM_F_DATE + "<?",
                    new String[]{beforeDateStr});
            if (deleted > 0) {
                Log.i("MaAlarmLogMng", "清理了 " + deleted + " 条旧报警日志");
            }
        } catch (Exception e) {
            Log.e("MaAlarmLogMng", "cleanOldLogs error: " + e.getMessage(), e);
        }
    }
}
