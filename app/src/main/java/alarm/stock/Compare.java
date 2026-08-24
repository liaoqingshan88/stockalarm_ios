package alarm.stock;

import java.util.List;
import java.util.HashMap;
import java.util.Map;

import android.os.AsyncTask;
import android.util.Log;
import android.widget.ListView;
import android.widget.TextView;
import static alarm.stock.Constants.*;

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

class PeakValleyTracker {
    // ---- 上冲前高失败回落 / 下探前低失败反弹 状态追踪 ----
    // 上涨>3% → 下跌(跌幅≥涨幅一半) → 再次上涨接近前高(≥前高*0.995) → 未突破就掉头回落 → 报警
    // 下跌>3% → 反弹(涨幅≥跌幅一半) → 再次下跌接近前低(≤前低*1.005) → 未跌破就掉头反弹 → 报警
    // 每只股票每天只报一次
    //
    // State array slots:
    // int[0]: phase
    // int[1]: rise_start_price * SCALE (上涨/下跌起点)
    // int[2]: wave_high * SCALE (上涨段最高价)
    // int[3]: valley_price * SCALE (回调低点)
    // int[4]: wave_low * SCALE (下跌段最低价)
    // int[5]: peak_price * SCALE (反弹高点)
    // int[6]: touched_high_flag (1=已触及前高附近, 0=未触及)
    // int[7]: touched_low_flag (1=已触及前低附近, 0=未触及)
    // int[8]: high_alert_count (高侧报警次数)
    // int[9]: low_alert_count (低侧报警次数)
    // int[10]: near_high_confirm_start_ms (高侧进入前高附近区间的起始时间戳)
    // int[11]: near_low_confirm_start_ms (低侧进入前低附近区间的起始时间戳)
    private static final Map<String, int[]> approachStates = new HashMap<String, int[]>();
    private static final float RISE_THRESHOLD = 0.03f;       // 上涨≥3%
    private static final float RETRACE_RATIO = 0.382f;       // 浅回调：回调/反弹≥涨幅38.2%（黄金分割浅回调位）
    private static final float NEAR_HIGH = 0.995f;           // 接近前高(当前价 >= 前高*0.995)
    private static final float NEAR_LOW = 1.005f;            // 接近前低(当前价 <= 前低*1.005)
    private static final long CONFIRM_MS = 3L * 60L * 1000L; // 突破前高/前低确认时间：3分钟
    private static final int CONFIRM_SAMPLES = 36;           // 每5秒一查，3分钟≈36个采样点（用采样计数确认，避免时间戳溢出）
    private static final int MAX_ALERTS = 1;                 // 只报一次
    private static final int SCALE = 10000;                  // 小数转整数倍数
    
    private static ShlAlarmLogMng shlLogMng = null;
    
    public static void setShlLogMng(ShlAlarmLogMng mng) {
        shlLogMng = mng;
    }
    
    public static void resetForNewDay() {
        String today = getDateStr();
        java.util.Iterator<String> sit = approachStates.keySet().iterator();
        while (sit.hasNext()) {
            if (!sit.next().startsWith(today)) { sit.remove(); }
        }
    }
    
    private static String getDateStr() {
        return new java.text.SimpleDateFormat("yyyyMMdd").format(new java.util.Date());
    }
    
    private static String makeKey(String code) {
        return getDateStr() + ":" + code;
    }

