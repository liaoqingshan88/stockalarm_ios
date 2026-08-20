package alarm.stock;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

/**
 * 数据库管理
 * v7→v8: 新增次高低点报警日志表 shl_alarm_log
 * v8→v9: ma_alarm_log 表增加 name 列（旧版建表遗漏）
 */
public class StockSQLite extends SQLiteOpenHelper {

	private final static String DB_NAME = "alarm.stock.ipdiy";
	private final static int DB_VERSION = 9;
	private final static String TAG = "StockSQLite";
	
	public StockSQLite(Context context) {
		super(context, DB_NAME, null, DB_VERSION);
	}

	@Override
	public void onCreate(SQLiteDatabase db) {
		// 主表：stock_info（与 Constants.TABLE_NAME 保持一致，StockMng 等依赖此表名和字段名）
		String sql = "CREATE TABLE stock_info(_id INTEGER PRIMARY KEY AUTOINCREMENT, code TEXT, name TEXT NOT NULL, price_h REAL, price_l REAL, rise INTEGER, drop_ INTEGER, valid INTEGER)";
		Log.i(TAG, sql);
		db.execSQL(sql);

		// 均线价格表（ma_price）
		String ma_price = "CREATE TABLE IF NOT EXISTS ma_price(_id INTEGER PRIMARY KEY AUTOINCREMENT, code TEXT, period INTEGER, price REAL)";
		Log.i(TAG, ma_price);
		db.execSQL(ma_price);

		// 均线报警日志表 (v6)
		String ma_alarm = "CREATE TABLE IF NOT EXISTS ma_alarm_log(_id INTEGER PRIMARY KEY AUTOINCREMENT, code TEXT, name TEXT, period INTEGER, type INTEGER, ma_price REAL, now_price REAL, alarm_date TEXT, alarm_time INTEGER)";
		Log.i(TAG, ma_alarm);
		db.execSQL(ma_alarm);

		// 报警历史日志表 (v7)
		String alarm_log = "CREATE TABLE IF NOT EXISTS alarm_log(_id INTEGER PRIMARY KEY AUTOINCREMENT, code TEXT, name TEXT, message TEXT, alarm_type TEXT, create_time DATETIME DEFAULT CURRENT_TIMESTAMP)";
		Log.i(TAG, alarm_log);
		db.execSQL(alarm_log);

		// 次高低点报警日志表 (v8)
		String shl_alarm = "CREATE TABLE IF NOT EXISTS shl_alarm_log(_id INTEGER PRIMARY KEY AUTOINCREMENT, code TEXT, name TEXT, direction TEXT, price REAL, amplitude REAL, day_low REAL, day_high REAL, alarm_count INTEGER, max_count INTEGER, alarm_date TEXT, create_time DATETIME DEFAULT CURRENT_TIMESTAMP)";
		Log.i(TAG, shl_alarm);
		db.execSQL(shl_alarm);
	}

	@Override
	public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
		Log.i(TAG, "upgrade db from " + oldVersion + " to " + newVersion);
		if (oldVersion < 6) {
			// v5→v6: 新增 ma_alarm_log 表
			db.execSQL("CREATE TABLE IF NOT EXISTS ma_alarm_log(_id INTEGER PRIMARY KEY AUTOINCREMENT, code TEXT, name TEXT, period INTEGER, type INTEGER, ma_price REAL, now_price REAL, alarm_date TEXT, alarm_time INTEGER)");
		}
		if (oldVersion < 7) {
			// v6→v7: 新增 alarm_log 表
			db.execSQL("CREATE TABLE IF NOT EXISTS alarm_log(_id INTEGER PRIMARY KEY AUTOINCREMENT, code TEXT, name TEXT, message TEXT, alarm_type TEXT, create_time DATETIME DEFAULT CURRENT_TIMESTAMP)");
		}
		if (oldVersion < 8) {
			// v7→v8: 新增 shl_alarm_log 表
			db.execSQL("CREATE TABLE IF NOT EXISTS shl_alarm_log(_id INTEGER PRIMARY KEY AUTOINCREMENT, code TEXT, name TEXT, direction TEXT, price REAL, amplitude REAL, day_low REAL, day_high REAL, alarm_count INTEGER, max_count INTEGER, alarm_date TEXT, create_time DATETIME DEFAULT CURRENT_TIMESTAMP)");
		}
		if (oldVersion < 9) {
			// v8→v9: ma_alarm_log 表增加 name 列（旧版建表遗漏）
			try {
				db.execSQL("ALTER TABLE ma_alarm_log ADD COLUMN name TEXT DEFAULT ''");
			} catch (Exception e) {
				Log.w(TAG, "升级v9: ma_alarm_log.name 列已存在，跳过");
			}
		}
	}

	@Override
	public void onConfigure(SQLiteDatabase db) {
		super.onConfigure(db);
		db.setForeignKeyConstraintsEnabled(true);
	}

	// ==================== 次高低点报警日志 ====================
	
	public void recordShlAlarm(String code, String name, String direction, float price, float amplitude, float dayLow, float dayHigh, int count, int maxCount, String alarmDate) {
		SQLiteDatabase db = getWritableDatabase();
		ContentValues cv = new ContentValues();
		cv.put("code", code);
		cv.put("name", name);
		cv.put("direction", direction);
		cv.put("price", price);
		cv.put("amplitude", amplitude);
		cv.put("day_low", dayLow);
		cv.put("day_high", dayHigh);
		cv.put("alarm_count", count);
		cv.put("max_count", maxCount);
		cv.put("alarm_date", alarmDate);
		db.insert("shl_alarm_log", null, cv);
	}
	
	public Cursor queryShlAlarmHistory(int limit) {
		SQLiteDatabase db = getReadableDatabase();
		return db.rawQuery(
			"SELECT _id, code, name, direction, price, amplitude, day_low, day_high, alarm_count, max_count, alarm_date, create_time FROM shl_alarm_log ORDER BY _id DESC LIMIT ?",
			new String[]{String.valueOf(limit)}
		);
	}
	
	public void clearShlAlarmHistory() {
		SQLiteDatabase db = getWritableDatabase();
		db.execSQL("DELETE FROM shl_alarm_log");
	}

	// ==================== 跨端清空（clear_stock） ====================

	/**
	 * 清空与跨端同步相关的本地数据：股票表(stock_info)、均线价格表(ma_price)、
	 * 预警记录表(ma_alarm_log)。供“清除股票-同时清除其它客户端”时同步本地使用。
	 */
	public void clearSyncStocks() {
		SQLiteDatabase db = getWritableDatabase();
		try {
			db.execSQL("DELETE FROM stock_info");
		} catch (Exception e) {
			Log.w(TAG, "清空 stock_info 失败: " + e.getMessage());
		}
		try {
			db.execSQL("DELETE FROM ma_price");
		} catch (Exception e) {
			Log.w(TAG, "清空 ma_price 失败: " + e.getMessage());
		}
		try {
			db.execSQL("DELETE FROM ma_alarm_log");
		} catch (Exception e) {
			Log.w(TAG, "清空 ma_alarm_log 失败: " + e.getMessage());
		}
	}
}
