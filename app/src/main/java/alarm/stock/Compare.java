package alarm.stock;

import java.util.List;
import java.util.HashMap;
import java.util.Map;

import android.os.AsyncTask;
import android.util.Log;
import android.widget.ListView;
import android.widget.TextView;
import static alarm.stock.Constants.*;

// 预警信息类，包含消息和触发预警的股票索引
class AlarmInfo {
	String message;
	int stockIndex;
	String stockCode;
	
	AlarmInfo(String message, int stockIndex, String stockCode) {
		this.message = message;
		this.stockIndex = stockIndex;
		this.stockCode = stockCode;
	}
}

// 冲高回落/探底回升追踪：记录每支股票在当天观测到的最高/最低价
class PeakValleyTracker {
    // key: 日期+股票代码, value: 当日观测最高价/最低价
    private static final Map<String, float[]> dailyObserved = new HashMap<String, float[]>();
    // 去重：已经报过冲高回落的，当天不再重复报
    private static final Map<String, Boolean> alertedPeak = new HashMap<String, Boolean>();
    private static final Map<String, Boolean> alertedValley = new HashMap<String, Boolean>();
    
    // 回落/反弹触发阈值（元），可根据股价动态调整
    private static final float MIN_RETRACE_AMOUNT = 0.01f;  // 1分钱
    
    public static void resetForNewDay() {
        String today = getDateStr();
        // 清理前一天的数据，保留当天的
        java.util.Iterator<String> it = dailyObserved.keySet().iterator();
        while (it.hasNext()) {
            if (!it.next().startsWith(today)) {
                it.remove();
            }
        }
        // 这里有个问题：上面的迭代器 remove 会让值也一起被删（因为 keySet() backed by map）
        // 重置 dailyObserved 后再清标记
        java.util.Iterator<String> pit = alertedPeak.keySet().iterator();
        while (pit.hasNext()) {
            if (!pit.next().startsWith(today)) {
                pit.remove();
            }
        }
        java.util.Iterator<String> vit = alertedValley.keySet().iterator();
        while (vit.hasNext()) {
            if (!vit.next().startsWith(today)) {
                vit.remove();
            }
        }
    }
    
    private static String getDateStr() {
        return new java.text.SimpleDateFormat("yyyyMMdd").format(new java.util.Date());
    }
    
    private static String makeKey(String code) {
        return getDateStr() + ":" + code;
    }
    
    /**
     * 检查冲高回落。
     * @param code 股票代码
     * @param nowPrice 当前价
     * @return 预警消息，无触发返回 null
     */
    public static String checkPeakFallback(String code, float nowPrice) {
        String key = makeKey(code);
        String alertKey = "peak:" + key;
        float[] obs = dailyObserved.get(key);
        
        if (obs == null) {
            // 第一次观测，记录当前价作为基准
            obs = new float[]{nowPrice, nowPrice};
            dailyObserved.put(key, obs);
            return null;
        }
        
        float observedHigh = obs[0];
        float observedLow = obs[1];
        
        // 创新高：更新观测最高价，下次再比较
        if (nowPrice > observedHigh) {
            obs[0] = nowPrice;
            // 创新高后重置冲高回落预警标记，允许后续回落时再报
            alertedPeak.remove(alertKey);
            return null;
        }
        
        // 创新低：更新观测最低价
        if (nowPrice < observedLow) {
            obs[1] = nowPrice;
            alertedValley.remove("valley:" + key);
            return null;
        }
        
        // --- 冲高回落检测 ---
        // 条件：曾经有过高点(observedHigh)，当前价从高点回落了至少 MIN_RETRACE_AMOUNT，
        // 且当前价没有突破高点，且当天还没报过
        if (!alertedPeak.containsKey(alertKey) && nowPrice < observedHigh - MIN_RETRACE_AMOUNT) {
            float dropPercent = ((observedHigh - nowPrice) / observedHigh) * 100;
            // 回落幅度至少0.05%才报，避免微小波动误报
            if (dropPercent >= 0.05f) {
                alertedPeak.put(alertKey, true);
                return String.format("冲高回落 %.1f%%(高%.2f现%.2f)", dropPercent, observedHigh, nowPrice);
            }
        }
        
        return null;
    }
    
