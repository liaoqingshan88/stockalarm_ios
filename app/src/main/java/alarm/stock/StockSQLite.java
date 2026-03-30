/**
 *
 */
package alarm.stock;

import android.content.Context;

import android.database.sqlite.SQLiteDatabase;

import android.database.sqlite.SQLiteDatabase.CursorFactory;

import android.database.sqlite.SQLiteOpenHelper;

import static alarm.stock.Constants.*;

//import static android.provider.BaseColumns._ID;

//import android.database.sqlite.SQLiteOpenHelper;


/**
 * @author ipdiy
 *
 */
public class StockSQLite extends SQLiteOpenHelper {
	// private static final String DATABASE_NAME = DB_NAME;

	//private static final int DB_VERSION = 1;

	public StockSQLite(Context context, String name, CursorFactory factory, int version) {
		super(context, name, factory, version);
		// TODO Auto-generated constructor stub
	}
	/*
	public StockSQLite(Context context) {
	       super(context, DATABASE_NAME, factory, DATABASE_VERSION);
	       // TODO Auto-generated constructor stub
	}   */
	@Override
	public void onCreate(SQLiteDatabase db) {
		// TODO Auto-generated method stub
		db.execSQL(TABLE_CREATE);
	}


	@Override
	public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
		// TODO Auto-generated method stub
		db.execSQL("DROP TABLE IF EXISTS " + TABLE_NAME);
		onCreate(db);
	}
}
