package alarm.stock;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Notification;
import android.app.NotificationManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.SystemClock;
import android.provider.MediaStore.Audio;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

//add by ipdiy for view
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.Button;
import android.widget.ListView;
import android.widget.RelativeLayout;
import android.widget.SimpleAdapter;
import android.widget.TextView;
import android.widget.EditText;
import android.widget.Toast;
import android.util.Log;
import android.view.*;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.View.OnCreateContextMenuListener;
import android.view.View.OnFocusChangeListener;
import android.content.ClipData;
import android.content.ClipboardManager;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;

//add by ipdiy for http
//import java.io.BufferedReader;
//import java.io.IOException;
//import java.io.InputStreamReader;
//import java.net.URL;
//import java.net.URLConnection;


//add by ipdiy for stock interface
import alarm.stock.SinaStockInfo.ParseStockInfoException;

import static alarm.stock.Constants.*;
//import static android.provider.BaseColumns._ID;
import android.content.ComponentName;
//import android.content.ContentValues;
import android.content.DialogInterface;
import android.content.Intent;

//add by ipdiy for stock mangage
import alarm.stock.StockMng.*;

/*
因为要修改数据库，所以调用getWritableDatabase()方法来获取数据库的一个写句柄，当然也可以获取
到读句柄。
add by ipdiy for notifi
import android.app.NotificationManager;
import android.app.Notification;
*/


import android.app.PendingIntent;
import android.app.NotificationChannel;
import android.content.Context;
//import android.content.Intent;
//import android.content.ContentValues;
import android.graphics.Color;
import android.media.AudioAttributes;
import android.media.RingtoneManager;
//import android.app.Activity;
import android.os.Build;

//import alarm.stock.StockNotify;
//import java.util.ArrayList;
//import java.util.List;

//import org.apache.commons.httpclient.HttpException;

//import android.app.Activity;
//import android.os.Bundle;
//import android.widget.ArrayAdapter;
//import android.widget.ListView;

import alarm.stock.Update;

// For image import and OCR
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.provider.MediaStore;
import java.io.InputStream;
import java.io.IOException;
import java.util.regex.Pattern;
import java.util.regex.Matcher;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions;


public class AlarmActivity extends Activity {
//	private static String[] FROM= {_ID,F_CODE,F_NAME,F_PRICE_H, F_PRICE_L, F_RISE, F_DROP, F_VALID};

//	private static String ORDER_BY = _ID;
	//*/
	private StockSQLite mySqlite;
	private Activity m_activity = this;
	private SinaStockClient mClient;
	StockMng m_stockMng;
	private MaPriceMng m_maMng;
	private MaDownloader m_maDownloader;
	private MaAlarmLogMng m_maAlarmLogMng;
	private static boolean maDataRefreshedToday = false;
	static  boolean is_first_run = true;
	static boolean is_notified = false;
	static boolean is_http_timeout = false;
	
	private String url_reg = "";
	private String url_apk = "";
	private String url_ver = "";

	private Login m_login; 
	private Update m_update;
	
	Handler handler;
	Runnable runnable;

	// 15 秒无操作自动隐藏顶部输入/按钮区（包含“导入股票图片”按钮）
	private static final long AUTO_HIDE_HEADER_DELAY_MS = 15_000L;
	private final Handler uiHandler = new Handler();
	private final Runnable autoHideHeaderRunnable = new Runnable() {
		@Override
		public void run() {
			hideHeaderControlsIfNeeded();
		}
	};

	private long statusMessageUntilUptimeMs = 0L;
	private final Runnable clearStatusMessageRunnable = new Runnable() {
		@Override
		public void run() {
			statusMessageUntilUptimeMs = 0L;
		}
	};

	private boolean isStatusMessageActive() {
		return statusMessageUntilUptimeMs > SystemClock.uptimeMillis();
	}

	public void showStatusMessageFor5s(String message) {
		final TextView eView = (TextView) findViewById(R.id.edit_stock_info);
		if (eView == null) return;
		statusMessageUntilUptimeMs = SystemClock.uptimeMillis() + 5000L;
		eView.setText(StockTime.GetTime() + " " + message);
		handler.removeCallbacks(clearStatusMessageRunnable);
		handler.postDelayed(clearStatusMessageRunnable, 5000L);
	}
	
	// For image import
	private static final int REQUEST_CODE_PICK_IMAGE = 1001;
	private TextRecognizer textRecognizer;
	/*
	protected void alarm_dialog() {
	      AlertDialog.Builder builder = new Builder(Main.this);
	      builder.setMessage("确认退出吗？");
	      builder.setTitle("提示");
	      builder.setPositiveButton("确认", new OnClickListener() {

	       @Override
	       public void onClick(DialogInterface dialog, int which) {
	        dialog.dismiss();
	        Main.this.finish();
	       }
	      });

	      builder.setNegativeButton("取消", new OnClickListener() {
	       @Override
	       handler.removeCallbacks(runnable);
	       public void onClick(DialogInterface dialog, int which) {
	        dialog.dismiss();
	       }
	      });

	      builder.create().show();
	     }

	  
	private void myNotify(Activity act) {
		StockNotify notify = new  StockNotify(act);
		notify.Notification("");
	}
  */
	/*
	private void alarm_dialog() {
	    AlertDialog.Builder builder = new AlertDialog.Builder(this);
	    //builder.setIcon(R.drawable.icon);
	    builder.setTitle("继续预警吗？");
	    builder.setPositiveButton("确定", new DialogInterface.OnClickListener() {
	        public void onClick(DialogInterface dialog, int whichButton) {
	            //这里添加点击确定后的逻辑
	        }
	    });
	    builder.setNegativeButton("取消", new DialogInterface.OnClickListener() {
	        public void onClick(DialogInterface dialog, int whichButton) {
	            //这里添加点击确定后的逻辑
	            handler.removeCallbacks(runnable);
	        }
	    });
	    builder.create().show();
	}
	*/


	static boolean stock_trade_time = false;
	int check_inter = 5;   //init check interval 5 second
	int check_count_down = check_inter;	//检查倒计时
	int err_times = 0;
	int enter_time = 0;

	// 当前需要突出显示的列表行（-1 表示不高亮）
	private int highlightedPosition = -1;
	// 排序/高亮：用 code 兜底，避免排序后 position 变化导致高亮错位
	private String highlightedCode = null;