    /**
     * 检查探底回升。
     * @param code 股票代码
     * @param nowPrice 当前价
     * @return 预警消息，无触发返回 null
     */
    public static String checkValleyBounce(String code, float nowPrice) {
        String key = makeKey(code);
        String alertKey = "valley:" + key;
        float[] obs = dailyObserved.get(key);
        
        if (obs == null) {
            return null;
        }
        
        float observedHigh = obs[0];
        float observedLow = obs[1];
        
        // 已经创新高/低的情况在 checkPeakFallback 里处理了
        // 这里只需检测从低点反弹
        if (!alertedValley.containsKey(alertKey) && nowPrice > observedLow + MIN_RETRACE_AMOUNT) {
            float risePercent = ((nowPrice - observedLow) / observedLow) * 100;
            if (risePercent >= 0.05f) {
                alertedValley.put(alertKey, true);
                return String.format("探底回升 %.1f%%(低%.2f现%.2f)", risePercent, observedLow, nowPrice);
            }
        }
        
        return null;
    }
}


/*
 1. AsyncTask的三个泛型参数说明（三个参数可以是任何类型） 
 2.    第一个参数：传入doInBackground()方法的参数类型 
 3.    第二个参数：传入onProgressUpdate()方法的参数类型 
 4.    第三个参数：传入onPostExecute()方法的参数类型，也是doInBackground()方法返回的类型。

 */
class Compare extends AsyncTask<StockMng, TextView, List<SinaStockInfo>>{
   
    TextView te=null;
    ListView listview = null;
    String[] codes = null;
    StockMng stockMng = null;
    AlarmActivity act = null;
    MaPriceMng maMng = null;
    MaAlarmLogMng maAlarmLogMng = null;
    
    public Compare(AlarmActivity act,TextView te, ListView listview, MaPriceMng maMng, MaAlarmLogMng maAlarmLogMng) {
        super();
        this.te = te;
        this.listview = listview;
        this.act = act;
        this.maMng = maMng;
        this.maAlarmLogMng = maAlarmLogMng;
    }

   @Override
    protected List<SinaStockInfo> doInBackground(StockMng... params)  {
	   try {
		   System.out.println("do enter");
		   this.stockMng = params[0];
		   if(null == this.stockMng )
		   {
		   		Log.e("cmp", "compare para err\n");
		   		return null;
		   }
		   this.codes = stockMng.GetCodes();
		   SinaStockClient mClient;
		   mClient = SinaStockClient.getInstance();
		   List<SinaStockInfo>  stockinfo  = mClient.getStockInfo(codes );
		   System.out.println("do return ok");
		   return stockinfo;
	   }
		catch (Exception e) {
			 System.out.println("do return failed");
			return null;
		}
    }
   
   //finished
   @Override
    protected void onPostExecute(List<SinaStockInfo> result){
		try{
		    super.onPostExecute(result);
	        if( null == result )
	        {
	        	AlarmActivity.is_http_timeout = true;
	        	return;
	        }
	        
	        AlarmActivity.is_http_timeout = false;
	        act.ReflashStockPrice(listview, result);
	        AlarmInfo alarmInfo = compareAllStocks(stockMng, result, maMng, maAlarmLogMng);
			if( alarmInfo != null && alarmInfo.message.length() > 0 )
			{
				te.append("\n"+alarmInfo.message);
				act.Notification(alarmInfo.message);
				// 定位到触发预警的股票并高亮显示
				act.scrollToAlarmStock(listview, alarmInfo.stockIndex, alarmInfo.stockCode);
			}
     	}catch (Exception e) {
     		Log.e("onPostExecute", "error: " + e.getMessage(), e);
     	}
    }

   @Override
    protected void onPreExecute() {
        super.onPreExecute();
    }

   @Override
    protected void onProgressUpdate(TextView... values) {
    }
   

	/**
	 * 均线名称数组，与 MaPriceMng.MA_PERIODS 对应。
	 */
	private static final String[] MA_NAMES = {"MA5", "MA10", "MA20", "MA30", "MA60", "MA120", "MA250"};