    public static String checkApproachFailure(String code, String name, float nowPrice) {
        String key = makeKey(code);
        int[] s = approachStates.get(key);
        if (s == null) {
            s = new int[12];
            approachStates.put(key, s);
        }
        
        int phase = s[0];
        String result = null;
        
        float riseStartPrice = s[1] / (float) SCALE;
        float waveHigh = s[2] / (float) SCALE;
        float valleyPrice = s[3] / (float) SCALE;
        float waveLow = s[4] / (float) SCALE;
        float peakPrice = s[5] / (float) SCALE;
        
        switch (phase) {
            case 0: // idle
                if (s[1] == 0) {
                    s[1] = (int)(nowPrice * SCALE);
                } else {
                    if (nowPrice > riseStartPrice * (1 + RISE_THRESHOLD)) {
                        s[2] = (int)(nowPrice * SCALE);
                        s[0] = 1;
                        Log.d("Approach", code + " 上涨3%至" + nowPrice + ", 进入up_wave");
                    } else if (nowPrice < riseStartPrice * (1 - RISE_THRESHOLD)) {
                        s[4] = (int)(nowPrice * SCALE);
                        s[0] = 10;
                        Log.d("Approach", code + " 下跌3%至" + nowPrice + ", 进入down_wave");
                    }
                }
                break;
                
            case 1: // up_wave
                waveHigh = s[2] / (float) SCALE;
                riseStartPrice = s[1] / (float) SCALE;
                if (nowPrice > waveHigh) {
                    s[2] = (int)(nowPrice * SCALE);
                } else {
                    float riseAmount = waveHigh - riseStartPrice;
                    float dropAmount = waveHigh - nowPrice;
                    if (riseAmount > 0.0001f && dropAmount >= riseAmount * RETRACE_RATIO) {
                        s[3] = (int)(nowPrice * SCALE);
                        s[0] = 2;
                        Log.d("Approach", code + " 浅回调(38.2%)至" + nowPrice + ", 进入pullback");
                    }
                }
                break;
                
            case 2: // pullback
                valleyPrice = s[3] / (float) SCALE;
                waveHigh = s[2] / (float) SCALE;
                if (nowPrice < valleyPrice) {
                    s[3] = (int)(nowPrice * SCALE);
                } else if (nowPrice > valleyPrice * 1.003f) {
                    if (nowPrice >= waveHigh * NEAR_HIGH) {
                        s[6] = 1;
                        s[0] = 3;
                        Log.d("Approach", code + " 触及前高" + waveHigh + " 现价" + nowPrice);
                    }
                }
                break;
                
            case 3: // re_approach_high: 接近前高，等待突破或超时确认失败
                waveHigh = s[2] / (float) SCALE;
                if (nowPrice > waveHigh * 1.002f) {
                    // 真突破前高，重置为新上涨波段
                    s[0] = 1;
                    s[1] = s[2];
                    s[2] = (int)(nowPrice * SCALE);
                    s[3] = 0;
                    s[6] = 0;
                    s[10] = 0;
                    Log.d("Approach", code + " 突破前高" + waveHigh + "至" + nowPrice + ", 重置");
                    break;
                }
                if (nowPrice >= waveHigh * NEAR_HIGH) {
                    // 仍在"接近前高但未突破"区间：累积采样数
                    s[10] = s[10] + 1;
                    if (s[6] == 1 && s[10] >= CONFIRM_SAMPLES) {
                        // 连续3分钟未能突破前高 → 确认上冲失败
                        int alertCount = s[8];
                        if (alertCount < MAX_ALERTS) {
                            alertCount++;
                            s[8] = alertCount;
                            if (shlLogMng != null) {
                                shlLogMng.recordAlarm(code, name, "high", nowPrice, 0, waveHigh, nowPrice, alertCount, MAX_ALERTS, getDateStr());
                            }
                            result = String.format("上冲前高未破(高%.2f现%.2f)", waveHigh, nowPrice);
                            Log.d("Approach", code + " 上冲前高未破(3分钟)! 高" + waveHigh + " 现" + nowPrice);
                        }
                        s[0] = 0;
                        s[1] = (int)(nowPrice * SCALE);
                        s[2] = 0;
                        s[3] = 0;
                        s[6] = 0;
                        s[10] = 0;
                    }
                    break;
                }
                // 已跌出接近区（掉头回落）：若曾接近过前高，提前确认失败
                if (s[6] == 1 || s[10] > 0) {
                    int alertCount = s[8];
                    if (alertCount < MAX_ALERTS) {
                        alertCount++;
                        s[8] = alertCount;
                        if (shlLogMng != null) {
                            shlLogMng.recordAlarm(code, name, "high", nowPrice, 0, waveHigh, nowPrice, alertCount, MAX_ALERTS, getDateStr());
                        }
                        result = String.format("上冲前高失败(高%.2f现%.2f)", waveHigh, nowPrice);
                        Log.d("Approach", code + " 上冲前高失败(掉头回落)! 高" + waveHigh + " 现" + nowPrice);
                    }
                    s[0] = 0;
                    s[1] = (int)(nowPrice * SCALE);
                    s[2] = 0;
                    s[3] = 0;
                    s[6] = 0;
                    s[10] = 0;
                }
                break;
                
            // ====== 下跌方向 ======
            case 10: // down_wave
                waveLow = s[4] / (float) SCALE;
                riseStartPrice = s[1] / (float) SCALE;
                if (nowPrice < waveLow) {
                    s[4] = (int)(nowPrice * SCALE);
                } else {
                    float dropAmount = riseStartPrice - waveLow;
                    float riseAmount = nowPrice - waveLow;
                    if (dropAmount > 0.0001f && riseAmount >= dropAmount * RETRACE_RATIO) {
                        s[5] = (int)(nowPrice * SCALE);
                        s[0] = 20;
                        Log.d("Approach", code + " 浅反弹(38.2%)至" + nowPrice + ", 进入rebound");
                    }
                }
                break;
                
            case 20: // rebound
                peakPrice = s[5] / (float) SCALE;
                waveLow = s[4] / (float) SCALE;
                if (nowPrice > peakPrice) {
                    s[5] = (int)(nowPrice * SCALE);
                } else if (nowPrice < peakPrice * 0.997f) {
                    if (nowPrice <= waveLow * NEAR_LOW) {
                        s[7] = 1;
                        s[0] = 30;
                        Log.d("Approach", code + " 触及前低" + waveLow + " 现价" + nowPrice);
                    }
                }
                break;
                
            case 30: // re_approach_low: 接近前低，等待跌破或超时确认失败
                waveLow = s[4] / (float) SCALE;
                if (nowPrice < waveLow * 0.998f) {
                    // 真跌破前低，重置为新下跌波段
                    s[0] = 10;
                    s[1] = s[4];
                    s[4] = (int)(nowPrice * SCALE);
                    s[5] = 0;
                    s[7] = 0;
                    s[11] = 0;
                    Log.d("Approach", code + " 跌破前低" + waveLow + "至" + nowPrice + ", 重置");
                    break;
                }
                if (nowPrice <= waveLow * NEAR_LOW) {
                    // 仍在"接近前低但未跌破"区间：累积采样数
                    s[11] = s[11] + 1;
                    if (s[7] == 1 && s[11] >= CONFIRM_SAMPLES) {
                        // 连续3分钟未能跌破前低 → 确认下探失败
                        int alertCount = s[9];
                        if (alertCount < MAX_ALERTS) {
                            alertCount++;
                            s[9] = alertCount;
                            if (shlLogMng != null) {
                                shlLogMng.recordAlarm(code, name, "low", nowPrice, 0, waveLow, nowPrice, alertCount, MAX_ALERTS, getDateStr());
                            }
                            result = String.format("下探前低未破(低%.2f现%.2f)", waveLow, nowPrice);
                            Log.d("Approach", code + " 下探前低未破(3分钟)! 低" + waveLow + " 现" + nowPrice);
                        }
                        s[0] = 0;
                        s[1] = (int)(nowPrice * SCALE);
                        s[4] = 0;
                        s[5] = 0;
                        s[7] = 0;
                        s[11] = 0;
                    }
                    break;
                }
                // 已反弹离开接近区（掉头反弹）：若曾接近过前低，提前确认失败
                if (s[7] == 1 || s[11] > 0) {
                    int alertCount = s[9];
                    if (alertCount < MAX_ALERTS) {
                        alertCount++;
                        s[9] = alertCount;
                        if (shlLogMng != null) {
                            shlLogMng.recordAlarm(code, name, "low", nowPrice, 0, waveLow, nowPrice, alertCount, MAX_ALERTS, getDateStr());
                        }
                        result = String.format("下探前低失败(低%.2f现%.2f)", waveLow, nowPrice);
                        Log.d("Approach", code + " 下探前低失败(掉头反弹)! 低" + waveLow + " 现" + nowPrice);
                    }
                    s[0] = 0;
                    s[1] = (int)(nowPrice * SCALE);
                    s[4] = 0;
                    s[5] = 0;
                    s[7] = 0;
                    s[11] = 0;
                }
                break;
        }
        return result;
    }
    