	private enum SortColumn { NONE, NAME, PRICE, CHG }
	private enum SortOrder { NONE, ASC, DESC }
	private SortColumn sortColumn = SortColumn.NONE;
	private SortOrder sortOrder = SortOrder.NONE;
	private String lastHeaderNameBaseText = "名称";
	private String lastHeaderPriceBaseText = "现价";
	private String lastHeaderChgBaseText = "现涨";

	
	/** Called when the activity is first created. */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.main);

		showLastCrashIfAny();

		// 上报设备信息到服务器
		DeviceReporter.reportAsync(this);

		/*创建的快捷方式运行的程序，在有警告后点击出来的是新程序
		由手工复制的快捷方式不会，所以不创建2013.5.18
		//不升级，只创建快捷方式
		Update u = new Update(url_apk, url_ver,  AlarmActivity.this);
		if(! u.IfaddShortCut())
		{
			u.createSystemSwitcherShortCut();
		}
		*/
        enter_time = StockTime.GetCurTime();
		mySqlite = new StockSQLite(this, DB_NAME, null, DB_VERSION);
		m_stockMng = new StockMng(mySqlite);
		m_maMng = new MaPriceMng(mySqlite);
		m_maDownloader = new MaDownloader();
		m_maAlarmLogMng = new MaAlarmLogMng(mySqlite);
		//ListView list = (ListView) findViewById(R.id.ListView01);
		createListView(m_stockMng.getStocks());

		if(!StockTime.IsTradeTime()) {
			stock_trade_time = true;//以便runnable处理
		}

		final TextView eView = (TextView) findViewById(R.id.edit_stock_info) ;
		final ListView listview = (ListView) findViewById(R.id.ListView01);
		eView.setText(m_stockMng.GetCode() );

		bindSortHeaderClicks();

		// 启动时保持顶部区域可见，并启动无操作计时（仅在列表可滚动时才会自动隐藏）
		showHeaderControls();
		scheduleAutoHideHeader();

		final Button btn_stop=(Button)findViewById(R.id.btn_stop);
		btn_stop.setFocusable(true);
		btn_stop.requestFocus(); 
		btn_stop.setFocusableInTouchMode(true);

		m_update = new Update(AlarmActivity.this);
		m_login = new Login(m_update);
		m_update.SetLoginer(m_login);
		// 启动后自动检查更新（不依赖手动点击）
		new Handler().postDelayed(new Runnable() {
			@Override
			public void run() {
				try {
					m_update.check();
				} catch (Exception e) {
					Log.e("autoUpdate", "自动检查更新失败: " + e.getMessage(), e);
				}
			}
		}, 2000);
		
		// Initialize OCR text recognizer
		textRecognizer = TextRecognition.getClient(
			new ChineseTextRecognizerOptions.Builder().build()
		);
		
		//cmp.execute(m_stockMng);
		handler=new Handler();
		runnable=new Runnable() {
			@Override
			public void run() {
				// TODO Auto-generated method stub
				//要做的事情
				final TextView eView = (TextView) findViewById(R.id.edit_stock_info) ;
				// eTitle现在是LinearLayout，不需要TextView引用
				//eView.setText("begin..");
				
				String[] codes = m_stockMng.GetCodes();

				if(codes.length == 0 )
				{
					btn_stop.setText("开始");
					eView.setHeight(100);
					eView.setText("\n您还没有添加股票，请输入股票代码添加股票。\n");
					// eTitle现在是LinearLayout，用于显示列标题，帮助信息显示在eView中
					eView.append(Constants.help_info);
					eView.append(Constants.free_info);
					return;
				}
				// eTitle现在是LinearLayout，高度由布局文件控制

				//不这么来一下可能会死机
				if( is_first_run ) {
                   /*
					 Handler handler = new Handler();        
					         handler.postDelayed(new Runnable() {            
					            public void run() {
					                 // TODO Auto-generated method stub
					                DisableItems(m_stockMng.getStocks());
					             }
					         }, 2000);
					     */
					//DisableItems(m_stockMng.getStocks());
					is_first_run = false;
				}

				if (StockTime.IsTradeTime() ) {
					if( !is_http_timeout  )
					{
						if( !stock_trade_time ) {
							stock_trade_time = true;
							if (!isStatusMessageActive()) {
								eView.setText(StockTime.GetTime() + " 预警开始");
							}
						}
						else {
							if (!isStatusMessageActive()) {
								eView.setText(StockTime.GetTime() + " 预警中...");
							}
						}
					}
				}
				else {
					if( stock_trade_time) {
						//由运行转为暂停
						stock_trade_time = false;
						if (!isStatusMessageActive()) {
							eView.setText(StockTime.GetTime() +" 预警暂停");
						}

						try {
							// String codes[] = m_stockMng.GetCodes();
							mClient = SinaStockClient.getInstance();
							List<SinaStockInfo>  stockinfo  = mClient.getStockInfo(codes );
							
							ListView listview = (ListView) findViewById(R.id.ListView01);
							if( stockinfo != null )
							{
								ReflashStockPrice(listview, stockinfo);
							}
						}
						catch(Exception e) {
							eView.setText("price"+e.toString() );
						}

						handler.postDelayed(this, 1000);
						return;
					}
					else  {
						String now = StockTime.GetTime();
						if (!isStatusMessageActive()) {
							eView.setText(now +" 预警停止");
						}

						if( now.indexOf(Constants.time_of_auto_update) != -1) {
							m_update.check();
							handler.postDelayed(this, 1000*60);//只检测一次
						}
						else {
							handler.postDelayed(this, 1000);
						}

						return;
					}
				}

				if( StockTime.GetCurTime() - enter_time > 60 && StockTime.GetCurTime() - enter_time < 100)
				{
					if( !m_login.IsLogin() )
					{
						m_login.Do();
					}
				}
				//List<SinaStockInfo>  result = null;
				
				try
				{
					//*每5秒一次检测
					if(  (check_count_down --) > 0) {
						if( (!is_http_timeout) && ( check_count_down > 10) )
						{
							check_count_down = 1;	//由超时到正常，更快速的把“继续”改成“暂停"
						}
						handler.postDelayed(this, 1000);
						return;
					}
					//*/
					check_count_down = check_inter;

					Compare cmp = new Compare((AlarmActivity)m_activity, eView, listview, m_maMng, m_maAlarmLogMng);
					cmp.execute(m_stockMng);

					// 每30分钟刷新一次均线数据
					if (!maDataRefreshedToday || check_inter > 5) {
						// 首次运行或网络恢复后刷新均线
						refreshMaDataForAllStocks();
						maDataRefreshedToday = true;
					} else if (check_count_down % 360 == 0) {
						refreshMaDataForAllStocks();
					}

					System.out.println("timeout " +is_http_timeout +"T"+ err_times + "times");
					if( is_http_timeout )
					{   // IOException, ParseStockInfoException e)
						if (++err_times >= 3) {
							showStatusMessageFor5s("网络异常，请检查网络");
							btn_stop.setText("继续");
							check_inter = 60;
							//handler.postDelayed(this, 1000*60);
						}
						else {
							check_inter = 10;
							//handler.postDelayed(this, 1000*10);
						}
					}
					else
					{
						check_inter = 5;//恢复默认
						//能到这里，说明网络恢复正常，清除err_times
						if( err_times>0) {
							btn_stop.setText("暂停");
							err_times = 0;
						}
					}

					if( ! is_notified ) {
						//没有警告就继续检测
						handler.postDelayed(this, 1000);
					}
					else {
						if (!isStatusMessageActive()) {
							eView.setText(StockTime.GetTime() +" 预警停止");
						}
						btn_stop.setText("继续");
					}
				}
				catch(Exception e)
				{
					
				}
				
			}
		};
		
		handler.postDelayed(runnable, 1000*1);//每秒执行一次runnable.刷新时间 

		
		//showItems(cursor);
		//pause按钮
		//final Button btn_stop=(Button)findViewById(R.id.btn_stop);
		//static int i = -1;
		btn_stop.setOnClickListener(new View.OnClickListener() {
			public void onClick(View v) {
				final TextView eView = (TextView) findViewById(R.id.edit_stock_info) ;

				if( btn_stop.getText().equals("继续") || btn_stop.getText().equals("开始") ) {
					handler.postDelayed(runnable, 1000);//每两秒执行一次runnable.
					if (!isStatusMessageActive()) {
						eView.setText(StockTime.GetTime() +" 预警中...");
					}
					btn_stop.setText("暂停");
					is_notified = false;	//以便继续检测
				}
				else {
					handler.removeCallbacks(runnable);
					if (!isStatusMessageActive()) {
						eView.setText(StockTime.GetTime() +" 预警停止");
					}
					btn_stop.setText("继续");
				}
			}
		});
		
		
		//showItems(cursor);
		//添加 按钮
		final Button btn_Add=(Button)findViewById(R.id.btn_submit);
		btn_Add.setOnClickListener(new View.OnClickListener() {
			public void onClick(View v) {
				final EditText e_code = (EditText) findViewById(R.id.edit_stock_code) ;
				final EditText e_price_h = (EditText) findViewById(R.id.edit_price_h) ;
				final EditText e_price_l = (EditText) findViewById(R.id.edit_price_l) ;
				final EditText e_rise = (EditText) findViewById(R.id.edit_rise) ;
				final EditText e_drop = (EditText) findViewById(R.id.edit_drop) ;
				final TextView eView = (TextView) findViewById(R.id.edit_stock_info) ;
				// eTitle现在是LinearLayout，不需要TextView引用
				
				final String code = e_code.getText().toString();
				//String name = "noname";
				String s_price_h = e_price_h.getText().toString();
				String s_price_l = e_price_l.getText().toString();
				String s_rise = e_rise.getText().toString();
				String s_drop = e_drop.getText().toString();
				final Float price_h =  s_price_h.length()>0? Float.parseFloat(s_price_h):(float)0.0;
				final Float price_l = s_price_l.length()>0? Float.parseFloat(s_price_l):(float)0.0;
				final Integer rise = s_rise.length()>0? Integer.parseInt(s_rise):0;
				final Integer drop_ = s_drop.length()>0? Integer.parseInt(s_drop):0;
				//eView.setText(e_code.getText()+"\r\n");
				eView.setText("正在查询...");
				btn_Add.setEnabled(false); // 禁用按钮，防止重复点击
				
				// 在后台线程中执行网络请求
				new Thread(new Runnable() {
					@Override
					public void run() {
						StockMng stockMng = new StockMng(mySqlite);
						
						try {
							mClient = SinaStockClient.getInstance();
							List<SinaStockInfo>  stockinfo  = mClient.getStockInfo(new String[] {code});
							final SinaStockInfo stock1 = stockinfo.get(0);
							stockMng.Set(code,stock1.getName(), price_h, price_l, rise, drop_, true);
							stockMng.Add();

							// 添加股票后自动下载均线数据
							final String newCodeForMa = code;
							new Thread(new Runnable() {
								@Override
								public void run() {
									try {
										float[] mas = m_maDownloader.downloadAndCalculateMAs(newCodeForMa);
										if (mas != null) {
											m_maMng.saveMaPrices(newCodeForMa, mas);
										}
									} catch (Exception e) {
										android.util.Log.e("addStock", "MA download failed: " + e.getMessage(), e);
									}
								}
							}).start();
							
							// 获取所有股票的当前价格
							List<SinaStockInfo> allStockInfo = null;
							try {
								String[] allCodes = m_stockMng.GetCodes();
								if (allCodes.length > 0) {
									allStockInfo = mClient.getStockInfo(allCodes);
								}
							} catch (Exception e) {
								// 获取价格失败时保持为null
								allStockInfo = null;
							}
							
							final List<SinaStockInfo> finalAllStockInfo = allStockInfo;
							
							// 在主线程中更新UI
							runOnUiThread(new Runnable() {
								@Override
								public void run() {
									// 列标题现在是LinearLayout，不需要设置文本
									// 直接使用价格信息创建ListView
									createListView(m_stockMng.getStocks(), finalAllStockInfo);
									DisableItems(m_stockMng.getStocks());
									btn_Add.setEnabled(true);
								}
							});
						}
						catch (ParseStockInfoException e) {
							runOnUiThread(new Runnable() {
								@Override
								public void run() {
									eView.setText("股票代码不存在"+code);
									btn_Add.setEnabled(true);
								}
							});
							check_inter = 2;//add by ipdiy 2013.5.21 debug
						}
						catch (ExceptionAddStock e) {
							try {
								stockMng.Update();
								runOnUiThread(new Runnable() {
									@Override
									public void run() {
										createListView(m_stockMng.getStocks());
										DisableItems(m_stockMng.getStocks());
										btn_Add.setEnabled(true);
									}
								});
							}
							catch (Exception ee) {
								ee.printStackTrace();
								Log.e("add", "error: " + ee.getMessage(), ee);
								runOnUiThread(new Runnable() {
									@Override
									public void run() {
										btn_Add.setEnabled(true);
									}
								});
							}
						}
						catch (java.io.IOException e) {
							// 网络连接错误
							e.printStackTrace();
							Log.e("add", "network error: " + e.getMessage(), e);
							final String errorMsg = e.getMessage();
							final String userMsg;
							if (errorMsg != null && errorMsg.contains("Unable to resolve host")) {
								userMsg = "网络连接失败，请检查网络设置或稍后重试";
							} else if (errorMsg != null && errorMsg.contains("No address associated")) {
								userMsg = "无法连接到服务器，请检查网络连接";
							} else if (errorMsg != null && errorMsg.contains("NetworkOnMainThread")) {
								userMsg = "网络请求错误，请重试";
							} else {
								userMsg = "网络错误：" + (errorMsg != null ? errorMsg : "未知错误");
							}
							runOnUiThread(new Runnable() {
								@Override
								public void run() {
									showStatusMessageFor5s(userMsg);
									btn_Add.setEnabled(true);
								}
							});
						}
						catch (Exception e) {
							// TODO Auto-generated catch block
							e.printStackTrace();
							Log.e("add", "error: " + e.getMessage(),e);
							final String errorMsg = "发生错误：" + (e.getMessage() != null ? e.getMessage() : "未知错误");
							runOnUiThread(new Runnable() {
								@Override
								public void run() {
									eView.setText(errorMsg);
									btn_Add.setEnabled(true);
								}
							});
						}
					}
				}).start();
			}
		});

		
		final EditText e_code = (EditText) findViewById(R.id.edit_stock_code) ;
		//TextEdit eEdit = (TextEdit) findViewById(R.id.edit_stock_code) ;
		//股票代码输入后自动查询
		e_code.setOnFocusChangeListener(new OnFocusChangeListener() {
			final TextView eView = (TextView) findViewById(R.id.edit_stock_info) ;
			@Override
			public void onFocusChange(View v, boolean hasFocus) {
				final String code = e_code.getText().toString();

				if( hasFocus==false) {
					if (code.length() == 0) {
						return;
					}
					
					// 在后台线程中执行网络请求
					eView.setText("正在查询...");
					new Thread(new Runnable() {
						@Override
						public void run() {
							try {
								//查询股价
								mClient = SinaStockClient.getInstance();
								List<SinaStockInfo>  stockinfo  = mClient.getStockInfo(new String[] {code});
								final SinaStockInfo stock1 = stockinfo.get(0);
								
								//查询数据库
								StockMng stockMng = new StockMng(mySqlite);
								stockMng.Get(code);
								
								// 在主线程中更新UI
								runOnUiThread(new Runnable() {
									@Override
									public void run() {
										eView.append("\n"+stock1.toString());
										final EditText e_price_h = (EditText) findViewById(R.id.edit_price_h) ;
										final EditText e_price_l = (EditText) findViewById(R.id.edit_price_l) ;
										final EditText e_rise = (EditText) findViewById(R.id.edit_rise) ;
										final EditText e_drop = (EditText) findViewById(R.id.edit_drop) ;
										// 价格精确到分（保留两位小数）
										e_price_h.setText(String.format("%.2f", stockMng.GetHigh()));
										e_price_l.setText(String.format("%.2f", stockMng.GetLow()));
										e_rise.setText(""+stockMng.GetRise());
										e_drop.setText(""+stockMng.GetDrop());
										final Button btn_Add=(Button)findViewById(R.id.btn_submit);
										btn_Add.setText("更新预警");
									}
								});
							}
							catch (ExceptionStockNoExist e) {
								runOnUiThread(new Runnable() {
									@Override
									public void run() {
										// 新代码（列表里没有/数据库不存在）：清空阈值，按钮恢复“添加预警”
										final EditText e_price_h = (EditText) findViewById(R.id.edit_price_h) ;
										final EditText e_price_l = (EditText) findViewById(R.id.edit_price_l) ;
										final EditText e_rise = (EditText) findViewById(R.id.edit_rise) ;
										final EditText e_drop = (EditText) findViewById(R.id.edit_drop) ;
										e_price_h.setText("");
										e_price_l.setText("");
										e_rise.setText("");
										e_drop.setText("");
										final Button btn_Add=(Button)findViewById(R.id.btn_submit);
										btn_Add.setText("添加预警");
									}
								});
							}
							catch (ParseStockInfoException e) {
								runOnUiThread(new Runnable() {
									@Override
									public void run() {
										// 代码不存在：清空阈值，按钮恢复“添加预警”
										final EditText e_price_h = (EditText) findViewById(R.id.edit_price_h) ;
										final EditText e_price_l = (EditText) findViewById(R.id.edit_price_l) ;
										final EditText e_rise = (EditText) findViewById(R.id.edit_rise) ;
										final EditText e_drop = (EditText) findViewById(R.id.edit_drop) ;
										e_price_h.setText("");
										e_price_l.setText("");
										e_rise.setText("");
										e_drop.setText("");
										final Button btn_Add=(Button)findViewById(R.id.btn_submit);
										btn_Add.setText("添加预警");
										eView.append("\n股票代码不存在"+code);
									}
								});
								check_inter = 2;//add by ipdiy 2013.5.21  debug
							}
							catch (java.io.IOException e) {
								// 网络连接错误
								e.printStackTrace();
								Log.e("input", "network error: " + e.getMessage(), e);
								final String errorMsg = e.getMessage();
								final String userMsg;
								if (errorMsg != null && errorMsg.contains("Unable to resolve host")) {
									userMsg = "网络连接失败，请检查网络设置";
								} else if (errorMsg != null && errorMsg.contains("SSLHandshakeException")) {
									userMsg = "网络连接错误，请重试";
								} else {
									userMsg = "网络错误：" + (errorMsg != null ? errorMsg : "未知错误");
								}
								runOnUiThread(new Runnable() {
									@Override
									public void run() {
										// 查询失败：清空阈值，按钮恢复“添加预警”
										final EditText e_price_h = (EditText) findViewById(R.id.edit_price_h) ;
										final EditText e_price_l = (EditText) findViewById(R.id.edit_price_l) ;
										final EditText e_rise = (EditText) findViewById(R.id.edit_rise) ;
										final EditText e_drop = (EditText) findViewById(R.id.edit_drop) ;
										e_price_h.setText("");
										e_price_l.setText("");
										e_rise.setText("");
										e_drop.setText("");
										final Button btn_Add=(Button)findViewById(R.id.btn_submit);
										btn_Add.setText("添加预警");
										showStatusMessageFor5s(userMsg);
									}
								});
							}
							catch (Exception e) {
								// TODO Auto-generated catch block
								e.printStackTrace();
								Log.e("input", "error: " + e.getMessage(), e);
								final String errorMsg = "查询失败：" + (e.getMessage() != null ? e.getMessage() : "未知错误");
								runOnUiThread(new Runnable() {
									@Override
									public void run() {
										// 查询失败：清空阈值，按钮恢复“添加预警”
										final EditText e_price_h = (EditText) findViewById(R.id.edit_price_h) ;
										final EditText e_price_l = (EditText) findViewById(R.id.edit_price_l) ;
										final EditText e_rise = (EditText) findViewById(R.id.edit_rise) ;
										final EditText e_drop = (EditText) findViewById(R.id.edit_drop) ;
										e_price_h.setText("");
										e_price_l.setText("");
										e_rise.setText("");
										e_drop.setText("");
										final Button btn_Add=(Button)findViewById(R.id.btn_submit);
										btn_Add.setText("添加预警");
										showStatusMessageFor5s(errorMsg);
									}
								});
							}
						}
					}).start();
				}
				else {
					eView.setText("请输入股票代码：A股6位数字；港股可输入5位数字(如00700)或hk00700/00700.HK");
				}
			}
		});
		
		
		//升级按钮（长按可测试预警声音）
		Button btn_update =(Button)findViewById(R.id.btn_test);
		btn_update.setOnClickListener(new View.OnClickListener() {
			public void onClick(View v) {
				Toast.makeText(AlarmActivity.this, "正在检测版本", Toast.LENGTH_LONG).show();
				m_update.check();
			}
		});
		btn_update.setOnLongClickListener(new View.OnLongClickListener() {
			@Override
			public boolean onLongClick(View v) {
				Notification("测试预警：这是铃声测试");
				TextView eView = (TextView) findViewById(R.id.edit_stock_info);
				eView.setText(StockTime.GetTime() + " 已触发测试预警");
				Toast.makeText(AlarmActivity.this, "已触发测试预警", Toast.LENGTH_SHORT).show();
				return true;
			}
		});
		
		//帮助按钮
		Button btn_help =(Button)findViewById(R.id.btn_help);
		btn_help.setOnClickListener(new View.OnClickListener() {
			public void onClick(View v) {
				
				/*
				 Uri uri = Uri.parse("http://ipdiy.net:8080/mysoft/stockalarm/index.jsp");  
				 Intent it = new Intent(Intent.ACTION_VIEW, uri);  
				 startActivity(it);
				 */
				Intent intent = new Intent(AlarmActivity.this, Help.class); 
				startActivity(intent);
				 
			}
		});
			
		Button btn_share =(Button)findViewById(R.id.btn_share);
		btn_share.setOnClickListener(new View.OnClickListener() {
			public void onClick(View v) {
                Intent intent=new Intent(Intent.ACTION_SEND);
				intent.setType("text/plain"); 
				intent.putExtra(Intent.EXTRA_SUBJECT, "????"); 
				intent.putExtra(Intent.EXTRA_TEXT, share_info);  
				intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK); 
				startActivity(Intent.createChooser(intent, getTitle())); 
			}
		});
		
		//导入图片按钮
		Button btn_import_image = (Button)findViewById(R.id.btn_import_image);
		btn_import_image.setOnClickListener(new View.OnClickListener() {
			public void onClick(View v) {
				// 打开图片选择器 - 使用多种方式确保兼容性
				try {
					// 方法1: 使用ACTION_GET_CONTENT（兼容性最好，支持模拟器和真机）
					Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
					intent.setType("image/*");
					intent.addCategory(Intent.CATEGORY_OPENABLE);
					
					// 方法2: 如果不可用，尝试ACTION_PICK
					Intent pickIntent = new Intent(Intent.ACTION_PICK);
					pickIntent.setType("image/*");
					
					// 创建选择器，让用户选择使用哪个应用
					Intent chooserIntent = Intent.createChooser(intent, "选择图片");
					chooserIntent.putExtra(Intent.EXTRA_INITIAL_INTENTS, new Intent[]{pickIntent});
					
					if (chooserIntent.resolveActivity(getPackageManager()) != null) {
						startActivityForResult(chooserIntent, REQUEST_CODE_PICK_IMAGE);
					} else {
						Toast.makeText(AlarmActivity.this, "无法打开图片选择器\n模拟器提示：请先通过拖拽或ADB添加图片到设备", Toast.LENGTH_LONG).show();
					}
				} catch (Exception e) {
					Log.e("ImagePicker", "打开图片选择器失败: " + e.getMessage(), e);
					Toast.makeText(AlarmActivity.this, "打开图片选择器失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
				}
			}
		});
		
		//设置按钮
		Button btn_settings = (Button)findViewById(R.id.btn_settings);
		btn_settings.setOnClickListener(new View.OnClickListener() {
			public void onClick(View v) {
				Intent intent = new Intent(AlarmActivity.this, SettingsActivity.class);
				startActivity(intent);
			}
		});

		//历史消息按钮
		Button btn_history = (Button)findViewById(R.id.btn_history);
		btn_history.setOnClickListener(new View.OnClickListener() {
			public void onClick(View v) {
				Intent intent = new Intent(AlarmActivity.this, AlarmHistoryActivity.class);
				startActivity(intent);
			}
		});
		
	}

	private void showLastCrashIfAny() {
		try {
			File crashFile = new File(getFilesDir(), CrashCatcherApp.CRASH_FILE_NAME);
			if (!crashFile.exists() || crashFile.length() <= 0) {
				return;
			}
			StringBuilder sb = new StringBuilder();
			BufferedReader br = new BufferedReader(new FileReader(crashFile));
			try {
				String line;
				while ((line = br.readLine()) != null) {
					sb.append(line).append("\n");
				}
			} finally {
				try { br.close(); } catch (Exception ignore) {}
			}

			final String crashText = sb.toString();
			new AlertDialog.Builder(this)
					.setTitle("上次闪退原因")
					.setMessage(crashText.length() > 4000 ? crashText.substring(0, 4000) + "\n...(内容过长已截断)" : crashText)
					.setPositiveButton("复制", new DialogInterface.OnClickListener() {
						@Override
						public void onClick(DialogInterface dialog, int which) {
							try {
								ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
								if (cm != null) {
									cm.setPrimaryClip(ClipData.newPlainText("crash", crashText));
									Toast.makeText(AlarmActivity.this, "已复制崩溃信息", Toast.LENGTH_LONG).show();
								}
							} catch (Exception e) {
								Toast.makeText(AlarmActivity.this, "复制失败: " + e.getMessage(), Toast.LENGTH_LONG).show();
							}
						}
					})
					.setNegativeButton("清除", new DialogInterface.OnClickListener() {
						@Override
						public void onClick(DialogInterface dialog, int which) {
							try { crashFile.delete(); } catch (Exception ignore) {}
						}
					})
					.show();
		} catch (Exception e) {
			Log.e("CrashCatcher", "读取崩溃日志失败: " + e.getMessage(), e);
		}
	}

	@Override
	public void onResume() {
		super.onResume();
		//DisableItems(m_stockMng.getStocks());
		scheduleAutoHideHeader();
		try {
			ListView list = (ListView) findViewById(R.id.ListView01);
			if (list != null && list.getAdapter() instanceof android.widget.BaseAdapter) {
				((android.widget.BaseAdapter) list.getAdapter()).notifyDataSetChanged();
			}
		} catch (Exception ignore) {
		}
	}

	@Override
	protected void onPause() {
		super.onPause();
		cancelAutoHideHeader();
	}

	@Override
	public boolean dispatchTouchEvent(MotionEvent ev) {
		// 任意触摸/滚动都算用户操作：重置计时
		if (ev != null && (ev.getAction() == MotionEvent.ACTION_DOWN || ev.getAction() == MotionEvent.ACTION_MOVE)) {
			scheduleAutoHideHeader();
		}
		return super.dispatchTouchEvent(ev);
	}

	private void scheduleAutoHideHeader() {
		uiHandler.removeCallbacks(autoHideHeaderRunnable);
		uiHandler.postDelayed(autoHideHeaderRunnable, AUTO_HIDE_HEADER_DELAY_MS);
	}

	private void cancelAutoHideHeader() {
		uiHandler.removeCallbacks(autoHideHeaderRunnable);
	}

	private void showHeaderControls() {
		View header = findViewById(R.id.header_controls_container);
		if (header != null) {
			header.setVisibility(View.VISIBLE);
		}
	}

	private void hideHeaderControlsIfNeeded() {
		View header = findViewById(R.id.header_controls_container);
		if (header == null) return;
		ListView list = (ListView) findViewById(R.id.ListView01);
		if (list == null) return;
		if (list.getAdapter() == null || list.getAdapter().getCount() <= 0) return;

		// 只有在“列表内容需要滚动（能向下滚）”时才自动隐藏
		boolean canScrollDown;
		try {
			canScrollDown = list.canScrollVertically(1);
		} catch (Throwable t) {
			int last = list.getLastVisiblePosition();
			int count = list.getAdapter().getCount();
			canScrollDown = count > 0 && last < count - 1;
		}

		if (canScrollDown) {
			header.setVisibility(View.GONE);
		}
	}
	
	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		super.onActivityResult(requestCode, resultCode, data);
		if (m_update != null && m_update.handleInstallActivityResult(requestCode, resultCode, data)) {
			return;
		}
		
		if (requestCode == REQUEST_CODE_PICK_IMAGE) {
			if (resultCode == RESULT_OK && data != null) {
				Uri imageUri = data.getData();
				if (imageUri != null) {
					Log.d("ImagePicker", "选择的图片URI: " + imageUri.toString());
					processImage(imageUri);
				} else {
					Log.w("ImagePicker", "图片URI为空");
					Toast.makeText(this, "未选择图片", Toast.LENGTH_SHORT).show();
				}
			} else if (resultCode == RESULT_CANCELED) {
				Log.d("ImagePicker", "用户取消了图片选择");
				// 用户取消选择，不显示错误提示
			} else {
				Log.w("ImagePicker", "图片选择失败，resultCode: " + resultCode);
				Toast.makeText(this, "选择图片失败，请重试", Toast.LENGTH_SHORT).show();
			}
		}
	}
	
	private void processImage(Uri imageUri) {
		final TextView eView = (TextView) findViewById(R.id.edit_stock_info);
		eView.setText("正在识别图片中的股票信息...");
		
		// 在后台线程中处理图片
		new Thread(new Runnable() {
			@Override
			public void run() {
				try {
					// 从URI加载图片
					InputStream inputStream = getContentResolver().openInputStream(imageUri);
					Bitmap bitmap = BitmapFactory.decodeStream(inputStream);
					if (inputStream != null) {
						inputStream.close();
					}
					
					if (bitmap == null) {
						runOnUiThread(new Runnable() {
							@Override
							public void run() {
								eView.setText("无法读取图片，请重试");
								Toast.makeText(AlarmActivity.this, "无法读取图片", Toast.LENGTH_SHORT).show();
							}
						});
						return;
					}
					
					// 创建InputImage对象
					InputImage image = InputImage.fromBitmap(bitmap, 0);
					
					// 执行OCR识别
					textRecognizer.process(image)
						.addOnSuccessListener(visionText -> {
							// 识别成功，提取股票代码
							String recognizedText = visionText.getText();
							Log.d("OCR", "识别到的文本: " + recognizedText);
							
							// 从识别文本中提取股票代码
							List<String> stockCodes = extractStockCodes(recognizedText);
							
							if (stockCodes.isEmpty()) {
								runOnUiThread(new Runnable() {
									@Override
									public void run() {
										eView.setText("未在图片中找到股票代码\n识别到的文本:\n" + recognizedText);
										Toast.makeText(AlarmActivity.this, "未找到股票代码", Toast.LENGTH_LONG).show();
									}
								});
							} else {
								// 找到股票代码，自动添加到监控列表
								addStocksFromCodes(stockCodes, recognizedText);
							}
						})
						.addOnFailureListener(e -> {
							// 识别失败
							Log.e("OCR", "识别失败: " + e.getMessage(), e);
							runOnUiThread(new Runnable() {
								@Override
								public void run() {
									eView.setText("图片识别失败: " + e.getMessage());
									Toast.makeText(AlarmActivity.this, "识别失败", Toast.LENGTH_SHORT).show();
								}
							});
						});
					
				} catch (Exception e) {
					Log.e("ImageProcess", "处理图片失败: " + e.getMessage(), e);
					runOnUiThread(new Runnable() {
						@Override
						public void run() {
							eView.setText("处理图片失败: " + e.getMessage());
							Toast.makeText(AlarmActivity.this, "处理图片失败", Toast.LENGTH_SHORT).show();
						}
					});
				}
			}
		}).start();
	}
	
	/**
	 * 从识别文本中提取股票代码
	 * 股票代码通常是6位数字（沪市以6开头，深市以0或3开头）
	 * 改进版本：支持更灵活的匹配，能识别更多股票代码
	 */
	private List<String> extractStockCodes(String text) {
		List<String> codes = new ArrayList<>();
		
		// 记录原始文本用于调试
		Log.d("OCR", "原始识别文本: " + text);
		
		// 方法1: 港股（显式带HK标识）：HK00700 / hk 700 / 00700.HK
		Pattern patternHk1 = Pattern.compile("(?i)\\bHK\\s*([0-9]{1,5})\\b");
		Matcher matcherHk1 = patternHk1.matcher(text);
		while (matcherHk1.find()) {
			String digits = matcherHk1.group(1);
			if (digits != null) {
				String code = String.format("%05d", Integer.parseInt(digits));
				if (!codes.contains(code)) {
					codes.add(code);
					Log.d("OCR", "找到港股代码(方法1): " + code);
				}
			}
		}
		Pattern patternHk2 = Pattern.compile("(?i)\\b([0-9]{1,5})\\s*\\.\\s*HK\\b");
		Matcher matcherHk2 = patternHk2.matcher(text);
		while (matcherHk2.find()) {
			String digits = matcherHk2.group(1);
			if (digits != null) {
				String code = String.format("%05d", Integer.parseInt(digits));
				if (!codes.contains(code)) {
					codes.add(code);
					Log.d("OCR", "找到港股代码(方法1b): " + code);
				}
			}
		}

		// 方法2: 匹配标准的A股6位股票代码（使用单词边界）
		Pattern pattern1 = Pattern.compile("\\b([036]\\d{5})\\b");
		Matcher matcher1 = pattern1.matcher(text);
		while (matcher1.find()) {
			String code = matcher1.group(1);
			if (code.length() == 6 && !codes.contains(code)) {
				codes.add(code);
				Log.d("OCR", "找到股票代码(A股-方法2): " + code);
			}
		}
		
		// 方法3: 匹配连续的6位A股代码（不使用单词边界，更宽松）
		// 这样可以匹配表格中紧挨着的股票代码
		Pattern pattern2 = Pattern.compile("([036]\\d{5})");
		Matcher matcher2 = pattern2.matcher(text);
		while (matcher2.find()) {
			String code = matcher2.group(1);
			if (code.length() == 6 && !codes.contains(code)) {
				codes.add(code);
				Log.d("OCR", "找到股票代码(A股-方法3): " + code);
			}
		}
		
		// 方法4: 处理OCR可能出现的识别错误
		// 将常见的OCR错误字符替换（O->0, I->1, l->1等）
		String correctedText = text.replace('O', '0').replace('o', '0')
			.replace('I', '1').replace('l', '1').replace('|', '1')
			.replace('S', '5').replace('s', '5')
			.replace('Z', '2').replace('z', '2');
		
		Pattern pattern3 = Pattern.compile("([036]\\d{5})");
		Matcher matcher3 = pattern3.matcher(correctedText);
		while (matcher3.find()) {
			String code = matcher3.group(1);
			if (code.length() == 6 && !codes.contains(code)) {
				codes.add(code);
				Log.d("OCR", "找到股票代码(A股-方法4-纠错后): " + code);
			}
		}
		
		// 方法5: 匹配可能被空格或其他字符分隔的A股代码
		// 例如: "6 0 0 0 0 1" 或 "6-0-0-0-0-1"
		Pattern pattern4 = Pattern.compile("([036])[\\s\\-]?(\\d)[\\s\\-]?(\\d)[\\s\\-]?(\\d)[\\s\\-]?(\\d)[\\s\\-]?(\\d)");
		Matcher matcher4 = pattern4.matcher(correctedText);
		while (matcher4.find()) {
			String code = matcher4.group(1) + matcher4.group(2) + matcher4.group(3) 
				+ matcher4.group(4) + matcher4.group(5) + matcher4.group(6);
			if (code.length() == 6 && !codes.contains(code)) {
				codes.add(code);
				Log.d("OCR", "找到股票代码(A股-方法5-分隔符): " + code);
			}
		}

		// 方法6: 兜底匹配港股常见5位代码（以0开头），避免把年份/金额等误识别为股票代码
		Pattern patternHkLoose = Pattern.compile("\\b(0\\d{4})\\b");
		Matcher matcherHkLoose = patternHkLoose.matcher(correctedText);
		while (matcherHkLoose.find()) {
			String code = matcherHkLoose.group(1);
			if (code != null && !codes.contains(code)) {
				codes.add(code);
				Log.d("OCR", "找到港股代码(方法6-宽松): " + code);
			}
		}
		
		Log.d("OCR", "总共找到 " + codes.size() + " 个股票代码: " + codes.toString());
		return codes;
	}
	
	/**
	 * 将识别到的股票代码添加到监控列表
	 */
	private void addStocksFromCodes(List<String> stockCodes, String recognizedText) {
		final TextView eView = (TextView) findViewById(R.id.edit_stock_info);
		// eTitle现在是LinearLayout，不需要TextView引用
		
		final StringBuilder resultMessage = new StringBuilder();
		resultMessage.append("识别到 ").append(stockCodes.size()).append(" 个股票代码:\n");
		
		// 在后台线程中添加股票
		new Thread(new Runnable() {
			@Override
			public void run() {
				int successCount = 0;
				int failCount = 0;
				
				for (String code : stockCodes) {
					try {
						StockMng stockMng = new StockMng(mySqlite);
						
						// 查询股票信息
						mClient = SinaStockClient.getInstance();
						List<SinaStockInfo> stockinfo = mClient.getStockInfo(new String[] {code});
						
						if (stockinfo != null && stockinfo.size() > 0) {
							SinaStockInfo stock1 = stockinfo.get(0);
							// 获取当前价格
							float currentPrice = stock1.getNowPrice();
							// 导入股票时，最高价和最低价设置为当前价格的±10%
							// 最高价 = 当前价格 × 1.10 (上涨10%)
							// 最低价 = 当前价格 × 0.90 (下跌10%)
							// 精确到分（保留两位小数）：先乘以100，四舍五入，再除以100
							float highPrice = Math.round(currentPrice * 1.10f * 100.0f) / 100.0f;
							float lowPrice = Math.round(currentPrice * 0.90f * 100.0f) / 100.0f;
							// 默认设置涨幅和跌幅都为5%
							stockMng.Set(code, stock1.getName(), highPrice, lowPrice, 5, 5, true);
							stockMng.Add();
							successCount++;
							resultMessage.append("✓ ").append(code).append(" ").append(stock1.getName()).append("\n");
						} else {
							failCount++;
							resultMessage.append("✗ ").append(code).append(" 查询失败\n");
						}
					} catch (ExceptionAddStock e) {
						// 股票已存在，尝试更新
						try {
							StockMng stockMng = new StockMng(mySqlite);
							stockMng.Get(code);
							stockMng.Enable(true);
							stockMng.Update();
							resultMessage.append("→ ").append(code).append(" 已存在，已激活\n");
						} catch (Exception ex) {
							failCount++;
							resultMessage.append("✗ ").append(code).append(" 已存在但更新失败\n");
						}
					} catch (ParseStockInfoException e) {
						failCount++;
						resultMessage.append("✗ ").append(code).append(" 代码不存在\n");
					} catch (Exception e) {
						failCount++;
						resultMessage.append("✗ ").append(code).append(" 添加失败: ").append(e.getMessage()).append("\n");
						Log.e("AddStock", "添加股票失败: " + code, e);
					}
				}
				
				// 获取所有股票的当前价格
				List<SinaStockInfo> allStockInfo = null;
				try {
					String[] allCodes = m_stockMng.GetCodes();
					if (allCodes.length > 0) {
						allStockInfo = mClient.getStockInfo(allCodes);
					}
				} catch (Exception e) {
					allStockInfo = null;
				}
				
				final List<SinaStockInfo> finalAllStockInfo = allStockInfo;
				final int finalSuccessCount = successCount;
				final int finalFailCount = failCount;
				
				// 在主线程中更新UI
				runOnUiThread(new Runnable() {
					@Override
					public void run() {
						resultMessage.append("\n成功: ").append(finalSuccessCount).append(" 个，失败: ").append(finalFailCount).append(" 个");
						eView.setText(resultMessage.toString());
						// 列标题现在是LinearLayout，不需要设置文本
						
						// 刷新列表
						createListView(m_stockMng.getStocks(), finalAllStockInfo);
						DisableItems(m_stockMng.getStocks());
						
						if (finalSuccessCount > 0) {
							Toast.makeText(AlarmActivity.this, "成功添加 " + finalSuccessCount + " 个股票", Toast.LENGTH_LONG).show();
						}
					}
				});
			}
		}).start();
	}

	@Override
	public void onBackPressed() {
		new AlertDialog.Builder(this).setTitle("退出后将不能收到股价预警，确认退出吗？")
		.setIcon(android.R.drawable.ic_dialog_info)
		.setPositiveButton("确定", new DialogInterface.OnClickListener() {
			@Override
			public void onClick(DialogInterface dialog, int which) {
				// 点击“确认”后的操作
				// 退出时上报设备状态
				DeviceReporter.reportAsync(AlarmActivity.this);
				//this.finish();
				android.os.Process.killProcess(android.os.Process.myPid());
			}
		})
		.setNegativeButton("取消", new DialogInterface.OnClickListener() {
			@Override
			public void onClick(DialogInterface dialog, int which) {
				// 点击“返回”后的操作,这里不设置没有任何操作
			}
		}).show();
// super.onBackPressed();
	}

	public void Notification( String msg) {
		
		/*
		int Notification_ID_BASE = 110;
		Notification baseNF;
		//Notification ID
		int Notification_ID_MEDIA = 119;
		Notification mediaNF;
		mediaNF = new Notification();
		mediaNF.icon = R.drawable.ic_launcher;
		mediaNF.tickerText = "股票预警55";
		//自定义声音
		mediaNF.sound = Uri.withAppendedPath(Audio.Media.INTERNAL_CONTENT_URI, "6");
		//通知时发出的振动
		//第一个参数: 振动前等待的时间
		//第二个参数： 第一次振动的时长、以此类推
		//long[] vir = {0,100,200,300};
		//mediaNF.vibrate = vir;
		PendingIntent pd;
		//Intent intent = new Intent(this,StockNotify.class);
		// pd = PendingIntent.getActivity(this, 0, intent, 0);
		//pd = PendingIntent.getActivity(this, 0, new Intent(this, StockNotify.class), 0);//
		Intent appIntent = new Intent(Intent.ACTION_MAIN);
		//appIntent.setAction(Intent.ACTION_MAIN);
		appIntent.addCategory(Intent.CATEGORY_LAUNCHER);
		appIntent.setComponent(
			new ComponentName(m_activity.getPackageName(), m_activity.getPackageName() + "." + m_activity.getLocalClassName()));
		appIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED |Intent.FLAG_ACTIVITY_CLEAR_TOP);//关键的一步，设置启动模式
		pd =PendingIntent.getActivity(m_activity, 0,appIntent,0);
		mediaNF.setLatestEventInfo(m_activity, "股票预警",msg, pd);
		NotificationManager nm;
		nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
		mediaNF.flags |= mediaNF.FLAG_AUTO_CANCEL;
		mediaNF.defaults |= Notification.DEFAULT_VIBRATE;
		//mediaNF.defaults |= Notification.DEFAULT_LIGHTS;
		nm.notify(Notification_ID_MEDIA, mediaNF);
		
		*/
		/*
		Notification notification = new Notification();   
		NotificationManager notificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);  
		  
		  
		notification.icon = R.drawable.ic_launcher; //你自己的资源图片  
		Context mContext = getApplicationContext();  
		
		
		Intent appIntent = new Intent(Intent.ACTION_MAIN);  
		appIntent.addCategory(Intent.CATEGORY_LAUNCHER);  
		appIntent.setComponent(new ComponentName(this.getPackageName(), this.getPackageName() + "." + this.getLocalClassName()));  
		appIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK| Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);//关键的一步，设置启动模式  
		  
		  
		PendingIntent intent = PendingIntent.getActivity(mContext, 0,  
		appIntent, 0);  
		notification.setLatestEventInfo(getApplicationContext(),  
		getString(R.string.app_name), "-----", intent);  
		  
		  
		notificationManager.notify(0, notification);  
*/
		
		// 预警触发后不停止监控，继续监控其他股票
		// is_notified = true;  // 注释掉，不停止监控
		String app_name = getString(R.string.app_name);


		NotificationManager barmanager=(NotificationManager)getSystemService(Context.NOTIFICATION_SERVICE);
        Intent appIntent = new Intent(Intent.ACTION_MAIN);
     
        appIntent.addCategory(Intent.CATEGORY_LAUNCHER);
        appIntent.setComponent(new ComponentName(this.getPackageName(), this.getPackageName() + "." + this.getLocalClassName())); 
        appIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK| Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);//关键的一步，设置启动模式为打开上一个activity
        PendingIntent contentIntent =PendingIntent.getActivity(this, 0,appIntent,0);
        
        // 铃声设置
        Uri ringtoneUri = SettingsActivity.getRingtoneUri(this);
        if (ringtoneUri == null) {
        	ringtoneUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
        }
        
        // 使用现代的 Notification.Builder API 替代已废弃的 setLatestEventInfo
        Notification.Builder builder;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        	// Android 8.0+ 的声音由通知渠道控制
        	boolean vibrateEnabled = SettingsActivity.getVibrateEnabled(this);
        	String channelSuffix = (ringtoneUri != null) ? String.valueOf(ringtoneUri.toString().hashCode()) : "default";
        	String channelId = "stock_alarm_channel_" + channelSuffix + "_" + (vibrateEnabled ? "v1" : "v0");
        	NotificationChannel channel = barmanager.getNotificationChannel(channelId);
        	if (channel == null) {
        		channel = new NotificationChannel(channelId, "股票预警", NotificationManager.IMPORTANCE_HIGH);
        	}
        	channel.enableVibration(vibrateEnabled);
        	if (ringtoneUri != null) {
        		AudioAttributes attributes = new AudioAttributes.Builder()
        				.setUsage(AudioAttributes.USAGE_NOTIFICATION)
        				.setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
        				.build();
        		channel.setSound(ringtoneUri, attributes);
        	}
        	barmanager.createNotificationChannel(channel);
        	
        	builder = new Notification.Builder(this, channelId);
        } else {
        	builder = new Notification.Builder(this);
        }
        
        builder.setSmallIcon(R.drawable.ic_launcher)
        		.setContentTitle(app_name)
        		.setContentText(msg)
        		.setContentIntent(contentIntent)
        		.setAutoCancel(true);
        
        // 根据设置应用振动和铃声
        int defaults = 0;
        
        // 振动设置
        if (SettingsActivity.getVibrateEnabled(this)) {
        	defaults |= Notification.DEFAULT_VIBRATE;
        }
        
        // Android 8.0 以下声音由通知对象控制；8.0+ 已在渠道里设置
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
        	if (ringtoneUri != null) {
        		builder.setSound(ringtoneUri);
        	} else {
        		defaults |= Notification.DEFAULT_SOUND;
        	}
        }
        
        builder.setDefaults(defaults);
        Notification notice = builder.build();
        
        barmanager.notify(0,notice);
		
	}
	
	/**
	 * 滚动到触发预警的股票位置并高亮显示
	 * @param listview ListView控件
	 * @param stockIndex 股票在列表中的索引位置
	 * @param stockCode 股票代码（用于验证）
	 */
	public void scrollToAlarmStock(ListView listview, int stockIndex, String stockCode) {
		if (listview == null || stockIndex < 0) return;
		// 报警时列表可能已排序/过滤，stockIndex 可能不再对应当前适配器位置。
		// 优先用股票代码在当前适配器里定位真实位置，找不到再退回 stockIndex。
		int pos = findAdapterPositionByCode(listview, stockCode);
		if (pos < 0) pos = stockIndex;

		// 预警触发时，确保顶部编辑区可见（否则用户看不到已更新的设置）
		showHeaderControls();
		scheduleAutoHideHeader();

		selectStockAtPosition(listview, pos, stockCode, true, false);
	}

	private int findAdapterPositionByCode(ListView listview, String stockCode) {
		if (listview == null || stockCode == null || stockCode.length() == 0) return -1;
		try {
			android.widget.ListAdapter adapter = listview.getAdapter();
			if (adapter == null) return -1;
			for (int i = 0; i < adapter.getCount(); i++) {
				Object item = adapter.getItem(i);
				if (item instanceof java.util.Map) {
					@SuppressWarnings("unchecked")
					java.util.Map<String, Object> map = (java.util.Map<String, Object>) item;
					Object c = map.get("code");
					if (c != null && stockCode.equals(String.valueOf(c))) {
						return i;
					}
				}
			}
		} catch (Exception ignore) {
		}
		return -1;
	}

	/**
	 * 选中某一行股票：滚动定位、突出显示，并更新上方编辑框的预警设置。
	 * @param listview 列表
	 * @param position 行号
	 * @param code 股票代码（用于更新编辑框）
	 * @param smoothScroll 是否使用平滑滚动
	 * @param forceUpdate 是否强制更新编辑框（用户主动点击时为true，预警自动选中时为false）
	 */
	private void selectStockAtPosition(final ListView listview, final int position, final String code, final boolean smoothScroll, final boolean forceUpdate) {
		runOnUiThread(new Runnable() {
			@Override
			public void run() {
				try {
					highlightedPosition = position;
					highlightedCode = code;
					android.widget.ListAdapter adapter = listview.getAdapter();
					if (adapter instanceof android.widget.BaseAdapter) {
						((android.widget.BaseAdapter) adapter).notifyDataSetChanged();
					}

					// 定位到对应行
					if (smoothScroll) {
						listview.smoothScrollToPosition(position);
					} else {
						listview.setSelection(position);
					}

					// 更新上方编辑框为该股票设置
					// 若用户正在编辑输入框，则不要被自动更新打断（除非是用户主动点击列表项）
					boolean shouldUpdate = code != null && code.length() > 0;
					if (shouldUpdate && !forceUpdate) {
						shouldUpdate = !isUserEditingHeaderInputs();
					}
					if (shouldUpdate) {
						updateEditorByStockCode(code);
					}
				} catch (Exception e) {
					Log.e("selectStockAtPosition", "选中股票失败: " + e.getMessage(), e);
				}
			}
		});
	}

	/**
	 * 根据股票代码更新上方输入框（高/低价、涨/跌幅、按钮文案）。
	 */
	private void updateEditorByStockCode(String code) {
		try {
			final EditText e_code = (EditText) findViewById(R.id.edit_stock_code);
			final EditText e_price_h = (EditText) findViewById(R.id.edit_price_h);
			final EditText e_price_l = (EditText) findViewById(R.id.edit_price_l);
			final EditText e_rise = (EditText) findViewById(R.id.edit_rise);
			final EditText e_drop = (EditText) findViewById(R.id.edit_drop);
			final Button btn_Add = (Button) findViewById(R.id.btn_submit);

			if (e_code != null) {
				e_code.setText(code);
			}

			StockMng stockMng = new StockMng(mySqlite);
			stockMng.Get(code);

			if (e_price_h != null) e_price_h.setText(String.format("%.2f", stockMng.GetHigh()));
			if (e_price_l != null) e_price_l.setText(String.format("%.2f", stockMng.GetLow()));
			if (e_rise != null) e_rise.setText("" + stockMng.GetRise());
			if (e_drop != null) e_drop.setText("" + stockMng.GetDrop());
			if (btn_Add != null) btn_Add.setText("更新预警");
		} catch (Exception e) {
			// 数据不存在或读取失败时不强行覆盖用户正在输入的内容
			Log.w("updateEditorByStockCode", "update failed: " + e.getMessage());
		}
	}

	/**
	 * 用户正在编辑顶部输入框时，禁止自动更新这些输入框内容，避免打断用户输入。
	 */
	private boolean isUserEditingHeaderInputs() {
		try {
			EditText e_code = (EditText) findViewById(R.id.edit_stock_code);
			EditText e_price_h = (EditText) findViewById(R.id.edit_price_h);
			EditText e_price_l = (EditText) findViewById(R.id.edit_price_l);
			EditText e_rise = (EditText) findViewById(R.id.edit_rise);
			EditText e_drop = (EditText) findViewById(R.id.edit_drop);
			return (e_code != null && e_code.hasFocus())
					|| (e_price_h != null && e_price_h.hasFocus())
					|| (e_price_l != null && e_price_l.hasFocus())
					|| (e_rise != null && e_rise.hasFocus())
					|| (e_drop != null && e_drop.hasFocus());
		} catch (Exception ignore) {
			return false;
		}
	}

	void DisableItems(List<StockMng> list_stock) {
		ListView list = (ListView) findViewById(R.id.ListView01);

		if( list.getChildCount() == 0) {
			return;
		}

		//final EditText e_info = (EditText) findViewById(R.id.edit_stock_info) ;
		int first = list.getFirstVisiblePosition();
		for(int i=0; i<list.getChildCount(); i++) {
			int dataPos = first + i;
			if (dataPos < 0 || dataPos >= list_stock.size()) continue;
			StockMng stock = list_stock.get(dataPos);
			View itemView = list.getChildAt(i);
			if (itemView == null) continue;

			// 单行布局使用LinearLayout，背景色设置为白色或透明
			boolean isHighlighted = false;
			try {
				if (highlightedCode != null && highlightedCode.length() > 0) {
					isHighlighted = highlightedCode.equals(stock.GetCode());
				} else {
					isHighlighted = (dataPos == highlightedPosition);
				}
			} catch (Exception ignore) {
			}
			if (isHighlighted) {
				// 高亮行：彩色模式淡黄色；黑白模式用浅灰（保持黑白）
				boolean colorful = true;
				try { colorful = SettingsActivity.getColorfulListEnabled(itemView.getContext()); } catch (Exception ignore) {}
				itemView.setBackgroundColor(Color.parseColor(colorful ? "#FFF4CC" : "#F0F0F0"));
				itemView.setAlpha(1.0f);
			} else if(!stock.IsValid()) {
				// 禁用状态：使用透明度区分
				itemView.setBackgroundColor(Color.parseColor("#FFFFFF"));
				itemView.setAlpha(0.6f);
			} else {
				// 激活状态：使用白色背景
				itemView.setBackgroundColor(Color.parseColor("#FFFFFF"));
				itemView.setAlpha(1.0f);
			}
		}
	}
	/**
	 * 刷新所有有效股票的均线数据（在后台线程中执行）。
	 */
	private void refreshMaDataForAllStocks() {
		final String[] codes = m_stockMng.GetCodes();
		if (codes == null || codes.length == 0) return;
		
		new Thread(new Runnable() {
			@Override
			public void run() {
				try {
					for (String code : codes) {
						if (code == null || code.isEmpty()) continue;
						float[] mas = m_maDownloader.downloadAndCalculateMAs(code);
						if (mas != null) {
							m_maMng.saveMaPrices(code, mas);
						}
						// 避免请求过快
						try { Thread.sleep(500); } catch (InterruptedException ie) {}
					}
					android.util.Log.i("refreshMaData", "均线数据刷新完成");
				} catch (Exception e) {
					android.util.Log.e("refreshMaData", "刷新均线数据失败: " + e.getMessage(), e);
				}
			}
		}).start();
	}

	void createListView(List<StockMng> list_stock) {
		createListView(list_stock, null);
	}
	
	void createListView(List<StockMng> list_stock, List<SinaStockInfo> stockInfos) {
		//绑定Layout里面的ListView
		ListView list = (ListView) findViewById(R.id.ListView01);
		//生成动态数组，加入数据
		ArrayList<HashMap<String, Object>> listItem = new ArrayList<HashMap<String, Object>>();
		if( list_stock.size() == 0)//add 2013.5.21
		{
			return ;
		}

		// 创建股票代码到价格信息的映射
		java.util.HashMap<String, SinaStockInfo> priceMap = new java.util.HashMap<String, SinaStockInfo>();
		if (stockInfos != null) {
			for (SinaStockInfo info : stockInfos) {
				// 通过股票名称匹配（因为SinaStockInfo中没有代码信息）
				priceMap.put(info.getName(), info);
			}
		}

		for(int i=0; i<list_stock.size(); i++) {
			StockMng stock = list_stock.get(i);
			HashMap<String, Object> map = new HashMap<String, Object>();
			//map.put("ItemImage", R.drawable.ic_launcher);//图像资源的ID
			map.put("code", stock.GetCode());
			map.put("name", stock.GetName());
			// 列表“涨幅/跌幅”显示的是你设置的预警阈值，而不是实时涨跌幅
			map.put("rise", String.valueOf(stock.GetRise()));
			map.put("drop", String.valueOf(stock.GetDrop()));
			// 当前涨幅（相对昨收），默认 0.0（保留 1 位小数）
			map.put("chg", "0.0");
			
			// 如果有价格信息，使用实际价格；否则使用0.0
			SinaStockInfo priceInfo = priceMap.get(stock.GetName());
			if (priceInfo != null) {
				// 价格精确到分（保留两位小数）
				map.put("price", String.format("%.2f", priceInfo.getNowPrice()));
				// “高价/低价”显示的是你预设的预警阈值（高于/低于），不是实时最高/最低价
				map.put("high", String.format("%.2f", stock.GetHigh()));
				map.put("low", String.format("%.2f", stock.GetLow()));
				// 计算当前涨幅（相对昨收），保留 1 位小数
				float yesterdayPrice = priceInfo.getYestodayPrice();
				float nowPrice = priceInfo.getNowPrice();
				if (yesterdayPrice > 0) {
					float changePercent = ((nowPrice - yesterdayPrice) / yesterdayPrice) * 100;
					map.put("chg", String.format("%.1f", changePercent));
				}
			} else {
				map.put("price", "0.0");
				// 价格精确到分（保留两位小数）
				map.put("high", String.format("%.2f", stock.GetHigh()));
				map.put("low", String.format("%.2f", stock.GetLow()));
			}
			listItem.add(map);
		}

		// 应用排序（如开启）
		applySortIfNeeded(listItem);
		// 排序后重新计算高亮 position，避免高亮错位
		realignHighlightAfterSort(listItem);

		//*
		//生成适配器的Item和动态数组对应的元素
		final List<StockMng> finalStocks = list_stock;
		// 让行情刷新可以拿到并排序同一份数据源
		list.setTag(listItem);
		SimpleAdapter listItemAdapter = new SimpleAdapter(
			this,
			listItem,//数据源
			R.layout.list_items,//ListItem的XML实现
			//动态数组与ImageItem对应的子项
			new String[] {"name", "price", "chg", "high","low","rise", "drop"},
			//ImageItem的XML文件里面的一个ImageView,两个TextView ID
			new int[] {R.id.list_name, R.id.list_price_now,
					   R.id.list_change_now, R.id.list_high, R.id.list_low, R.id.list_rise, R.id.list_drop
					  }
		) {
			@Override
			public View getView(int position, View convertView, android.view.ViewGroup parent) {
				View v = super.getView(position, convertView, parent);
				boolean colorful = true;
				try { colorful = SettingsActivity.getColorfulListEnabled(v.getContext()); } catch (Exception ignore) {}
				// 颜色模式：彩色=涨红跌绿；黑白=全部黑色
				try {
					TextView tvPrice = (TextView) v.findViewById(R.id.list_price_now);
					TextView tvChg = (TextView) v.findViewById(R.id.list_change_now);

					int baseColor = Color.parseColor("#000000");
					if (!colorful) {
						if (tvPrice != null) tvPrice.setTextColor(baseColor);
						if (tvChg != null) tvChg.setTextColor(baseColor);
					} else {
						float chg = 0f;
						try {
							@SuppressWarnings("unchecked")
							Map<String, Object> item = (Map<String, Object>) getItem(position);
							if (item != null) {
								Object chgObj = item.get("chg");
								if (chgObj != null) {
									chg = Float.parseFloat(String.valueOf(chgObj));
								}
							}
						} catch (Exception ignore) {
						}
						int color;
						if (chg > 0f) {
							color = Color.parseColor("#D0021B"); // 红
						} else if (chg < 0f) {
							color = Color.parseColor("#1AA260"); // 绿
						} else {
							color = baseColor;
						}
						if (tvPrice != null) tvPrice.setTextColor(color);
						if (tvChg != null) tvChg.setTextColor(color);
					}
				} catch (Exception ignore) {
				}
				try {
					boolean isHighlighted = (position == highlightedPosition);
					try {
						if (highlightedCode != null) {
							@SuppressWarnings("unchecked")
							Map<String, Object> item = (Map<String, Object>) getItem(position);
							if (item != null) {
								Object c = item.get("code");
								if (c != null) {
									isHighlighted = highlightedCode.equals(String.valueOf(c));
								}
							}
						}
					} catch (Exception ignore) {
					}
					boolean isValid = true;
					if (finalStocks != null && position >= 0 && position < finalStocks.size()) {
						isValid = finalStocks.get(position).IsValid();
					}

					if (isHighlighted) {
						// 高亮行：彩色模式淡黄色；黑白模式用浅灰（保持黑白）
						v.setBackgroundColor(Color.parseColor(colorful ? "#FFF4CC" : "#F0F0F0"));
						v.setAlpha(1.0f);
					} else if (!isValid) {
						v.setBackgroundColor(Color.parseColor("#FFFFFF"));
						v.setAlpha(0.6f);
					} else {
						v.setBackgroundColor(Color.parseColor("#FFFFFF"));
						v.setAlpha(1.0f);
					}
				} catch (Exception ignore) {
				}
				return v;
			}
		};
		//添加并且显示
		list.setAdapter(listItemAdapter);
		//list.getItemAtPosition(0).ForceColor = Color.RED;
		// list.Items[0].ForeColor = Color.RED;
		// list.Items[0].UseItemStyleForSubItems = false;
		// list.selectedItems.BackColor = Color.RED;
		//*/
		//添加点击
		list.setOnItemClickListener(new OnItemClickListener() {
			@Override
			public void onItemClick(AdapterView<?> arg0, View arg1, int arg2,
			long arg3) {
				// 用户点击某行股票：显示顶部按钮/输入区
				showHeaderControls();
				scheduleAutoHideHeader();
				Map <String,Object> map = extracted(arg2);
				String code = map.get("code").toString();
				ListView lv = (arg0 instanceof ListView) ? (ListView) arg0 : (ListView) findViewById(R.id.ListView01);
				selectStockAtPosition(lv, arg2, code, false, true);
			}
		});
		//list.getItemAtPosition(0).
		//添加长按点击
		list.setOnCreateContextMenuListener(new OnCreateContextMenuListener() {
			@Override
			public void onCreateContextMenu(ContextMenu menu, View v,ContextMenuInfo menuInfo) {
				menu.setHeaderTitle("股票更改");
				//ListView list = (ListView) findViewById(R.id.ListView01);
				menu.add(1, 0, 4, "删除这支股票预警");

				try {
					AdapterView.AdapterContextMenuInfo info = (AdapterView.AdapterContextMenuInfo)menuInfo;
					int stock_id = (int)info.id;//这里的info.id对应的就是数据库中_id的值
					Map <String,Object> map = extracted(stock_id);
					String code = map.get("code").toString();
					StockMng stockMng = new StockMng(mySqlite);
					stockMng.Get(code);

					if( stockMng.IsValid()) {
						menu.add(2, 1, 5, "暂停这支股票预警");
					}
					else {
						menu.add(2, 1, 5, "激活这支股票预警");
					}
				}
				catch (Exception e) {
				}
			}
		});
	}

	//长按菜单响应函数
	@Override
	public boolean onContextItemSelected(MenuItem item) {
		AdapterView.AdapterContextMenuInfo info = (AdapterView.AdapterContextMenuInfo)item.getMenuInfo();
		int stock_id = (int)info.id;//这里的info.id对应的就是数据库中_id的值
		//info.toString();
		Map <String,Object> map = extracted(stock_id);
		String code = map.get("code").toString();
		StockMng stockMng = new StockMng(mySqlite);

		switch( item.getItemId()) {
			case 0:
				stockMng.Delete(code);
				createListView(m_stockMng.getStocks());
				DisableItems(m_stockMng.getStocks());
				break;

				//
			case 1:
				try {
					stockMng.Get(code);

					if( stockMng.IsValid()) {
						stockMng.Enable(false);
					}
					else {
						stockMng.Enable(true);
					}

					stockMng.Update();
					DisableItems(m_stockMng.getStocks());
				}
				catch(Exception e) {
					setTitle(getTitle() + "diable + " + e.toString());
				}

				break;
		}

		return super.onContextItemSelected(item);
	}


	
		public void ReflashStockPrice(ListView lv, List<SinaStockInfo> stockinfos) {
		// TODO Auto-generated method stub
		boolean has_fetched_price = true;  // 标记已成功获取过价格
		if( null == lv || null == stockinfos )
		{
			Log.e("reflash", "ReflashStockPrice para err");
			return ;
		}
		
		// 获取适配器
		android.widget.SimpleAdapter adapter = (android.widget.SimpleAdapter)lv.getAdapter();
		if (adapter == null) {
			Log.e("reflash", "Adapter is null");
			return;
		}
		
		// 创建查询代码到价格信息的映射（用代码匹配，避免除权改名后匹配不上）
		java.util.HashMap<String, SinaStockInfo> priceMap = new java.util.HashMap<String, SinaStockInfo>();
		for (SinaStockInfo info : stockinfos) {
			String queryCode = info.getCode();
			if (queryCode != null) {
				priceMap.put(queryCode, info);
			}
			// 也保留名称匹配作为兜底
			priceMap.put(info.getName(), info);
		}
		
		// 更新适配器数据源
		boolean dataChanged = false;
		for (int i = 0; i < adapter.getCount(); i++) {
			@SuppressWarnings("unchecked")
			HashMap<String, Object> item = (HashMap<String, Object>)adapter.getItem(i);
			if (item != null) {
				String stockCode = (String)item.get("code");
				String stockName = (String)item.get("name");
				// 优先用代码匹配，兜底用名称匹配
				SinaStockInfo priceInfo = (stockCode != null) ? priceMap.get(stockCode) : null;
				if (priceInfo == null && stockName != null) {
					priceInfo = priceMap.get(stockName);
				}
				if (priceInfo != null) {
					// 更新价格
					String newPrice = String.format("%.2f", priceInfo.getNowPrice());
					String oldPrice = (String)item.get("price");
					if (!newPrice.equals(oldPrice)) {
						item.put("price", newPrice);
						// "高价/低价"为预警阈值，不随行情刷新覆盖
						// 更新当前涨幅（相对昨收），保留 1 位小数
						float yesterdayPrice = priceInfo.getYestodayPrice();
						float nowPrice = priceInfo.getNowPrice();
						if (yesterdayPrice > 0) {
							float changePercent = ((nowPrice - yesterdayPrice) / yesterdayPrice) * 100;
							item.put("chg", String.format("%.1f", changePercent));
						} else {
							item.put("chg", "0.0");
						}
						dataChanged = true;
					}
				}
			}
		}
		
		// 如果数据有变化，通知适配器刷新（并在需要时重新排序）
		if (dataChanged) {
			// 如果启用了排序，先重新排序
			@SuppressWarnings("unchecked")
			List<HashMap<String, Object>> listData = (List<HashMap<String, Object>>) lv.getTag();
			if (listData != null) {
				applySortIfNeeded(listData);
				realignHighlightAfterSort(listData);
			}
			lv.post(new Runnable() {
				@Override
				public void run() {
					// 适配器会从Tag中获取数据源，所以只需要告诉适配器数据变了
					try {
						if (lv.getAdapter() instanceof android.widget.BaseAdapter) {
							((android.widget.BaseAdapter) lv.getAdapter()).notifyDataSetChanged();
						}
					} catch (Exception e) {
						Log.e("reflash", "notifyDataSetChanged error: " + e.getMessage(), e);
					}
				}
			});
		}
	}	

	
	private HashMap<String, Object> extracted(int position) {
		ListView list=(ListView)findViewById(R.id.ListView01);
		return (HashMap<String,Object>)(list.getAdapter().getItem(position));
	}

	private void bindSortHeaderClicks() {
		try {
			final TextView hName = (TextView) findViewById(R.id.header_sort_name);
			final TextView hPrice = (TextView) findViewById(R.id.header_sort_price);
			final TextView hChg = (TextView) findViewById(R.id.header_sort_chg);
			if (hName != null) lastHeaderNameBaseText = String.valueOf(hName.getText());
			if (hPrice != null) lastHeaderPriceBaseText = String.valueOf(hPrice.getText());
			if (hChg != null) lastHeaderChgBaseText = String.valueOf(hChg.getText());

			// Set click listener on both the label TextView and its parent LinearLayout
			// so clicking anywhere in the column area triggers sort
			View.OnClickListener nameClick = new View.OnClickListener() {
				@Override public void onClick(View v) { onSortHeaderClicked(SortColumn.NAME); }
			};
			View.OnClickListener priceClick = new View.OnClickListener() {
				@Override public void onClick(View v) { onSortHeaderClicked(SortColumn.PRICE); }
			};
			View.OnClickListener chgClick = new View.OnClickListener() {
				@Override public void onClick(View v) { onSortHeaderClicked(SortColumn.CHG); }
			};
			
			if (hName != null) {
				hName.setOnClickListener(nameClick);
				View parent = (View) hName.getParent();
				if (parent != null) parent.setOnClickListener(nameClick);
			}
			if (hPrice != null) {
				hPrice.setOnClickListener(priceClick);
				View parent = (View) hPrice.getParent();
				if (parent != null) parent.setOnClickListener(priceClick);
			}
			if (hChg != null) {
				hChg.setOnClickListener(chgClick);
				View parent = (View) hChg.getParent();
				if (parent != null) parent.setOnClickListener(chgClick);
			}
			updateSortHeaderUI();
		} catch (Exception e) {
			Log.w("bindSortHeaderClicks", "bind failed: " + e.getMessage());
		}
	}

	private void onSortHeaderClicked(SortColumn clicked) {
		// 切换逻辑：同一列 -> DESC -> ASC -> NONE；切到新列 -> DESC
		if (clicked == null) clicked = SortColumn.NONE;
		if (clicked != sortColumn) {
			sortColumn = clicked;
			sortOrder = SortOrder.DESC;
		} else {
			if (sortOrder == SortOrder.DESC) sortOrder = SortOrder.ASC;
			else if (sortOrder == SortOrder.ASC) {
				sortOrder = SortOrder.NONE;
				sortColumn = SortColumn.NONE;
			} else {
				sortOrder = SortOrder.DESC;
			}
		}

		ListView list = (ListView) findViewById(R.id.ListView01);
		if (list != null) {
			@SuppressWarnings("unchecked")
			List<HashMap<String, Object>> data = (List<HashMap<String, Object>>) list.getTag();
			if (data != null) {
				applySortIfNeeded(data);
				realignHighlightAfterSort(data);
				if (list.getAdapter() instanceof android.widget.BaseAdapter) {
					((android.widget.BaseAdapter) list.getAdapter()).notifyDataSetChanged();
				}
			}
		}
		updateSortHeaderUI();
	}

	private void updateSortHeaderUI() {
		try {
			TextView hName = (TextView) findViewById(R.id.header_sort_name);
			TextView hPrice = (TextView) findViewById(R.id.header_sort_price);
			TextView hChg = (TextView) findViewById(R.id.header_sort_chg);
			if (hName != null) hName.setText(buildHeaderText(lastHeaderNameBaseText, SortColumn.NAME));
			if (hPrice != null) hPrice.setText(buildHeaderText(lastHeaderPriceBaseText, SortColumn.PRICE));
			if (hChg != null) hChg.setText(buildHeaderText(lastHeaderChgBaseText, SortColumn.CHG));
		} catch (Exception ignore) {
		}
	}

	private String buildHeaderText(String base, SortColumn col) {
		if (base == null) base = "";
		if (col != sortColumn || sortOrder == SortOrder.NONE) return base;
		if (sortOrder == SortOrder.DESC) return base + " ↓";
		if (sortOrder == SortOrder.ASC) return base + " ↑";
		return base;
	}

	private void applySortIfNeeded(List<HashMap<String, Object>> data) {
		if (data == null || data.size() <= 1) return;
		if (sortColumn == SortColumn.NONE || sortOrder == SortOrder.NONE) return;
		final int dir = (sortOrder == SortOrder.ASC) ? 1 : -1;

		Comparator<HashMap<String, Object>> cmp = new Comparator<HashMap<String, Object>>() {
			@Override
			public int compare(HashMap<String, Object> a, HashMap<String, Object> b) {
				if (a == null && b == null) return 0;
				if (a == null) return 1;
				if (b == null) return -1;
				try {
					switch (sortColumn) {
						case NAME:
							String an = String.valueOf(a.get("name"));
							String bn = String.valueOf(b.get("name"));
							return dir * an.compareToIgnoreCase(bn);
						case PRICE:
							return dir * Float.compare(safeFloat(a.get("price")), safeFloat(b.get("price")));
						case CHG:
							return dir * Float.compare(safeFloat(a.get("chg")), safeFloat(b.get("chg")));
						default:
							return 0;
					}
				} catch (Exception ignore) {
					return 0;
				}
			}
		};

		try {
			Collections.sort(data, cmp);
		} catch (Exception e) {
			Log.w("applySortIfNeeded", "sort failed: " + e.getMessage());
		}
	}

	private float safeFloat(Object v) {
		if (v == null) return 0f;
		try {
			String s = String.valueOf(v).trim();
			if (s.length() == 0) return 0f;
			return Float.parseFloat(s);
		} catch (Exception ignore) {
			return 0f;
		}
	}

	private void realignHighlightAfterSort(List<HashMap<String, Object>> data) {
		if (highlightedCode == null || highlightedCode.length() == 0) return;
		if (data == null) return;
		for (int i = 0; i < data.size(); i++) {
			HashMap<String, Object> m = data.get(i);
			if (m == null) continue;
			Object c = m.get("code");
			if (c != null && highlightedCode.equals(String.valueOf(c))) {
				highlightedPosition = i;
				return;
			}
		}
	}
	
}


