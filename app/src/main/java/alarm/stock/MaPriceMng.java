package alarm.stock;

import java.util.ArrayList;
import java.util.List;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

/**
 * 均线价格管理类。
 * 管理每支股票的多条均线价格预警数据（MA5, MA10, MA20, MA30, MA60, MA120, MA250）。
 * 数据存储在 ma_price 表中。
 */
public class MaPriceMng {

    private StockSQLite m_sqlite;

    // 均线周期定义
    public static final int[] MA_PERIODS = {5, 10, 20, 30, 60, 120, 250};

    public MaPriceMng(StockSQLite sqlite) {
        m_sqlite = sqlite;
    }

    /**
     * 获取所有股票的均线价格数据。
     * 返回 Map，key 为股票代码，value 为该股票的各周期均线价格数组。
     */
    public java.util.HashMap<String, float[]> getAllMaPrices() {
        java.util.HashMap<String, float[]> result = new java.util.HashMap<>();
        SQLiteDatabase db = m_sqlite.getReadableDatabase();
        Cursor cursor = null;
        try {
            cursor = db.query(Constants.TABLE_MA_PRICE,
                    new String[]{Constants.MA_F_CODE, Constants.MA_F_PERIOD, Constants.MA_F_PRICE},
                    null, null, null, null, null);
            while (cursor.moveToNext()) {
                String code = cursor.getString(0);
                int period = cursor.getInt(1);
                float price = cursor.getFloat(2);
                float[] prices = result.get(code);
                if (prices == null) {
                    prices = new float[MA_PERIODS.length];
                    for (int i = 0; i < prices.length; i++) {
                        prices[i] = -1f; // -1 表示无数据
                    }
                    result.put(code, prices);
                }
                for (int i = 0; i < MA_PERIODS.length; i++) {
                    if (MA_PERIODS[i] == period) {
                        prices[i] = price;
                        break;
                    }
                }
            }
        } catch (Exception e) {
            android.util.Log.e("MaPriceMng", "getAllMaPrices error: " + e.getMessage(), e);
        } finally {
            if (cursor != null) cursor.close();
        }
        return result;
    }

    /**
     * 获取某支股票的均线价格数据。
     * 返回数组，顺序对应 MA_PERIODS。
     * 某周期无数据时为 -1。
     */
    public float[] getMaPrices(String code) {
        float[] prices = new float[MA_PERIODS.length];
        for (int i = 0; i < prices.length; i++) {
            prices[i] = -1f;
        }
        SQLiteDatabase db = m_sqlite.getReadableDatabase();
        Cursor cursor = null;
        try {
            cursor = db.query(Constants.TABLE_MA_PRICE,
                    new String[]{Constants.MA_F_PERIOD, Constants.MA_F_PRICE},
                    "code=?", new String[]{code}, null, null, null);
            while (cursor.moveToNext()) {
                int period = cursor.getInt(0);
                float price = cursor.getFloat(1);
                for (int i = 0; i < MA_PERIODS.length; i++) {
                    if (MA_PERIODS[i] == period) {
                        prices[i] = price;
                        break;
                    }
                }
            }
        } catch (Exception e) {
            android.util.Log.e("MaPriceMng", "getMaPrices error: " + e.getMessage(), e);
        } finally {
            if (cursor != null) cursor.close();
        }
        return prices;
    }

    /**
     * 保存某支股票的各周期均线价格数据。
     * 先删除该股票的所有旧数据，再插入新数据。
     */
    public void saveMaPrices(String code, float[] prices) {
        SQLiteDatabase db = m_sqlite.getWritableDatabase();
        try {
            // 先删除旧数据
            db.delete(Constants.TABLE_MA_PRICE, "code=?", new String[]{code});

            // 插入新数据
            for (int i = 0; i < MA_PERIODS.length && i < prices.length; i++) {
                if (prices[i] < 0) continue; // 跳过无效值
                ContentValues values = new ContentValues();
                values.put(Constants.MA_F_CODE, code);
                values.put(Constants.MA_F_PERIOD, MA_PERIODS[i]);
                values.put(Constants.MA_F_PRICE, prices[i]);
                db.insert(Constants.TABLE_MA_PRICE, null, values);
            }
        } catch (Exception e) {
            android.util.Log.e("MaPriceMng", "saveMaPrices error: " + e.getMessage(), e);
        }
    }

    /**
     * 删除某支股票的所有均线数据。
     */
    public void deleteMaPrices(String code) {
        SQLiteDatabase db = m_sqlite.getWritableDatabase();
        try {
            db.delete(Constants.TABLE_MA_PRICE, "code=?", new String[]{code});
        } catch (Exception e) {
            android.util.Log.e("MaPriceMng", "deleteMaPrices error: " + e.getMessage(), e);
        }
    }
}