    // 保留原有的冲高回落/探底回升
}



class Compare extends AsyncTask<StockMng, TextView, List<SinaStockInfo>>{

    // ===== 同步后报警节流门（问题2：先刷新价格/更新界面，再报警）=====
    // 当 sync 拉取/写入新增了股票（收到另一端同步设置的股票预警）时，记录时间戳；
    // 后台保活预警在随后一小段窗口内跳过报警检查，让 UI 先刷新价格并展示，避免“收到同步数据立即使报警”。
    private static final long SYNC_ALARM_GATE_MS = 10_000L; // 同步后 10 秒内不触发后台报警
    private static volatile long lastSyncNewStockMs = 0L;

    /** 标记：刚刚通过同步新增了股票（另一端同步过来的），后台报警应短暂让路给 UI 刷新。 */
    public static void markSyncNewStock() {
        lastSyncNewStockMs = System.currentTimeMillis();
    }

    /** 后台预警检查是否应跳过（同步新数据刚到达、界面价格尚未刷新）。 */
    public static boolean shouldHoldBackgroundAlarm() {
        return lastSyncNewStockMs > 0 && (System.currentTimeMillis() - lastSyncNewStockMs) < SYNC_ALARM_GATE_MS;
    }

    TextView te=null;
    ListView listview = null;
    String[] codes = null;
    StockMng stockMng = null;
    AlarmActivity act = null;
    MaPriceMng maMng = null;
    MaAlarmLogMng maAlarmLogMng = null;
    
