package alarm.stock;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;

public class SettingsActivity extends Activity {
	
	private static final String PREF_VIBRATE = "pref_vibrate";
	private static final String PREF_RINGTONE_URI = "pref_ringtone_uri";
	private static final String PREF_COLORFUL_LIST = "pref_colorful_list";
	private static final String PREF_MA_ALARM_ENABLED = "pref_ma_alarm_enabled";
	private static final String PREF_MA_ALARM_ENABLED_TS = "pref_ma_alarm_enabled_ts";
	private static final int REQUEST_CODE_RINGTONE = 1001;
	private static final String FEEDBACK_URL_PRIMARY = "http://www.yanhexing.com:9000/stock_alarm/soft_dev";
	private static final String FEEDBACK_URL_FALLBACK = "http://47.107.180.174:9000/stock_alarm/soft_dev";
	
	private CheckBox checkVibrate;
	private TextView textRingtone;
	private Button btnSelectRingtone;
	private RadioGroup groupColorMode;
	private RadioButton radioColorful;
	private RadioButton radioBw;
	private EditText editFeedback;
	private Button btnSubmitFeedback;
	private TextView textSubmitStatus;
	private SharedPreferences prefs;

	// 均线预警设置（全局开关）
	private CheckBox checkMaAlarm;
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_settings);
		
		prefs = PreferenceManager.getDefaultSharedPreferences(this);
		
		checkVibrate = (CheckBox) findViewById(R.id.check_vibrate);
		textRingtone = (TextView) findViewById(R.id.text_ringtone);
		btnSelectRingtone = (Button) findViewById(R.id.btn_select_ringtone);
		groupColorMode = (RadioGroup) findViewById(R.id.group_color_mode);
		radioColorful = (RadioButton) findViewById(R.id.radio_colorful);
		radioBw = (RadioButton) findViewById(R.id.radio_bw);
		editFeedback = (EditText) findViewById(R.id.edit_feedback);
		btnSubmitFeedback = (Button) findViewById(R.id.btn_submit_feedback);
		textSubmitStatus = (TextView) findViewById(R.id.text_submit_status);
		textSubmitStatus.setMovementMethod(new android.text.method.ScrollingMovementMethod());
		Button btnBack = (Button) findViewById(R.id.btn_back);
		// 多端同步账号入口
		Button btnSyncAccount = (Button) findViewById(R.id.btn_sync_account);
		if (btnSyncAccount != null) {
			btnSyncAccount.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					Intent intent = new Intent(SettingsActivity.this, SyncLoginActivity.class);
					startActivity(intent);
				}
			});
		}
		
		// 加载保存的设置
		loadSettings();

		// 均线预警设置
		setupMaPriceSettings();

		
		// 振动开关
		checkVibrate.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				saveVibrateSetting(checkVibrate.isChecked());
			}
		});
		
		// 选择铃声
		btnSelectRingtone.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				selectRingtone();
			}
		});
		
		// 返回按钮
		btnBack.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				finish();
			}
		});

		// 列表颜色模式
		if (groupColorMode != null) {
			groupColorMode.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
				@Override
				public void onCheckedChanged(RadioGroup group, int checkedId) {
					if (checkedId == R.id.radio_colorful) {
						saveColorfulListSetting(true);
					} else if (checkedId == R.id.radio_bw) {
						saveColorfulListSetting(false);
					}
				}
			});
		}

		// 提交软件修改意见
		if (btnSubmitFeedback != null) {
			btnSubmitFeedback.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					submitFeedback();
				}
			});
		}
	}

	/**
	 * 均线预警设置（全局开关）：勾选=所有股票都做均线预警，不勾选=都不做。
	 * 均线价格由 MaDownloader 按每只股票自动下载计算，无需用户手填。
	 */
	private void setupMaPriceSettings() {
		checkMaAlarm = (CheckBox) findViewById(R.id.check_ma_alarm);
		if (checkMaAlarm == null) {
			return;
		}
		// 加载保存的开关状态，默认开启（保持原有行为）
		checkMaAlarm.setChecked(getMaAlarmEnabled(this));
		checkMaAlarm.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				saveMaAlarmSetting(checkMaAlarm.isChecked());
			}
		});
	}

	private void saveMaAlarmSetting(boolean enabled) {
		final long ts = System.currentTimeMillis();
		SharedPreferences.Editor editor = prefs.edit();
		editor.putBoolean(PREF_MA_ALARM_ENABLED, enabled);
		editor.putLong(PREF_MA_ALARM_ENABLED_TS, ts);
		editor.apply();
		Toast.makeText(this, enabled ? "已启用均线预警" : "已关闭均线预警", Toast.LENGTH_SHORT).show();
		// 立即在后台线程上传到服务器（已登录同步时才传），失败静默重试一次，不阻塞 UI
		uploadMaAlarmSettingAsync(enabled, ts);
	}

	/**
	 * 后台线程上传均线全局开关到服务端（LWW）。未登录则跳过；失败静默重试一次。
	 */
	private void uploadMaAlarmSettingAsync(final boolean enabled, final long ts) {
		new Thread(new Runnable() {
			@Override
			public void run() {
				try {
					SyncClient client = new SyncClient(SettingsActivity.this);
					if (!client.isLoggedIn()) return;
					try {
						client.uploadMaAlarmSettings(enabled, ts);
					} catch (Exception e1) {
						Log.w("Settings", "上传均线开关失败，重试一次: " + e1.getMessage());
						try {
							Thread.sleep(2000);
							client.uploadMaAlarmSettings(enabled, ts);
						} catch (Exception e2) {
							Log.w("Settings", "上传均线开关重试仍失败: " + e2.getMessage());
						}
					}
				} catch (Throwable t) {
					Log.w("Settings", "上传均线开关异常: " + t.getMessage());
				}
			}
		}).start();
	}


	private void submitFeedback() {
		if (editFeedback == null || btnSubmitFeedback == null) {
			return;
		}
		final String feedback = editFeedback.getText().toString().trim();
		if (feedback.length() == 0) {
			Toast.makeText(this, "请先输入修改意见", Toast.LENGTH_SHORT).show();
			return;
		}

		btnSubmitFeedback.setEnabled(false);
		btnSubmitFeedback.setText("提交中...");

		new Thread(new Runnable() {
			@Override
			public void run() {
				int maxRetries = 3;
				Exception lastException = null;
				String currentUrl = FEEDBACK_URL_PRIMARY;
				
				for (int attempt = 1; attempt <= maxRetries; attempt++) {
					HttpURLConnection conn = null;
					try {
						URL url = new URL(currentUrl);
						conn = (HttpURLConnection) url.openConnection();
						conn.setRequestMethod("POST");
						conn.setConnectTimeout(15000);
						conn.setReadTimeout(15000);
						conn.setDoOutput(true);
						conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
						conn.setRequestProperty("Connection", "close");

						// 构建 JSON body
						String jsonBody = "{\"requirement\":\"" + escapeJsonString(feedback) + "\"}";
						OutputStream os = conn.getOutputStream();
						os.write(jsonBody.getBytes("UTF-8"));
						os.flush();
						os.close();

						final int code = conn.getResponseCode();
						Log.i("Settings", "HTTP状态码: " + code + " (尝试 " + attempt + "/" + maxRetries + " URL: " + currentUrl + ")");
						
						// 状态码 200-299 认为成功，尝试读取响应
						if (code >= 200 && code < 300) {
							String serverMessage = "✅ 提交成功！";
							long feedbackId = -1;
							try {
								InputStream stream = conn.getInputStream();
								if (stream != null) {
									BufferedReader br = new BufferedReader(new InputStreamReader(stream, "UTF-8"));
									StringBuilder response = new StringBuilder();
									String line;
									while ((line = br.readLine()) != null) {
										response.append(line);
									}
									br.close();
									
									// 解析 JSON 响应
									String jsonStr = response.toString();
									if (jsonStr.contains("\"message\"")) {
										// 简单提取 message 字段
										int msgStart = jsonStr.indexOf("\"message\":\"") + 11;
										int msgEnd = jsonStr.indexOf("\"", msgStart);
										if (msgStart > 10 && msgEnd > msgStart) {
											serverMessage = jsonStr.substring(msgStart, msgEnd);
											// 处理转义字符
											serverMessage = serverMessage.replace("\\u2705", "✅");
										}
									}
									// 提取 id
									if (jsonStr.contains("\"id\":")) {
										int idStart = jsonStr.indexOf("\"id\":") + 5;
										int idEnd = jsonStr.indexOf(",", idStart);
										if (idEnd == -1) idEnd = jsonStr.indexOf("}", idStart);
										if (idStart > 5 && idEnd > idStart) {
											try {
												feedbackId = Long.parseLong(jsonStr.substring(idStart, idEnd).trim());
											} catch (Exception e) {
												Log.w("Settings", "解析id失败: " + e.getMessage());
											}
										}
									}
								}
							} catch (Exception readEx) {
								Log.w("Settings", "读取响应失败（但已成功）: " + readEx.getMessage());
							}
							
							final String finalMessage = serverMessage;
							final long finalFeedbackId = feedbackId;
							runOnUiThread(new Runnable() {
								@Override
								public void run() {
									btnSubmitFeedback.setEnabled(true);
									btnSubmitFeedback.setText("提交意见");
									editFeedback.setText("");
									textSubmitStatus.setText(finalMessage + "\n\n📊 进度：\n✅ 提交需求\n✅ 服务器接收\n⏳ 等待审核...");
									textSubmitStatus.setVisibility(View.VISIBLE);
									// 开始轮询状态
									if (finalFeedbackId > 0) {
										startPollingStatus(finalFeedbackId);
									}
								}
							});
							return; // 成功，退出
						}
						
						// 非200，读取响应内容判断原因
						InputStream stream = conn.getErrorStream();
						StringBuilder response = new StringBuilder();
						if (stream != null) {
							try {
								BufferedReader br = new BufferedReader(new InputStreamReader(stream, "UTF-8"));
								String line;
								while ((line = br.readLine()) != null) {
									response.append(line);
								}
								br.close();
							} catch (Exception readEx) {
								Log.w("Settings", "读取响应失败: " + readEx.getMessage());
							}
						}
						Log.i("Settings", "响应内容: " + response.toString());
						
						// 检查是否被ICP拦截（403 + HTML响应）
						boolean isIcpBlocked = code == 403 && response.toString().contains("ICP");
						if (isIcpBlocked && currentUrl.equals(FEEDBACK_URL_PRIMARY)) {
							Log.w("Settings", "域名被ICP拦截，切换到IP地址...");
							currentUrl = FEEDBACK_URL_FALLBACK;
							if (conn != null) { try { conn.disconnect(); } catch (Exception ignored) {} }
							continue;
						}
						
						// 非200状态码，重试或失败
						if (attempt < maxRetries) {
							Log.w("Settings", "提交失败，准备重试...");
							Thread.sleep(1000);
							continue;
						} else {
							final int finalCode = code;
							final String responseStr = response.toString();
							runOnUiThread(new Runnable() {
								@Override
								public void run() {
									btnSubmitFeedback.setEnabled(true);
									btnSubmitFeedback.setText("提交意见");
									Toast.makeText(SettingsActivity.this, "提交失败(" + finalCode + "): " + responseStr, Toast.LENGTH_LONG).show();
								}
							});
							return;
						}
					} catch (final Exception e) {
						Log.e("Settings", "提交修改意见失败(尝试 " + attempt + "): " + e.getMessage());
						lastException = e;
						
						if (conn != null) {
							try { conn.disconnect(); } catch (Exception ignored) {}
							conn = null;
						}
						
						// Connection reset 或 timeout
						boolean shouldRetry = e.getMessage() != null && 
							(e.getMessage().contains("Connection reset") || 
							 e.getMessage().contains("timeout") ||
							 e.getMessage().contains("ECONNRESET"));
						
						// 如果用的是域名，切换到IP
						if (shouldRetry && currentUrl.equals(FEEDBACK_URL_PRIMARY)) {
							Log.i("Settings", "域名连接失败，切换到IP地址...");
							currentUrl = FEEDBACK_URL_FALLBACK;
							try { Thread.sleep(500); } catch (InterruptedException ie) {}
							continue;
						}
						
						// IP也失败，重试
						if (shouldRetry && attempt < maxRetries) {
							Log.i("Settings", "网络异常，1秒后重试...");
							try { Thread.sleep(1000); } catch (InterruptedException ie) {}
							continue;
						}
						
						// 最终失败
						final boolean maybeSuccess = shouldRetry;
						final String errMsg = e.getMessage();
						runOnUiThread(new Runnable() {
							@Override
							public void run() {
								btnSubmitFeedback.setEnabled(true);
								btnSubmitFeedback.setText("提交意见");
								if (maybeSuccess) {
									editFeedback.setText("");
									Toast.makeText(SettingsActivity.this, "已提交（网络不稳定，请稍后查看）", Toast.LENGTH_LONG).show();
								} else {
									Toast.makeText(SettingsActivity.this, "提交失败: " + errMsg, Toast.LENGTH_SHORT).show();
								}
							}
						});
						return;
					} finally {
						if (conn != null) {
							try { conn.disconnect(); } catch (Exception ignored) {}
						}
					}
				}
			}
		}).start();
	}
	
	private void loadSettings() {
		// 显示版本号
		try {
			PackageInfo pInfo = getPackageManager().getPackageInfo(getPackageName(), 0);
			String versionName = pInfo.versionName;
			int versionCode = pInfo.versionCode;
			TextView textVersion = (TextView) findViewById(R.id.text_version);
			if (textVersion != null) {
				textVersion.setText("当前版本：" + versionName + " (" + versionCode + ")");
			}
		} catch (PackageManager.NameNotFoundException e) {
			Log.e("Settings", "获取版本号失败: " + e.getMessage());
		}

		// 加载振动设置（默认为true）
		boolean vibrate = prefs.getBoolean(PREF_VIBRATE, true);
		checkVibrate.setChecked(vibrate);

		// 加载列表颜色（默认为彩色）
		boolean colorful = prefs.getBoolean(PREF_COLORFUL_LIST, true);
		if (radioColorful != null && radioBw != null) {
			if (colorful) {
				radioColorful.setChecked(true);
			} else {
				radioBw.setChecked(true);
			}
		}
		
		// 加载铃声设置
		String ringtoneUri = prefs.getString(PREF_RINGTONE_URI, null);
		if (ringtoneUri != null) {
			try {
				Uri uri = Uri.parse(ringtoneUri);
				String ringtoneName = RingtoneManager.getRingtone(this, uri).getTitle(this);
				textRingtone.setText(ringtoneName);
			} catch (Exception e) {
				Log.e("Settings", "加载铃声失败: " + e.getMessage());
				textRingtone.setText("默认铃声");
			}
		} else {
			textRingtone.setText("默认铃声");
		}
	}
	
	private void saveVibrateSetting(boolean vibrate) {
		SharedPreferences.Editor editor = prefs.edit();
		editor.putBoolean(PREF_VIBRATE, vibrate);
		editor.apply();
		Toast.makeText(this, vibrate ? "已启用振动" : "已禁用振动", Toast.LENGTH_SHORT).show();
	}

	private void saveColorfulListSetting(boolean colorful) {
		SharedPreferences.Editor editor = prefs.edit();
		editor.putBoolean(PREF_COLORFUL_LIST, colorful);
		editor.apply();
		Toast.makeText(this, colorful ? "列表颜色：彩色" : "列表颜色：黑白", Toast.LENGTH_SHORT).show();
	}

	// 轮询反馈状态
	private java.util.Timer pollingTimer;
	private void startPollingStatus(final long feedbackId) {
		// 停止之前的轮询
		if (pollingTimer != null) {
			pollingTimer.cancel();
		}
		
		pollingTimer = new java.util.Timer();
		pollingTimer.scheduleAtFixedRate(new java.util.TimerTask() {
			@Override
			public void run() {
				checkFeedbackStatus(feedbackId);
			}
		}, 3000, 5000); // 3秒后开始，每5秒查询一次
	}
	
	private void checkFeedbackStatus(final long feedbackId) {
		new Thread(new Runnable() {
			@Override
			public void run() {
				try {
					String urlStr = "http://47.107.180.174:9000/stock_alarm/soft_dev/" + feedbackId;
					java.net.URL url = new java.net.URL(urlStr);
					java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
					conn.setRequestMethod("GET");
					conn.setConnectTimeout(5000);
					conn.setReadTimeout(5000);
					
					int code = conn.getResponseCode();
					if (code == 200) {
						java.io.BufferedReader br = new java.io.BufferedReader(
							new java.io.InputStreamReader(conn.getInputStream(), "UTF-8"));
						StringBuilder response = new StringBuilder();
						String line;
						while ((line = br.readLine()) != null) {
							response.append(line);
						}
						br.close();
						
						// 解析状态
						String jsonStr = response.toString();
						String status = "";
						String note = "";
						
						if (jsonStr.contains("\"status\":\"")) {
							int statusStart = jsonStr.indexOf("\"status\":\"") + 10;
							int statusEnd = jsonStr.indexOf("\"", statusStart);
							if (statusStart > 9 && statusEnd > statusStart) {
								status = jsonStr.substring(statusStart, statusEnd);
							}
						}
						if (jsonStr.contains("\"note\":\"")) {
							int noteStart = jsonStr.indexOf("\"note\":\"") + 8;
							int noteEnd = jsonStr.indexOf("\"", noteStart);
							if (noteStart > 7 && noteEnd > noteStart) {
								note = jsonStr.substring(noteStart, noteEnd);
							}
						}
						
						final String finalStatus = status;
						final String finalNote = note;
						
						runOnUiThread(new Runnable() {
							@Override
							public void run() {
								updateProgressDisplay(finalStatus, finalNote, feedbackId);
							}
						});
						
						// 如果已完成，停止轮询
						if ("done".equals(status) || "rejected".equals(status)) {
							if (pollingTimer != null) {
								pollingTimer.cancel();
								pollingTimer = null;
							}
						}
					}
					conn.disconnect();
				} catch (Exception e) {
					Log.w("Settings", "查询状态失败: " + e.getMessage());
				}
			}
		}).start();
	}
	
	private void updateProgressDisplay(String status, String note, long feedbackId) {
		StringBuilder sb = new StringBuilder();
		sb.append("📋 需求ID: ").append(feedbackId).append("\n\n");
		sb.append("📊 处理进度：\n");
		
		// 根据状态显示进度
		String[] steps = {"received", "reviewed", "developing", "testing", "done"};
		String[] labels = {"已接收", "审核中", "开发中", "测试中", "已完成"};
		String[] icons = {"📥", "👀", "🔨", "🧪", "✅"};
		
		int currentStep = -1;
		for (int i = 0; i < steps.length; i++) {
			if (steps[i].equals(status)) {
				currentStep = i;
				break;
			}
		}
		// 一些状态映射
		if ("reviewing".equals(status)) currentStep = 1;
		if ("build".equals(status)) currentStep = 3;
		if ("rejected".equals(status)) currentStep = -2;
		
		for (int i = 0; i < steps.length; i++) {
			if (currentStep >= i) {
				sb.append("✅ ").append(labels[i]).append("\n");
			} else if (currentStep == -2) {
				sb.append("❌ ").append(labels[i]).append("\n");
			} else {
				sb.append("⏳ ").append(labels[i]).append("\n");
			}
		}
		
		// 显示备注
		if (note != null && note.length() > 0) {
			sb.append("\n💬 ").append(note);
		}
		
		// 特殊状态处理
		if ("rejected".equals(status)) {
			sb.append("\n\n❌ 需求被拒绝");
		} else if ("done".equals(status)) {
			sb.append("\n\n🎉 需求已完成，请检查新版本！");
		}
		
		textSubmitStatus.setText(sb.toString());
	}

	private void selectRingtone() {
		try {
			// 获取当前选择的铃声URI
			String currentUri = prefs.getString(PREF_RINGTONE_URI, null);
			Uri currentRingtoneUri = currentUri != null ? Uri.parse(currentUri) : RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
			
			// 打开铃声选择器
			Intent intent = new Intent(RingtoneManager.ACTION_RINGTONE_PICKER);
			intent.putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_NOTIFICATION);
			intent.putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, "选择通知铃声");
			intent.putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, currentRingtoneUri);
			intent.putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true);
			intent.putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, true);
			
			startActivityForResult(intent, REQUEST_CODE_RINGTONE);
		} catch (Exception e) {
			Log.e("Settings", "打开铃声选择器失败: " + e.getMessage());
			Toast.makeText(this, "无法打开铃声选择器", Toast.LENGTH_SHORT).show();
		}
	}
	
	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		super.onActivityResult(requestCode, resultCode, data);
		
		if (requestCode == REQUEST_CODE_RINGTONE && resultCode == RESULT_OK) {
			Uri ringtoneUri = data.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI);
			
			if (ringtoneUri != null) {
				// 保存选择的铃声URI
				SharedPreferences.Editor editor = prefs.edit();
				editor.putString(PREF_RINGTONE_URI, ringtoneUri.toString());
				editor.apply();
				
				// 显示铃声名称
				try {
					String ringtoneName = RingtoneManager.getRingtone(this, ringtoneUri).getTitle(this);
					textRingtone.setText(ringtoneName);
					Toast.makeText(this, "已选择: " + ringtoneName, Toast.LENGTH_SHORT).show();
				} catch (Exception e) {
					Log.e("Settings", "获取铃声名称失败: " + e.getMessage());
					textRingtone.setText("自定义铃声");
				}
			} else {
				// 用户选择了"无"（静音）
				SharedPreferences.Editor editor = prefs.edit();
				editor.remove(PREF_RINGTONE_URI);
				editor.apply();
				textRingtone.setText("静音");
				Toast.makeText(this, "已设置为静音", Toast.LENGTH_SHORT).show();
			}
		}
	}
	
	/**
	 * 获取振动设置
	 */
	public static boolean getVibrateEnabled(android.content.Context context) {
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
		return prefs.getBoolean(PREF_VIBRATE, true);
	}
	
	/**
	 * 获取铃声URI
	 */
	public static Uri getRingtoneUri(android.content.Context context) {
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
		String ringtoneUri = prefs.getString(PREF_RINGTONE_URI, null);
		if (ringtoneUri != null) {
			return Uri.parse(ringtoneUri);
		}
		return RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
	}

	/**
	 * 获取股票列表颜色模式：true=彩色(涨红跌绿)，false=黑白
	 */
	public static boolean getColorfulListEnabled(android.content.Context context) {
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
		return prefs.getBoolean(PREF_COLORFUL_LIST, true);
	}

	/**
	 * 获取均线预警全局开关：true=对所有股票做均线预警，false=都不做。默认 true。
	 */
	public static boolean getMaAlarmEnabled(android.content.Context context) {
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
		return prefs.getBoolean(PREF_MA_ALARM_ENABLED, true);
	}

	/**
	 * 获取均线预警全局开关的本地最后修改时间戳（毫秒）。无记录返回 0。
	 */
	public static long getMaAlarmEnabledTs(android.content.Context context) {
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
		return prefs.getLong(PREF_MA_ALARM_ENABLED_TS, 0L);
	}

	/**
	 * 写入均线预警全局开关 + 时间戳（用于跨端同步写入，不触发上传）。
	 */
	public static void setMaAlarmEnabled(android.content.Context context, boolean enabled, long ts) {
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
		prefs.edit()
			.putBoolean(PREF_MA_ALARM_ENABLED, enabled)
			.putLong(PREF_MA_ALARM_ENABLED_TS, ts)
			.apply();
	}

	/**
	 * 转义 JSON 字符串中的特殊字符
	 */
	private String escapeJsonString(String s) {
		if (s == null) return "";
		return s.replace("\\", "\\\\")
				.replace("\"", "\\\"")
				.replace("\n", "\\n")
				.replace("\r", "\\r")
				.replace("\t", "\\t");
	}
}