	public static AlarmInfo compareAllStocks(StockMng stockMng, List<SinaStockInfo>  stockinfo, MaPriceMng maMng, MaAlarmLogMng maAlarmLogMng ) {
		try{
				
			List<StockMng> listStocks = stockMng.getStocks();
			int i=0,j =0;

			// 每天首次检测时重置冲高回落追踪（防止跨日数据残留）
			{
				String todayStr = new java.text.SimpleDateFormat("yyyyMMdd").format(new java.util.Date());
				PeakValleyTracker.resetForNewDay();
			}

			for( i = 0; i< listStocks.size(); i++) {
				StockMng stock = listStocks.get(i);
				if( !stock.IsValid())
				{
					continue;
				}
				if( j>= stockinfo.size() )
				{
					break;
				}
				SinaStockInfo stock1 = stockinfo.get(j++);
				int ret = stock.Compare(stock1.getNowPrice(), stock1.getYestodayPrice());

				if( CMP_RESULT_NONE != ret ) {
					return new AlarmInfo(stock1.getName()+"-"+stock.GetCmpareResult(), i, stock.GetCode());
				}

				// 均线预警检查
				if (maMng != null) {
					String maAlarm = checkMaAlarm(stock.GetCode(), stock1.getNowPrice(), stock1.getYestodayPrice(), maMng, maAlarmLogMng);
					if (maAlarm != null) {
						return new AlarmInfo(stock1.getName() + "-" + maAlarm, i, stock.GetCode());
					}
				}

				// 冲高回落 / 探底回升检测
				String peakAlarm = PeakValleyTracker.checkPeakFallback(stock.GetCode(), stock1.getNowPrice());
				if (peakAlarm != null) {
					return new AlarmInfo(stock1.getName() + "-" + peakAlarm, i, stock.GetCode());
				}
				String valleyAlarm = PeakValleyTracker.checkValleyBounce(stock.GetCode(), stock1.getNowPrice());
				if (valleyAlarm != null) {
					return new AlarmInfo(stock1.getName() + "-" + valleyAlarm, i, stock.GetCode());
				}
			}

			return null;
		}
		catch (Exception e)
		{
			Log.e("compareAllStocks", "error: " + e.getMessage(), e);
			return null;
		}
	}

	/**
	 * 检查某支股票的均线价格预警。
	 * 当股价突破（上穿或下穿）某条均线时触发预警。
	 *
	 * @param code 股票代码
	 * @param nowPrice 当前价
	 * @param yestodayPrice 昨收价
	 * @param maMng MaPriceMng 实例
	 * @return 预警消息，无触发则返回 null
	 */
	private static String checkMaAlarm(String code, float nowPrice, float yestodayPrice, MaPriceMng maMng, MaAlarmLogMng maAlarmLogMng) {
		if (maMng == null) return null;
		try {
			float[] maPrices = maMng.getMaPrices(code);
			if (maPrices == null) return null;

			// 价格不能为0
			if (nowPrice < 0.00001f) return null;

			// 当天日期字符串 yyyyMMdd
			java.text.SimpleDateFormat dateFormat = new java.text.SimpleDateFormat("yyyyMMdd");
			String todayStr = dateFormat.format(new java.util.Date());

			StringBuilder alarmMsg = new StringBuilder();

			for (int i = 0; i < MaPriceMng.MA_PERIODS.length; i++) {
				float ma = maPrices[i];
				if (ma < 0) continue; // 无数据

				int period = MaPriceMng.MA_PERIODS[i];
				String maName = MA_NAMES[i];

				// 上穿 MA：昨收低于 MA，现价达到或超过 MA
				boolean crossUp = (yestodayPrice < ma && nowPrice >= ma);

				// 下穿 MA：昨收高于 MA，现价达到或低于 MA
				boolean crossDown = (yestodayPrice > ma && nowPrice <= ma);

				if (crossUp) {
					// 去重：当天是否已报过该股票的该均线上穿
					if (maAlarmLogMng != null && maAlarmLogMng.isAlreadyAlarmedToday(code, period, 1, todayStr)) {
						continue;
					}
					float diff = ((nowPrice - ma) / ma) * 100;
					if (alarmMsg.length() > 0) alarmMsg.append("; ");
					alarmMsg.append("上穿" + maName + "(" + String.format("%.2f,%.1f%%", ma, diff) + ")");
					// 记录报警日志
					if (maAlarmLogMng != null) {
						maAlarmLogMng.recordAlarm(code, period, 1, ma, nowPrice, todayStr);
					}
				} else if (crossDown) {
					// 去重：当天是否已报过该股票的该均线下穿
					if (maAlarmLogMng != null && maAlarmLogMng.isAlreadyAlarmedToday(code, period, 2, todayStr)) {
						continue;
					}
					float diff = ((ma - nowPrice) / ma) * 100;
					if (alarmMsg.length() > 0) alarmMsg.append("; ");
					alarmMsg.append("下穿" + maName + "(" + String.format("%.2f,%.1f%%", ma, diff) + ")");
					// 记录报警日志
					if (maAlarmLogMng != null) {
						maAlarmLogMng.recordAlarm(code, period, 2, ma, nowPrice, todayStr);
					}
				}
			}

			if (alarmMsg.length() > 0) {
				return alarmMsg.toString();
			}
		} catch (Exception e) {
			Log.e("compareAllStocks", "MA alarm check failed: " + e.getMessage(), e);
		}
		return null;
	}


}