    public Compare(AlarmActivity act, TextView te, ListView listview, MaPriceMng maMng, MaAlarmLogMng maAlarmLogMng) {
        super();
        this.te = te;
        this.listview = listview;
        this.act = act;
        this.maMng = maMng;
        this.maAlarmLogMng = maAlarmLogMng;
        try {
            if (act != null) {
                PeakValleyTracker.setShlLogMng(new ShlAlarmLogMng(new StockSQLite(act)));
            }
        } catch (Exception e) {
            Log.e("Compare", "Failed to init SHL log mgr", e);
        }
    }

   @Override
    protected List<SinaStockInfo> doInBackground(StockMng... params)  {
	   try {
		   this.stockMng = params[0];
		   if(null == this.stockMng ) {
		   		Log.e("cmp", "compare para err");
		   		return null;
		   }
		   this.codes = stockMng.GetCodes();
		   SinaStockClient mClient = SinaStockClient.getInstance();
		   List<SinaStockInfo> stockinfo = mClient.getStockInfo(codes);
		   return stockinfo;
	   } catch (Exception e) {
		   return null;
	   }
    }
   
   @Override
    protected void onPostExecute(List<SinaStockInfo> result){
		try{
		    super.onPostExecute(result);
	        if (null == result) {
	        	AlarmActivity.is_http_timeout = true;
	        	return;
	        }
	        AlarmActivity.is_http_timeout = false;
	        act.ReflashStockPrice(listview, result);
	        AlarmInfo alarmInfo = compareAllStocks(stockMng, result, maMng, maAlarmLogMng);
			if (alarmInfo != null && alarmInfo.message.length() > 0) {
				te.append("\n"+alarmInfo.message);
				act.Notification(alarmInfo.message);
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
   
	private static final String[] MA_NAMES = {"MA5", "MA10", "MA20", "MA30", "MA60", "MA120", "MA250"};

	/**
	 * 后台保活预警检查（供 StockAlertService 调用，不依赖 Activity）。
	 * 在后台线程拉取行情，命中预警时通过系统通知发出。
	 * @return 命中的预警消息，无则返回 null
	 */
	public static AlarmInfo checkAndNotify(android.content.Context ctx, StockMng stockMng, MaPriceMng maMng, MaAlarmLogMng maAlarmLogMng) {
		return checkAndNotify(ctx, stockMng, maMng, maAlarmLogMng, null);
	}

	/**
	 * 后台保活预警检查（指定待检查的股票代码子集）。
	 * 港股延长时段(11:30-12:00、15:00-16:00)只查港股时，由调用方传入过滤后的 codes。
	 * @param codes 要检查的股票代码；为 null 时取本地全部激活股票（stockMng.GetCodes()）
	 */
	public static AlarmInfo checkAndNotify(android.content.Context ctx, StockMng stockMng, MaPriceMng maMng, MaAlarmLogMng maAlarmLogMng, String[] codes) {
		try {
			if (stockMng == null) return null;

			// 问题2：同步刚写入新股票（另一端同步过来的预警设置）时，后台预警让路，
			// 等 UI 先刷新价格并展示后再检查，避免“收到同步数据立即报警、界面价格却未更新”。
			if (shouldHoldBackgroundAlarm()) {
				Log.d("Compare", "同步新数据刚到达，暂缓后台报警检查，等待界面刷新价格");
				return null;
			}

			if (codes == null) {
				codes = stockMng.GetCodes();
			}
			if (codes == null || codes.length == 0) return null;

			SinaStockClient client = SinaStockClient.getInstance();
			List<SinaStockInfo> result = client.getStockInfo(codes);
			if (result == null || result.isEmpty()) return null;

			AlarmInfo alarmInfo = compareAllStocks(stockMng, result, maMng, maAlarmLogMng);
			if (alarmInfo != null && alarmInfo.message != null && alarmInfo.message.length() > 0) {
				AlarmNotifier.notifyAlarm(ctx, alarmInfo.message);
				return alarmInfo;
			}
		} catch (Exception e) {
			Log.e("Compare", "checkAndNotify error: " + e.getMessage(), e);
		}
		return null;
	}

	public static AlarmInfo compareAllStocks(StockMng stockMng, List<SinaStockInfo> stockinfo, MaPriceMng maMng, MaAlarmLogMng maAlarmLogMng ) {
		try{
			List<StockMng> listStocks = stockMng.getStocks();
			PeakValleyTracker.resetForNewDay();

			// 按股票代码建立映射，支持任意「待检查代码子集」（如港股延长时段只查港股），
			// 避免依赖 stockinfo 与 listStocks 顺序一一对应。
			Map<String, SinaStockInfo> infoByCode = new HashMap<String, SinaStockInfo>();
			for (SinaStockInfo si : stockinfo) {
				if (si != null && si.getCode() != null) {
					infoByCode.put(si.getCode().toLowerCase(), si);
				}
			}

			for (int i = 0; i < listStocks.size(); i++) {
				StockMng stock = listStocks.get(i);
				if (!stock.IsValid()) continue;
				SinaStockInfo stock1 = infoByCode.get(stock.GetCode().toLowerCase());
				if (stock1 == null) continue;
				int ret = stock.Compare(stock1.getNowPrice(), stock1.getYestodayPrice());
				if (CMP_RESULT_NONE != ret) {
					return new AlarmInfo(stock1.getName()+"-"+stock.GetCmpareResult(), i, stock.GetCode());
				}

				if (maMng != null) {
					String maAlarm = checkMaAlarm(stock.GetCode(), stock1.getName(), stock1.getNowPrice(), stock1.getYestodayPrice(), maMng, maAlarmLogMng);
					if (maAlarm != null) {
						return new AlarmInfo(stock1.getName() + "-" + maAlarm, i, stock.GetCode());
					}
				}

				// 上冲前高失败回落 / 下探前低失败反弹 检测
				String approachAlarm = PeakValleyTracker.checkApproachFailure(stock.GetCode(), stock1.getName(), stock1.getNowPrice());
				if (approachAlarm != null) {
					return new AlarmInfo(stock1.getName() + "-" + approachAlarm, i, stock.GetCode());
				}
			}
			return null;
		} catch (Exception e) {
			Log.e("compareAllStocks", "error: " + e.getMessage(), e);
			return null;
		}
	}

	private static String checkMaAlarm(String code, String name, float nowPrice, float yestodayPrice, MaPriceMng maMng, MaAlarmLogMng maAlarmLogMng) {
		if (maMng == null) return null;
		try {
			float[] maPrices = maMng.getMaPrices(code);
			if (maPrices == null) return null;
			if (nowPrice < 0.00001f) return null;
			java.text.SimpleDateFormat dateFormat = new java.text.SimpleDateFormat("yyyyMMdd");
			String todayStr = dateFormat.format(new java.util.Date());
			StringBuilder alarmMsg = new StringBuilder();
			for (int i = 0; i < MaPriceMng.MA_PERIODS.length; i++) {
				float ma = maPrices[i];
				if (ma < 0) continue;
				int period = MaPriceMng.MA_PERIODS[i];
				String maName = MA_NAMES[i];
				boolean crossUp = (yestodayPrice < ma && nowPrice >= ma);
				boolean crossDown = (yestodayPrice > ma && nowPrice <= ma);
				if (crossUp) {
					if (maAlarmLogMng != null && maAlarmLogMng.isAlreadyAlarmedToday(code, period, 1, todayStr)) continue;
					float diff = ((nowPrice - ma) / ma) * 100;
					if (alarmMsg.length() > 0) alarmMsg.append("; ");
					alarmMsg.append("上穿" + maName + "(" + String.format("%.2f,%.1f%%", ma, diff) + ")");
					if (maAlarmLogMng != null) maAlarmLogMng.recordAlarm(code, name, period, 1, ma, nowPrice, todayStr);
				} else if (crossDown) {
					if (maAlarmLogMng != null && maAlarmLogMng.isAlreadyAlarmedToday(code, period, 2, todayStr)) continue;
					float diff = ((ma - nowPrice) / ma) * 100;
					if (alarmMsg.length() > 0) alarmMsg.append("; ");
					alarmMsg.append("下穿" + maName + "(" + String.format("%.2f,%.1f%%", ma, diff) + ")");
					if (maAlarmLogMng != null) maAlarmLogMng.recordAlarm(code, name, period, 2, ma, nowPrice, todayStr);
				}
			}
			if (alarmMsg.length() > 0) return alarmMsg.toString();
		} catch (Exception e) {
			Log.e("compareAllStocks", "MA alarm check failed: " + e.getMessage(), e);
		}
		return null;
	}
}
