package alarm.stock;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.ArrayList;

import android.app.Activity;
import android.content.Context;
import android.database.Cursor;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

// 次高低点历史记录项
class ShlHistoryItem {
    String stockName;
    String stockCode;
    String direction;
    String message;
    String time;
}

public class AlarmHistoryActivity extends Activity {
	
	private static final String TAG = "AlarmHistory";
	private TextView mTvHistory;
	private ScrollView mScrollView;
	private StockSQLite mDb;
	private SimpleDateFormat mTimeFmt = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
		// 初始化数据库
		mDb = new StockSQLite(this);
		
		// 使用简单的垂直布局
		LinearLayout layout = new LinearLayout(this);
		layout.setOrientation(LinearLayout.VERTICAL);
		layout.setPadding(16, 16, 16, 16);
		
		TextView title = new TextView(this);
		title.setText("报警历史记录");
		title.setTextSize(20);
		title.setPadding(0, 0, 0, 20);
		layout.addView(title);
		
		// 切换 Tab 按钮
		LinearLayout tabLayout = new LinearLayout(this);
		tabLayout.setOrientation(LinearLayout.HORIZONTAL);
		
		Button btnMa = new Button(this);
		btnMa.setText("均线报警");
		btnMa.setTag("ma");
		tabLayout.addView(btnMa);
		
		Button btnShl = new Button(this);
		btnShl.setText("次高低点");
		btnShl.setTag("shl");
		tabLayout.addView(btnShl);
		
		Button btnClear = new Button(this);
		btnClear.setText("清空");
		tabLayout.addView(btnClear);
		
		layout.addView(tabLayout);
		
		mScrollView = new ScrollView(this);
		mTvHistory = new TextView(this);
		mTvHistory.setPadding(0, 16, 0, 0);
		mTvHistory.setTextSize(14);
		mScrollView.addView(mTvHistory);
		layout.addView(mScrollView);
		
		// 返回按钮
		Button btnBack = new Button(this);
		btnBack.setText("← 返回");
		btnBack.setPadding(0, 16, 0, 0);
		layout.addView(btnBack);
		btnBack.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				finish();
			}
		});
		
		setContentView(layout);
		
		// 默认显示均线报警
		btnMa.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				showMaHistory();
			}
		});
		
		btnShl.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				showShlHistory();
			}
		});
		
		btnClear.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				if ("ma".equals(v.getTag())) {
					// 清空均线历史
				} else {
					if (mDb != null) mDb.clearShlAlarmHistory();
					mTvHistory.setText("已清空");
				}
			}
		});
		
		showMaHistory();
	}
	
	private void showMaHistory() {
		if (mDb == null) {
			mTvHistory.setText("数据库未初始化");
			return;
		}
		
		StringBuilder sb = new StringBuilder();
		sb.append("===== 均线报警历史 =====\n\n");
		
		try {
			Cursor cursor = mDb.getReadableDatabase().rawQuery(
				"SELECT code, name, period, type, now_price, ma_price, alarm_date, alarm_time FROM ma_alarm_log ORDER BY _id DESC LIMIT 100",
				null
			);
			
			String[] MA_NAMES = {"MA5", "MA10", "MA20", "MA30", "MA60", "MA120", "MA250"};
			int count = 0;
			
			if (cursor != null && cursor.moveToFirst()) {
				do {
					String code = cursor.getString(0);
					String name = cursor.getString(1);
					int period = cursor.getInt(2);
					int type = cursor.getInt(3);
					float nowPrice = cursor.getFloat(4);
					float maPrice = cursor.getFloat(5);
					String date = cursor.getString(6);
					long alarmTime = cursor.getLong(7);
					
					String maName = "MA?";
					for (int i = 0; i < MaPriceMng.MA_PERIODS.length; i++) {
						if (MaPriceMng.MA_PERIODS[i] == period && i < MA_NAMES.length) {
							maName = MA_NAMES[i];
							break;
						}
					}
					
					String dirText = (type == 1) ? "上穿" : "下穿";
					String timeStr = alarmTime > 0 ? new SimpleDateFormat("HH:mm:ss").format(new java.util.Date(alarmTime)) : "";
					sb.append(name).append("(").append(code).append(") ");
					sb.append(dirText).append(maName);
					sb.append(" 价").append(String.format("%.2f", nowPrice));
					sb.append(" MA").append(String.format("%.2f", maPrice));
					sb.append(" [").append(timeStr.length() > 0 ? timeStr : date).append("]\n");
					count++;
				} while (cursor.moveToNext());
				cursor.close();
			}
			
			if (count == 0) {
				sb.append("暂无均线报警记录\n");
			} else {
				sb.append("\n共 ").append(count).append(" 条记录\n");
			}
		} catch (Exception e) {
			sb.append("查询失败: ").append(e.getMessage()).append("\n");
		}
		
		mTvHistory.setText(sb.toString());
	}
	
	private void showShlHistory() {
		if (mDb == null) {
			mTvHistory.setText("数据库未初始化");
			return;
		}
		
		StringBuilder sb = new StringBuilder();
		sb.append("===== 次高低点报警历史 =====\n\n");
		
		try {
			Cursor cursor = mDb.queryShlAlarmHistory(100);
			int count = 0;
			
			if (cursor != null && cursor.moveToFirst()) {
				do {
					String code = cursor.getString(1);
					String name = cursor.getString(2);
					String direction = cursor.getString(3);
					float price = cursor.getFloat(4);
					float amplitude = cursor.getFloat(5);
					float dayLow = cursor.getFloat(6);
					float dayHigh = cursor.getFloat(7);
					int alarmCount = cursor.getInt(8);
					int maxCount = cursor.getInt(9);
					String date = cursor.getString(10);
					String time = cursor.getString(11);
					
					String dirText = "high".equals(direction) ? "🔴次高突破" : "🟢次低跌破";
					sb.append(dirText).append(" ");
					sb.append(name).append("(").append(code).append(")\n");
					sb.append("  价").append(String.format("%.2f", price));
					sb.append(" 振幅").append(String.format("%.1f%%", amplitude));
					sb.append(" 区间").append(String.format("%.2f~%.2f", dayLow, dayHigh));
					sb.append(" [").append(alarmCount).append("/").append(maxCount).append("]");
					sb.append(" ").append(time != null ? time.substring(11, 19) : date).append("\n\n");
					count++;
				} while (cursor.moveToNext());
				cursor.close();
			}
			
			if (count == 0) {
				sb.append("暂无次高低点报警记录\n");
			} else {
				sb.append("共 ").append(count).append(" 条记录\n");
			}
		} catch (Exception e) {
			sb.append("查询失败: ").append(e.getMessage()).append("\n");
		}
		
		mTvHistory.setText(sb.toString());
	}
}
