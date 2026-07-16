/**
 *
 */
package alarm.stock;

import android.content.Context;

import android.database.sqlite.SQLiteDatabase;

import android.database.sqlite.SQLiteDatabase.CursorFactory;

import android.database.sqlite.SQLiteOpenHelper;

import static alarm.stock.Constants.*;


/**
 * @author ipdiy
 *
 */
public class StockSQLite extends SQLiteOpenHelper {

	public StockSQLite(Context context, String name, CursorFactory factory, int version) {
		super(context, name, factory, version);
	}

	@Override
	public void onCreate(SQLiteDatabase db) {
		db.execSQL(TABLE_CREATE);
		db.execSQL(TABLE_MA_CREATE);
		db.execSQL(TABLE_MA_ALARM_LOG_CREATE);
	}

	@Override
	public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
		if (oldVersion < 6) {
			// 版本 5→6：新增 ma_price 表
			db.execSQL(TABLE_MA_CREATE);
		}
		if (oldVersion < 7) {
			// 版本 6→7：新增 ma_alarm_log 表
			db.execSQL(TABLE_MA_ALARM_LOG_CREATE);
		}
		if (oldVersion < 5) {
			// 老版本直接重建
			db.execSQL("DROP TABLE IF EXISTS " + TABLE_NAME);
			onCreate(db);
		}
	}
}
