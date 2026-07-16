package alarm.stock;

import android.app.Activity;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ListView;
import android.widget.SimpleAdapter;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;

import static alarm.stock.Constants.*;

/**
 * 均线报警历史记录页面。
 * 展示当天所有已触发的均线报警。
 */
public class AlarmHistoryActivity extends Activity {

    private StockSQLite mySqlite;
    private ListView listView;
    private TextView emptyText;
    private Button btnClear;
    private Button btnBack;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.alarm_history);

        mySqlite = new StockSQLite(this, DB_NAME, null, DB_VERSION);
        listView = (ListView) findViewById(R.id.history_list);
        emptyText = (TextView) findViewById(R.id.history_empty);
        btnClear = (Button) findViewById(R.id.btn_clear_history);
        btnBack = (Button) findViewById(R.id.btn_back);

        loadHistory();

        btnClear.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                SQLiteDatabase db = mySqlite.getWritableDatabase();
                db.delete(TABLE_MA_ALARM_LOG, null, null);
                loadHistory();
            }
        });

        btnBack.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadHistory();
    }

    private void loadHistory() {
        List<HashMap<String, String>> items = new ArrayList<>();
        SQLiteDatabase db = mySqlite.getReadableDatabase();
        Cursor cursor = null;

        try {
            cursor = db.query(TABLE_MA_ALARM_LOG,
                    new String[]{MA_ALARM_F_CODE, MA_ALARM_F_PERIOD, MA_ALARM_F_TYPE,
                                 MA_ALARM_F_MA_PRICE, MA_ALARM_F_NOW_PRICE, MA_ALARM_F_TIME},
                    null, null, null, null,
                    MA_ALARM_F_TIME + " DESC");

            while (cursor.moveToNext()) {
                String code = cursor.getString(0);
                int period = cursor.getInt(1);
                int type = cursor.getInt(2);
                float maPrice = cursor.getFloat(3);
                float nowPrice = cursor.getFloat(4);
                long timeMs = cursor.getLong(5);

                // 查询股票名称
                String name = getStockName(code);

                // 构建显示文本
                String typeStr = (type == 1) ? "上穿" : "下穿";
                String maName = "MA" + period;

                HashMap<String, String> item = new HashMap<>();
                item.put("name", (name != null ? name : code));
                item.put("code", code);
                item.put("msg", typeStr + maName +
                        " (均线:" + String.format("%.2f", maPrice) +
                        " 现价:" + String.format("%.2f", nowPrice) + ")");
                item.put("time", formatTime(timeMs));
                items.add(item);
            }
        } catch (Exception e) {
            android.util.Log.e("AlarmHistory", "loadHistory error: " + e.getMessage(), e);
        } finally {
            if (cursor != null) cursor.close();
        }

        if (items.isEmpty()) {
            listView.setVisibility(View.GONE);
            emptyText.setVisibility(View.VISIBLE);
        } else {
            listView.setVisibility(View.VISIBLE);
            emptyText.setVisibility(View.GONE);

            SimpleAdapter adapter = new SimpleAdapter(
                    this,
                    items,
                    R.layout.list_items_history,
                    new String[]{"name", "msg", "time"},
                    new int[]{R.id.hist_name, R.id.hist_msg, R.id.hist_time}
            );
            listView.setAdapter(adapter);
        }
    }

    private String getStockName(String code) {
        SQLiteDatabase db = mySqlite.getReadableDatabase();
        Cursor cursor = null;
        try {
            cursor = db.query(TABLE_NAME,
                    new String[]{F_NAME},
                    "code=?", new String[]{code},
                    null, null, null);
            if (cursor.moveToNext()) {
                return cursor.getString(0);
            }
        } catch (Exception e) {
            // ignore
        } finally {
            if (cursor != null) cursor.close();
        }
        return null;
    }

    private String formatTime(long timeMs) {
        SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss", Locale.getDefault());
        return sdf.format(new Date(timeMs));
    }
}
