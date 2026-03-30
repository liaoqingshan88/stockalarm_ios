package alarm.stock;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.ClipData;
import android.content.ContentResolver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.ResolveInfo;
import android.content.pm.Signature;
//import android.content.Intent.ShortcutIconResource;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.Parcelable;
import android.util.Log;
import android.webkit.URLUtil;
import android.widget.Toast;
import androidx.core.content.FileProvider;
import java.io.File;
//import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Locale;
//import java.util.Properties;
import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import java.text.SimpleDateFormat;
import java.util.Date;
//import java.util.jar.Attributes;

import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

// JSON解析支持
import org.json.JSONObject;
import org.json.JSONException;
import java.io.BufferedReader;
import java.io.InputStreamReader;


public class Update extends Activity {
	private static final String TAG = "DOWNLOADAPK";
	public static final int REQUEST_CODE_INSTALL_RESULT = 10087;

	private int version_inner_new;		//内部版本，用于比较
	private String version_ext_new;		//外部版本，用于显示
	private int version_inner_local;
	private String version_ext_local;		//外部版本，用于显示
	public ProgressDialog pBar;
	private String currentFilePath = "";
	private String fileEx = "";
	private String fileNa = "";
	private String downURL = "";
	private String VersionUri = "";
	private Context mContext;
	//add by idpiy 2013.6.25
	private Login m_login;
	private boolean request_update;
	
	// 新的API服务器地址
	// 后台页面见：`http://www.yanhexing.com:9000/`
	private static final String API_BASE_URL = "http://www.yanhexing.com:9000";
	// 备用地址：当域名被运营商/云厂商拦截（例如 ICP/备案拦截页）时兜底使用
	private static final String API_BASE_URL_FALLBACK = "http://47.107.180.174:9000";
	private static final String API_VERSION_URL = API_BASE_URL + "/api/version";
	private static final String API_DOWNLOAD_URL = API_BASE_URL + "/download/app.apk";
	private static final String API_DOWNLOAD_URL_FALLBACK = API_BASE_URL_FALLBACK + "/download/app.apk";

	// 记录本次成功请求所用的 baseUrl，便于拼接 downloadUrl
	private String apiBaseUrlUsed = API_BASE_URL;
	
	// JSON版本信息字段
	private String updateLog = "";
	private String updateTime = "";
	
	public Update( final Context context) {
		mContext = context;
		request_update = false;
		version_inner_new = 0;
		version_ext_new = "1.0";
		version_inner_local = GetLocalVerCode();
		version_ext_local = GetLocalVerName();
		
	}

	private void showStatusFor5s(final String message) {
		try {
			if (mContext instanceof AlarmActivity) {
				final AlarmActivity act = (AlarmActivity) mContext;
				act.runOnUiThread(new Runnable() {
					@Override
					public void run() {
						act.showStatusMessageFor5s(message);
					}
				});
			} else {
				Log.w(TAG, "STATUS: " + message);
			}
		} catch (Exception e) {
			Log.w(TAG, "showStatusFor5s failed: " + e.getMessage());
		}
	}

	public void SetLoginer(Login login)
	{
		m_login = login;
	}
	
	public void SetUrl(String urlapk, String urlver)
	{
		SimpleDateFormat df = new SimpleDateFormat("yyyyMMddHHmmss");
		String time = "?time=" + df.format(new Date());//主要是避开手机的缓存
		String user = "&user="+User.getDeviceid(mContext);
		String curVer = "&cur_ver="+version_inner_local;
		downURL = urlapk + time + user+curVer;
		VersionUri = urlver + time+ user+curVer;
		//version_inner_local = 0;
	}
	
	/**
	 * 设置新的API URL（使用JSON接口）
	 */
	public void SetApiUrl() {
		// 使用新的JSON API接口，始终使用真实服务器地址
		SimpleDateFormat df = new SimpleDateFormat("yyyyMMddHHmmss");
		String time = "?time=" + df.format(new Date());//主要是避开手机的缓存
		apiBaseUrlUsed = API_BASE_URL;
		VersionUri = API_VERSION_URL + time;
		// 下载URL会在解析JSON后设置
	}
	
	public boolean IsRequestUpdate()
	{
		return request_update;
	}
	
	public void check() {
		// 如果VersionUri为空，使用新的API接口
		boolean useNewApi = false;
		if (VersionUri == null || VersionUri.isEmpty()) {
			SetApiUrl();
			useNewApi = true;
		} else {
			// 检查是否已经使用了新的API
			useNewApi = VersionUri.contains(API_BASE_URL) ||
			            VersionUri.contains("/api/version");
		}
		
		// 如果使用新的JSON API，不需要登录，直接检查更新
		if (useNewApi) {
			//DeleteFolder("/sdcard/"+ Constants.path_install_child);
			DeleteFolder( Constants.path_install_child);
			CreateFolder( Constants.path_install);
			CreateFolder( Constants.path_install_child);
			
			GetVerFromServer(VersionUri);
			return;
		}
		
		// 旧的API需要登录
		if( m_login == null || m_login.IsLogin() )
		{
			// 解析Version网页，获取版本号
			//DeleteFolder("/sdcard/"+ Constants.path_install_child);
			DeleteFolder( Constants.path_install_child);
			CreateFolder( Constants.path_install);
			CreateFolder( Constants.path_install_child);
			
			GetVerFromServer(VersionUri);
		}
		else
		{
			request_update = true;
			if (m_login != null) {
				m_login.Do();
			} else {
				// 如果没有登录模块，直接检查更新
				GetVerFromServer(VersionUri);
			}
		}
	}


	public int GetLocalVerCode() {
		try {
			PackageManager manager = mContext.getPackageManager();
			PackageInfo info = manager.getPackageInfo(mContext.getPackageName(), 0);
			int ver = info.versionCode;
			//String version = info.versionName;
			//int ver = info.versionName;
			return ver;//this.getString(R.string.version_name) + version;
		}
		catch (Exception e) {
			e.printStackTrace();
			return 0;
			//rerurn R.string.version_name;
			//return "@string/app_name";//this.getString(R.string.can_not_find_version_name);
		}
	}
	public String GetLocalVerName() {
		try {
			PackageManager manager = mContext.getPackageManager();
			PackageInfo info = manager.getPackageInfo(mContext.getPackageName(), 0);
			String version = info.versionName;
			return version;//this.getString(R.string.version_name) + version;
		}
		catch (Exception e) {
			e.printStackTrace();
			//rerurn R.string.version_name;
			return "未知版本";//this.getString(R.string.can_not_find_version_name);
		}
	}

	boolean CreateFolder(String strFolder) {
		File file = new File(strFolder);

		if (!file.exists()) {
			if (file.mkdirs()) {
				return true;
			}
			else {
				return false;
			}
		}

		return true;
	}



	private void compareVer(boolean isRunManual) {
//当有最新版本的时候
		if(version_inner_new == 0) {
			if( isRunManual)
			{ showStatusFor5s("检测失败"); }
		}
		else if( version_inner_new > version_inner_local ) {
			// 构建更新消息
			String updateMessage = String.format("发现新版本[%s]，请更新！", version_ext_new);
			if (!updateLog.isEmpty()) {
				updateMessage += "\n\n更新说明：\n" + updateLog;
			}
			if (!updateTime.isEmpty()) {
				updateMessage += "\n\n更新时间：" + updateTime;
			}
			
			Dialog dialog = new AlertDialog.Builder(mContext).setTitle("系统更新")
			.setMessage(updateMessage) 

			// 设置确定按钮
			.setPositiveButton("确定"
			, new DialogInterface.OnClickListener() {
				@Override
				public void onClick(DialogInterface dialog,
				int which) {
					pBar = new ProgressDialog(mContext);
					pBar.setTitle("正在下载");
					pBar.setMessage("准备开始下载...");
					pBar.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
					pBar.setIndeterminate(false);
					pBar.setMax(100);
					pBar.setProgress(0);
					pBar.setCancelable(false);
					
					// 如果downURL为空，使用API下载地址
					if (downURL == null || downURL.isEmpty()) {
						downURL = API_DOWNLOAD_URL;
					}
					
					// 解析文件扩展名和文件名
					if (downURL.contains(".")) {
						String urlWithoutParams = downURL;
						if (downURL.contains("?")) {
							urlWithoutParams = downURL.substring(0, downURL.indexOf("?"));
						}
						fileEx = urlWithoutParams.substring(urlWithoutParams.lastIndexOf(".") + 1).toLowerCase();
						fileNa = urlWithoutParams.substring(urlWithoutParams.lastIndexOf("/") + 1, urlWithoutParams.lastIndexOf("."));
					} else {
						fileEx = "apk";
						fileNa = "app";
					}
					
					getFile(downURL);
				}
			}).setNegativeButton("取消",
			new DialogInterface.OnClickListener() {
				@Override
				public void onClick(DialogInterface dialog,
				int whichButton) {
// 点击"取消"按钮之后退出程序
				}
			}).create();// 创建
// 显示对话框
			dialog.show();
		}
		else {
			if(isRunManual)
			{ showStatusFor5s(String.format("已经是最新版本[%s]", version_ext_local)); }
		}
	}



	private void getFile(final String strPath) {
		pBar.show();
		// 直接使用直连下载，避免系统下载器在弱网下的状态不一致问题
		startLegacyDirectDownload(strPath);
	}

	private String formatBytes(long bytes) {
		if (bytes < 1024) return bytes + "B";
		if (bytes < 1024 * 1024) return String.format("%.1fKB", bytes / 1024.0);
		return String.format("%.1fMB", bytes / 1024.0 / 1024.0);
	}

	private void startLegacyDirectDownload(final String strPath) {
		try {
			currentFilePath = strPath;
			if (pBar != null && pBar.isShowing()) {
				pBar.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
				pBar.setIndeterminate(false);
				pBar.setMax(100);
				pBar.setProgress(0);
				pBar.setMessage("系统下载不可用，切换直连下载...");
			}
			Runnable r = new Runnable() {
				@Override
				public void run() {
					try {
						getDataSource(strPath);
					} catch (Exception e) {
						Log.e(TAG, "直连下载失败: " + e.getMessage(), e);
						showDownloadError("下载失败\n" + e.getClass().getSimpleName() + ": " + e.getMessage());
					}
				}
			};
			new Thread(r).start();
		} catch (Exception e) {
			Log.e(TAG, "启动直连下载失败: " + e.getMessage(), e);
			showDownloadError("启动直连下载失败\n" + e.getClass().getSimpleName() + ": " + e.getMessage());
		}
	}



	/*取得远程文件*/
	private void getDataSource(String strPath) throws Exception {
		if (!URLUtil.isNetworkUrl(strPath)) {
			throw new IllegalArgumentException("下载地址无效: " + strPath);
		}

		File downloadDir = getApkDownloadDir();
		File myTempFile = new File(downloadDir, Constants.install_name);
		File parent = myTempFile.getParentFile();
		if (parent != null && !parent.exists()) {
			parent.mkdirs();
		}
		if (myTempFile.exists() && !myTempFile.delete()) {
			Log.w(TAG, "删除旧安装包失败: " + myTempFile.getAbsolutePath());
		}

		final int maxAttempts = 3;
		Exception lastError = null;
		for (int attempt = 1; attempt <= maxAttempts; attempt++) {
			long totalBytes = fetchRemoteFileSize(strPath);
			HttpURLConnection conn = null;
			InputStream is = null;
			FileOutputStream fos = null;
			byte[] buf = new byte[8192];
			long downloadedBytes = 0L;
			try {
				if (attempt > 1 && myTempFile.exists()) {
					myTempFile.delete();
				}
				Log.i(TAG, "UPDATE_DOWNLOAD_START: attempt=" + attempt + "/" + maxAttempts + ", url=" + strPath);
				URL url = new URL(strPath);
				conn = (HttpURLConnection) url.openConnection();
				conn.setConnectTimeout(25000);
				conn.setReadTimeout(180000);
				conn.setInstanceFollowRedirects(true);
				conn.setRequestMethod("GET");
				conn.setRequestProperty("Connection", "close");
				conn.setRequestProperty("Accept-Encoding", "identity");

				int code = conn.getResponseCode();
				if (code != HttpURLConnection.HTTP_OK) {
					throw new RuntimeException("下载响应异常，HTTP状态码: " + code);
				}

				if (totalBytes <= 0) {
					long contentLength = conn.getContentLengthLong();
					if (contentLength > 0) {
						totalBytes = contentLength;
					}
				}

				is = conn.getInputStream();
				if (is == null) {
					throw new RuntimeException("下载流为空");
				}

				fos = new FileOutputStream(myTempFile, false);
				int numread;
				while ((numread = is.read(buf)) > 0) {
					fos.write(buf, 0, numread);
					downloadedBytes += numread;
					updateDirectDownloadProgress(downloadedBytes, totalBytes);
				}
				fos.flush();

				if (totalBytes > 0 && myTempFile.length() != totalBytes) {
					throw new RuntimeException("文件大小不匹配: " + myTempFile.length() + "/" + totalBytes);
				}

				Log.i(TAG, "UPDATE_DOWNLOAD_SUCCESS: size=" + myTempFile.length() + ", file=" + myTempFile.getAbsolutePath());
				final File finalApk = myTempFile;
				runOnMainThread(new Runnable() {
					@Override
					public void run() {
						openFile(finalApk);
					}
				});
				return;
			} catch (Exception e) {
				lastError = e;
				Log.e(TAG, "UPDATE_DOWNLOAD_ATTEMPT_FAIL: attempt=" + attempt + ", err=" + e.getClass().getSimpleName() + ": " + e.getMessage(), e);
				if (attempt < maxAttempts) {
					try {
						Thread.sleep(1200L * attempt);
					} catch (InterruptedException ignore) {
					}
				}
			} finally {
				try { if (fos != null) fos.close(); } catch (Exception ignore) {}
				try { if (is != null) is.close(); } catch (Exception ignore) {}
				if (conn != null) {
					conn.disconnect();
				}
			}
		}
		if (lastError != null) {
			throw lastError;
		}
		throw new RuntimeException("下载失败：未知错误");
	}

	private long fetchRemoteFileSize(String strPath) throws Exception {
		HttpURLConnection conn = null;
		try {
			URL url = new URL(strPath);
			conn = (HttpURLConnection) url.openConnection();
			conn.setConnectTimeout(20000);
			conn.setReadTimeout(20000);
			conn.setInstanceFollowRedirects(true);
			conn.setRequestMethod("HEAD");
			conn.setRequestProperty("Connection", "close");
			int code = conn.getResponseCode();
			if (code >= 200 && code < 300) {
				long len = conn.getContentLengthLong();
				if (len > 0) {
					return len;
				}
			}
		} catch (Exception e) {
			Log.w(TAG, "HEAD获取文件大小失败: " + e.getMessage());
		} finally {
			if (conn != null) conn.disconnect();
		}
		Log.w(TAG, "获取文件大小失败，按未知大小下载");
		return -1L;
	}

	private void updateDirectDownloadProgress(final long downloadedBytes, final long totalBytes) {
		runOnMainThread(new Runnable() {
			@Override
			public void run() {
				if (pBar == null || !pBar.isShowing()) {
					return;
				}
				int progress = 0;
				if (totalBytes > 0) {
					progress = (int) (downloadedBytes * 100 / totalBytes);
				}
				pBar.setProgress(progress);
				pBar.setMessage("直连下载中\n已下载 " + formatBytes(downloadedBytes) + " / " + (totalBytes > 0 ? formatBytes(totalBytes) : "未知大小"));
			}
		});
	}

	private File getApkDownloadDir() {
		try {
			File dir = mContext.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
			if (dir != null) {
				return dir;
			}
		} catch (Exception ignore) {
		}
		return mContext.getFilesDir();
	}

	private void showDownloadError(final String message) {
		Log.e(TAG, "UPDATE_DOWNLOAD_ERROR: " + message);
		Log.e(TAG, "UPDATE_ERROR_DETAIL: pkg=" + mContext.getPackageName()
				+ ", thread=" + Thread.currentThread().getName()
				+ ", downURL=" + downURL
				+ ", currentFilePath=" + currentFilePath);
		appendUpdateErrorLog(message);
		runOnMainThread(new Runnable() {
			@Override
			public void run() {
				try {
					if (pBar != null && pBar.isShowing()) {
						pBar.cancel();
					}
				} catch (Exception ignore) {
				}
				String firstLine = message;
				if (message != null && message.contains("\n")) {
					firstLine = message.substring(0, message.indexOf("\n"));
				}
				showStatusFor5s(firstLine);
			}
		});
	}

	private String appendUpdateErrorLog(String message) {
		File logFile;
		try {
			File dir = getApkDownloadDir();
			if (!dir.exists()) {
				dir.mkdirs();
			}
			logFile = new File(dir, "update_error.log");
			FileWriter writer = new FileWriter(logFile, true);
			try {
				String time = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date());
				writer.write(time + " UPDATE_DOWNLOAD_ERROR: " + message + "\n");
			} finally {
				writer.close();
			}
			return logFile.getAbsolutePath();
		} catch (Exception e) {
			Log.e(TAG, "写入错误日志失败: " + e.getMessage(), e);
			return "写入失败";
		}
	}

	private void runOnMainThread(Runnable runnable) {
		if (Looper.myLooper() == Looper.getMainLooper()) {
			runnable.run();
		} else {
			new Handler(Looper.getMainLooper()).post(runnable);
		}
	}


	//删除存储卡内的临时文件
	private void delFile(String strFileName) {
		File myFile = new File(strFileName);

		if(myFile.exists()) {
			myFile.delete();
		}
	}
	/* 在手机上打开文件 */

	private void openFile(File f) {
		pBar.cancel();
		if (f == null || !f.exists() || f.length() <= 0) {
			showDownloadError("安装失败：安装包不存在或大小为0");
			return;
		}
		Log.i(TAG, "准备安装APK: " + f.getAbsolutePath() + ", size=" + f.length());

		// 在拉起系统安装器前预检签名，避免用户走完整安装流程后才看到签名冲突
		if (isUpdateSignatureIncompatible(f)) {
			new AlertDialog.Builder(mContext)
					.setTitle("安装失败")
					.setMessage("签名不一致，无法覆盖安装。\n请先卸载旧版，再安装新包。")
					.setPositiveButton("确定", null)
					.show();
			return;
		}
		
		// Android 8.0+ 需要检查安装权限
		if (android.os.Build.VERSION.SDK_INT >= 26) {
			if (!mContext.getPackageManager().canRequestPackageInstalls()) {
				// 没有安装权限，提示用户去设置
				AlertDialog.Builder builder = new AlertDialog.Builder(mContext);
				builder.setTitle("需要安装权限");
				builder.setMessage("应用需要安装权限才能更新，请在设置中允许安装未知来源的应用。");
				builder.setPositiveButton("去设置", new DialogInterface.OnClickListener() {
					@Override
					public void onClick(DialogInterface dialog, int which) {
						try {
							Intent intent = new Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES);
							intent.setData(Uri.parse("package:" + mContext.getPackageName()));
							if (mContext instanceof Activity) {
								((Activity) mContext).startActivityForResult(intent, 10086);
							} else {
								intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
								mContext.startActivity(intent);
							}
						} catch (Exception e) {
							Log.e(TAG, "打开设置失败: " + e.getMessage(), e);
							showStatusFor5s("请在设置中允许安装未知来源应用");
						}
					}
				});
				builder.setNegativeButton("取消", null);
				builder.show();
				return;
			}
		}
		
		Intent intent = new Intent(Intent.ACTION_INSTALL_PACKAGE);
		intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
		
		/* 调用getMIMEType()来取得MimeType */
		String type = getMIMEType(f);
		
		Uri apkUri;
		// Android 7.0+ 必须使用content://，禁止file://
		if (android.os.Build.VERSION.SDK_INT >= 24) {
			try {
				apkUri = createFileProviderUri(f);
			} catch (Exception e) {
				Log.e(TAG, "创建FileProvider URI失败: " + e.getMessage(), e);
				showDownloadError("无法打开安装程序: FileProvider异常: " + e.getMessage());
				return;
			}
		} else {
			apkUri = Uri.fromFile(f);
		}
		
		/* 设定intent的file与MimeType */
		intent.setDataAndType(apkUri, type);
		intent.putExtra(Intent.EXTRA_NOT_UNKNOWN_SOURCE, true);
		intent.putExtra(Intent.EXTRA_RETURN_RESULT, true);
		intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
		intent.setClipData(ClipData.newRawUri("", apkUri));
		
		// 显式授予安装器读取权限，避免部分机型读取FileProvider失败
		try {
			PackageManager pm = mContext.getPackageManager();
			java.util.List<ResolveInfo> resolveInfos = pm.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY);
			for (ResolveInfo resolveInfo : resolveInfos) {
				String packageName = resolveInfo.activityInfo.packageName;
				mContext.grantUriPermission(packageName, apkUri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
			}
		} catch (Exception e) {
			Log.w(TAG, "授予安装器URI权限失败: " + e.getMessage());
		}
		
		try {
			if (mContext instanceof Activity) {
				((Activity) mContext).startActivityForResult(intent, REQUEST_CODE_INSTALL_RESULT);
			} else {
				mContext.startActivity(intent);
				// 非Activity上下文无法回调安装结果，给出提示避免用户误解
				showStatusFor5s("已打开安装界面");
			}
		} catch (Exception e) {
			Log.e(TAG, "打开安装程序失败: " + e.getMessage(), e);
			showDownloadError("无法打开安装程序: " + e.getClass().getSimpleName() + ": " + e.getMessage());
		}
	}

	public boolean handleInstallActivityResult(int requestCode, int resultCode, Intent data) {
		if (requestCode != REQUEST_CODE_INSTALL_RESULT) {
			return false;
		}

		String title;
		String message;
		if (resultCode == Activity.RESULT_OK) {
			title = "安装成功";
			message = "新版本安装成功。";
			Log.i(TAG, "INSTALL_RESULT: success");
		} else {
			int installStatus = Integer.MIN_VALUE;
			String installMsg = null;
			if (data != null) {
				installStatus = data.getIntExtra("android.intent.extra.INSTALL_RESULT", Integer.MIN_VALUE);
				installMsg = data.getStringExtra("android.intent.extra.INSTALL_RESULT_MESSAGE");
			}
			// 部分系统会把安装器以新任务启动，导致当前Activity立即收到 RESULT_CANCELED，
			// 但真实安装仍在系统安装器中继续。此处避免误判为失败。
			if (resultCode == Activity.RESULT_CANCELED
					&& installStatus == Integer.MIN_VALUE) {
				title = "安装提示";
				message = "系统安装界面已打开，请以系统安装器页面的最终结果为准。";
				Log.i(TAG, "INSTALL_RESULT: canceled early, treat as installer handoff");
			} else {
				title = "安装失败";
				message = buildInstallFailMessage(resultCode, installStatus, installMsg);
			}
			Log.e(TAG, "INSTALL_RESULT: failed, resultCode=" + resultCode
					+ ", installStatus=" + installStatus + ", msg=" + installMsg);
		}

		final String finalTitle = title;
		final String finalMessage = message;
		runOnMainThread(new Runnable() {
			@Override
			public void run() {
				new AlertDialog.Builder(mContext)
						.setTitle(finalTitle)
						.setMessage(finalMessage)
						.setPositiveButton("确定", null)
						.show();
			}
		});
		return true;
	}

	private String buildInstallFailMessage(int resultCode, int installStatus, String installMsg) {
		// 使用固定错误码，避免低compileSdk下找不到PackageManager常量
		final int INSTALL_FAILED_UPDATE_INCOMPATIBLE = -7;
		final int INSTALL_FAILED_VERSION_DOWNGRADE = -25;
		final int INSTALL_PARSE_FAILED_INCONSISTENT_CERTIFICATES = -104;
		final int INSTALL_FAILED_INVALID_APK = -2;

		if (installStatus == INSTALL_FAILED_UPDATE_INCOMPATIBLE) {
			return "签名不一致，无法覆盖安装。\n请先卸载旧版，再安装新包。";
		}
		if (installStatus == INSTALL_FAILED_VERSION_DOWNGRADE) {
			return "安装包版本低于当前已安装版本，系统拒绝降级安装。";
		}
		if (installStatus == INSTALL_PARSE_FAILED_INCONSISTENT_CERTIFICATES) {
			return "安装包签名校验失败。";
		}
		if (installStatus == INSTALL_FAILED_INVALID_APK) {
			return "安装包无效，请重新下载。";
		}
		if (resultCode == Activity.RESULT_CANCELED) {
			return "已取消安装。";
		}
		StringBuilder sb = new StringBuilder("安装失败");
		if (installMsg != null && installMsg.length() > 0) {
			sb.append("：").append(installMsg);
		} else if (installStatus != Integer.MIN_VALUE) {
			sb.append("（错误码: ").append(installStatus).append("）");
		} else {
			sb.append("。");
		}
		return sb.toString();
	}

	private boolean isUpdateSignatureIncompatible(File apkFile) {
		try {
			PackageManager pm = mContext.getPackageManager();
			PackageInfo installed = pm.getPackageInfo(mContext.getPackageName(), PackageManager.GET_SIGNATURES);
			PackageInfo archive = pm.getPackageArchiveInfo(apkFile.getAbsolutePath(), PackageManager.GET_SIGNATURES);
			if (installed == null || archive == null) {
				return false;
			}
			if (installed.signatures == null || archive.signatures == null) {
				return false;
			}
			HashSet<String> installedSet = new HashSet<String>();
			for (Signature s : installed.signatures) {
				if (s != null) {
					installedSet.add(s.toCharsString());
				}
			}
			HashSet<String> archiveSet = new HashSet<String>();
			for (Signature s : archive.signatures) {
				if (s != null) {
					archiveSet.add(s.toCharsString());
				}
			}
			boolean mismatch = !installedSet.equals(archiveSet);
			if (mismatch) {
				Log.e(TAG, "INSTALL_PRECHECK_FAIL: signature mismatch, installed="
						+ installedSet.size() + ", archive=" + archiveSet.size());
			}
			return mismatch;
		} catch (Exception e) {
			Log.w(TAG, "INSTALL_PRECHECK_WARN: signature check failed: " + e.getMessage());
			return false;
		}
	}

	private Uri createFileProviderUri(File file) throws Exception {
		String authority = mContext.getPackageName() + ".fileprovider";
		return FileProvider.getUriForFile(mContext, authority, file);
	}
	/* 判断文件MimeType的method */
	private String getMIMEType(File f) {
		String type = "";
		String fName = f.getName();
		/* 取得扩展名 */
		String end = fName.substring(fName.lastIndexOf(".") + 1, fName.length()).toLowerCase();

		/* 按扩展名的类型决定MimeType */
		if(end.equals("m4a")
		   || end.equals("mp3")
		   || end.equals("mid")
		   || end.equals("xmf")
		   || end.equals("ogg")
		   || end.equals("wav")) {
			type = "audio";
		}
		else if(end.equals("3gp") || end.equals("mp4")) {
			type = "video";
		}
		else if(end.equals("jpg")
				|| end.equals("gif")
				|| end.equals("png")
				|| end.equals("jpeg")
				|| end.equals("bmp")) {
			type = "image";
		}
		else if(end.equals("apk")) {
			/* android.permission.INSTALL_PACKAGES */
			type = "application/vnd.android.package-archive";
		}
		else {
			type = "*";
		}

		/*如果无法直接打开，就跳出软件清单给使用者选择 */
		if(!end.equals("apk")) {
			type += "/*";
		}

		return type;
	}

	private void GetVerFromServer(String resourceUrl) {
		// 如果URL包含新的API地址，使用JSON解析
		if (resourceUrl != null && (resourceUrl.contains("/api/version") || 
			resourceUrl.contains(API_BASE_URL))) {
			GetNewVerJson gv = new GetNewVerJson();
			gv.execute(resourceUrl);
		} else {
			// 兼容旧的XML解析方式
			GetNewVer gv = new GetNewVer();
			gv.execute(resourceUrl);
		}
	}

	class VesionXMLContent extends DefaultHandler {
		private String version_inner; // 获取内部版本号
		private String version_ext; // 获取外部版本号
		private String preTag;// 前一个元素名称

		public String getVersionInner() {
			return version_inner;
		}
		public String getVersionExt() {
			return version_ext;
		}

		@Override
		public void startDocument() throws SAXException {
			version_inner =new String();
			version_ext =new String();

		}
		@Override
		public void characters(char[] ch, int start, int length)
		throws SAXException {
			String data = new String(ch, start, length);

			if ("ver_inner".equals(preTag)) {
				version_inner = data;
			}
			if ("ver_ext".equals(preTag)) {
				version_ext = data;
			}

		}

		
		@Override
		public void startElement(String uri, String localName, String qName, Attributes attributes)
		throws SAXException {
			preTag = localName;
		}

		@Override
		public void endElement(String uri, String localName, String qName)
		throws SAXException {
			preTag = null;
		}
	}
//==========================================================================
// GetNewVer
//==========================================================================
	class GetNewVer extends AsyncTask<String, Integer, String> {


		@Override
		protected String doInBackground(String... urlVer) {
			String ver_inner = null;
			URL url = null;

			try {
				url = new URL(urlVer[0]);
			}
			catch (MalformedURLException e) {
				e.printStackTrace();
			}

			InputSource inStream = null;

			try {
				inStream = new InputSource(url.openStream());
				inStream.setEncoding("UTF-8");
				//ver_inner = SAXGetVersionService.readRssXml(is);
				SAXParserFactory spf = SAXParserFactory.newInstance();
				SAXParser saxParser = spf.newSAXParser(); // 创建解析器
				VesionXMLContent handler = new VesionXMLContent();
				saxParser.parse(inStream, handler);
				ver_inner = handler.getVersionInner();
				version_ext_new = handler.getVersionExt();
			}
			catch (Exception e) {
				e.printStackTrace();
				ver_inner = "0";
			}

			return ver_inner;
		}

		@Override
		protected void onCancelled() {
			super.onCancelled();
		}

		@Override
		protected void onPostExecute(String result) {
			version_inner_new = Integer.parseInt(result);
			
			String now = StockTime.GetTime();

			if( now.indexOf(Constants.time_of_auto_update) != -1) {
				compareVer(false);//自动升级
			}
			else {
				compareVer(true);//手工升级
			}
		}
	}
	
	//==========================================================================
	// GetNewVerJson - 使用JSON API获取版本信息
	//==========================================================================
	class GetNewVerJson extends AsyncTask<String, Integer, JSONObject> {

		@Override
		protected JSONObject doInBackground(String... urlVer) {
			JSONObject jsonObject = null;
			Exception lastError = null;
			final int maxRetry = 3;
			String urlString = urlVer[0];
			for (int attempt = 1; attempt <= maxRetry; attempt++) {
				BufferedReader reader = null;
				InputStream in = null;
				HttpURLConnection conn = null;
				try {
					Log.d(TAG, "正在检查版本更新，URL: " + urlString + ", attempt=" + attempt + "/" + maxRetry);
					URL url = new URL(urlString);
					conn = (HttpURLConnection) url.openConnection();
					conn.setInstanceFollowRedirects(true);
					conn.setUseCaches(false);
					conn.setConnectTimeout(20000); // 网络抖动场景提高超时
					conn.setReadTimeout(30000);
					conn.setRequestMethod("GET");
					// 一些移动网络/代理对 keep-alive + gzip 较敏感，容易 reset；这里尽量用“最朴素”的连接方式
					conn.setRequestProperty("Connection", "close");
					conn.setRequestProperty("Accept", "application/json,text/plain,*/*");
					conn.setRequestProperty("Accept-Encoding", "identity");
					conn.setRequestProperty("User-Agent", "stock-alarm/" + version_inner_local + " (Android)");
					conn.connect();
					int code = conn.getResponseCode();
					if (code != HttpURLConnection.HTTP_OK) {
						// 域名在部分网络环境可能被拦截，表现为 403 / reset 等。
						// 一旦遇到非200且当前用的是域名，直接切到备用IP继续尝试。
						if (urlString != null
								&& urlString.contains(API_BASE_URL)
								&& !urlString.contains(API_BASE_URL_FALLBACK)) {
							urlString = urlString.replace(API_BASE_URL, API_BASE_URL_FALLBACK);
							apiBaseUrlUsed = API_BASE_URL_FALLBACK;
							showStatusFor5s("更新域名访问异常(HTTP " + code + ")，已切换备用地址");
							continue;
						}
						throw new IOException("HTTP " + code);
					}
					
					// 读取JSON响应
					in = conn.getInputStream();
					reader = new BufferedReader(new InputStreamReader(in, "UTF-8"));
					StringBuilder response = new StringBuilder();
					String line;
					while ((line = reader.readLine()) != null) {
						response.append(line);
					}
					
					String responseStr = response.toString();
					Log.d(TAG, "服务器响应: " + responseStr);

					// 某些网络环境下，域名会被返回“备案/ICP拦截页”（HTML），这会导致 JSON 解析失败。
					// 检测到这种情况，自动切换到备用IP继续尝试。
					String respLower = responseStr == null ? "" : responseStr.toLowerCase(Locale.ROOT);
					boolean looksLikeHtml = respLower.contains("<html") || respLower.contains("<!doctype html");
					boolean looksLikeIcpBlock = respLower.contains("non-compliance icp filing")
							|| respLower.contains("beian-block")
							|| respLower.contains("aliyun.com/beian");
					if (looksLikeHtml || looksLikeIcpBlock) {
						if (urlString != null && urlString.contains(API_BASE_URL) && !urlString.contains(API_BASE_URL_FALLBACK)) {
							urlString = urlString.replace(API_BASE_URL, API_BASE_URL_FALLBACK);
							apiBaseUrlUsed = API_BASE_URL_FALLBACK;
							showStatusFor5s("更新域名访问异常，已切换备用地址");
							// 进入下一次 attempt 重新请求
							continue;
						}
						throw new JSONException("Response is not JSON (HTML/ICP block)");
					}
					
					// 解析JSON
					if (responseStr != null && !responseStr.trim().isEmpty()) {
						jsonObject = new JSONObject(responseStr);
						// 成功解析后，记录本次实际使用的 baseUrl
						if (urlString != null && urlString.contains(API_BASE_URL_FALLBACK)) {
							apiBaseUrlUsed = API_BASE_URL_FALLBACK;
						} else {
							apiBaseUrlUsed = API_BASE_URL;
						}
						return jsonObject;
					} else {
						throw new RuntimeException("服务器返回空响应");
					}
				}
				catch (java.net.UnknownHostException e) {
					lastError = e;
					Log.e(TAG, "无法解析服务器地址, attempt=" + attempt + ": " + e.getMessage(), e);
				}
				catch (java.net.SocketTimeoutException e) {
					lastError = e;
					Log.e(TAG, "检查更新超时, attempt=" + attempt + ": " + e.getMessage(), e);
				}
				catch (java.net.ConnectException e) {
					lastError = e;
					Log.e(TAG, "无法连接到服务器, attempt=" + attempt + ": " + e.getMessage(), e);
				}
				catch (java.net.SocketException e) {
					lastError = e;
					// 常见：Connection reset / Broken pipe / Network is unreachable 等
					Log.e(TAG, "网络连接被重置, attempt=" + attempt + ": " + e.getMessage(), e);
					// 域名被拦截/重置时，切换备用IP重试
					if (urlString != null
							&& urlString.contains(API_BASE_URL)
							&& !urlString.contains(API_BASE_URL_FALLBACK)) {
						urlString = urlString.replace(API_BASE_URL, API_BASE_URL_FALLBACK);
						apiBaseUrlUsed = API_BASE_URL_FALLBACK;
						showStatusFor5s("更新域名连接异常，已切换备用地址");
					}
				}
				catch (java.io.IOException e) {
					lastError = e;
					Log.e(TAG, "网络连接错误, attempt=" + attempt + ": " + e.getMessage(), e);
					// 同上：域名侧非预期错误，切换备用IP
					if (urlString != null
							&& urlString.contains(API_BASE_URL)
							&& !urlString.contains(API_BASE_URL_FALLBACK)) {
						urlString = urlString.replace(API_BASE_URL, API_BASE_URL_FALLBACK);
						apiBaseUrlUsed = API_BASE_URL_FALLBACK;
						showStatusFor5s("更新域名网络异常，已切换备用地址");
					}
				}
				catch (JSONException e) {
					lastError = e;
					Log.e(TAG, "JSON解析错误, attempt=" + attempt + ": " + e.getMessage(), e);
				}
				catch (Exception e) {
					lastError = e;
					Log.e(TAG, "获取版本信息失败, attempt=" + attempt + ": " + e.getMessage(), e);
				}
				finally {
					try {
						if (reader != null) {
							reader.close();
						}
					} catch (Exception ignore) {}
					try {
						if (in != null) {
							in.close();
						}
					} catch (Exception ignore) {}
					try {
						if (conn != null) {
							conn.disconnect();
						}
					} catch (Exception ignore) {}
				}
				
				if (attempt < maxRetry) {
					try {
						Thread.sleep(1200L * attempt);
					} catch (InterruptedException ignore) {
					}
				}
			}

			if (lastError != null) {
				Log.e(TAG, "检查更新最终失败: " + lastError.getClass().getSimpleName() + ": " + lastError.getMessage(), lastError);
			}

			return jsonObject;
		}

		@Override
		protected void onCancelled() {
			super.onCancelled();
		}

		@Override
		protected void onPostExecute(JSONObject result) {
			boolean isRunManual = true;
			String now = StockTime.GetTime();
			if( now.indexOf(Constants.time_of_auto_update) != -1) {
				isRunManual = false;
			}
			
			if (result != null) {
				try {
					// 解析JSON字段
					version_ext_new = result.optString("version", "1.0.0");
					version_inner_new = result.optInt("versionCode", 0);
					updateLog = result.optString("updateLog", "");
					updateTime = result.optString("updateTime", "");
					
					// 获取下载URL
					String downloadUrlPath = result.optString("downloadUrl", "/download/app.apk");
					
					if (downloadUrlPath.startsWith("/")) {
						// 相对路径，拼接完整URL（使用真实服务器地址）
						String base = (apiBaseUrlUsed != null && apiBaseUrlUsed.length() > 0) ? apiBaseUrlUsed : API_BASE_URL;
						downURL = base + downloadUrlPath;
					} else if (downloadUrlPath.startsWith("http://") || downloadUrlPath.startsWith("https://")) {
						// 绝对路径
						downURL = downloadUrlPath;
					} else {
						// 默认使用API下载地址
						downURL = (API_BASE_URL_FALLBACK.equals(apiBaseUrlUsed)) ? API_DOWNLOAD_URL_FALLBACK : API_DOWNLOAD_URL;
					}
					
					// 添加时间戳避免缓存
					SimpleDateFormat df = new SimpleDateFormat("yyyyMMddHHmmss");
					String time = "?time=" + df.format(new Date());
					if (downURL.contains("?")) {
						downURL += "&t=" + df.format(new Date());
					} else {
						downURL += time;
					}
					
					Log.d(TAG, "版本信息: version=" + version_ext_new + ", versionCode=" + version_inner_new);
					Log.d(TAG, "下载地址: " + downURL);
					
					// 检查版本号是否有效
					if (version_inner_new <= 0) {
						if (isRunManual) {
							showStatusFor5s("服务器返回的版本号无效");
						}
						return;
					}
				}
				catch (Exception e) {
					Log.e(TAG, "解析版本信息失败: " + e.getMessage(), e);
					version_inner_new = 0;
					if (isRunManual) {
						showStatusFor5s("解析版本信息失败");
					}
					return;
				}
			} else {
				version_inner_new = 0;
				if (isRunManual) {
					showStatusFor5s("检查更新失败，无法连接服务器");
				}
				return;
			}
			
			compareVer(isRunManual);
		}
	}


	private boolean DeleteFolder(String path) {
		boolean success = false;

		try {
			ArrayList<File> images = new ArrayList<File>();
			getFiles(images, path); //

			for (int i = 1; i < images.size(); i++) {
				File latestSavedImage = images.get(i);

				if( latestSavedImage.exists()) {
					success = latestSavedImage.delete();
				}
			}
		}
		catch (Exception e) {
			e.printStackTrace();
			showStatusFor5s("删除临时文件失败");
		}

		return success;
	}

	private void getFiles(ArrayList<File> fileList, String path) {
		File[] allFiles = new File(path).listFiles();

		if( allFiles == null ) {
			return;
		}

		for (int i = 0; i < allFiles.length; i++) {
			File file = allFiles[i];

			if (file.isFile()) {
				fileList.add(file);
			}
			else if (!file.getAbsolutePath().contains(".thumnail")) {
				getFiles(fileList, file.getAbsolutePath());
			}
		}
	}

/*	
	 public void addShortCut(){

	        Intent shortcut = new Intent("com.android.launcher.action.INSTALL_SHORTCUT");

	        // 设置属性

	        shortcut.putExtra(Intent.EXTRA_SHORTCUT_NAME, getResources().getString(R.string.app_name));

	        ShortcutIconResource iconRes = Intent.ShortcutIconResource.fromContext(mContext, R.drawable.ic_launcher);

	        shortcut.putExtra(Intent.EXTRA_SHORTCUT_ICON,iconRes);

	 

	        // 是否允许重复创建

	        shortcut.putExtra("duplicate", false);

	        Intent intent = new Intent(Intent.ACTION_MAIN);

	        intent.setFlags(Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);

	        intent.addFlags(Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY);

	        intent.addCategory(Intent.CATEGORY_LAUNCHER);

	        intent.setClass(mContext, mContext.getClass());

	        // 设置启动程序

	        System.out.println("createIcon");

	        shortcut.putExtra(Intent.EXTRA_SHORTCUT_INTENT, intent);

	        mContext.sendBroadcast(shortcut);

	    }
*/
	   public  boolean  IfaddShortCut(){

		// TODO Auto-generated method stub
	         boolean isInstallShortcut = false;
	         final ContentResolver cr = mContext.getContentResolver();
	         final String AUTHORITY = "com.android.launcher.settings";
	         final String AUTHORITY2 = "com.android.launcher2.settings";
			 
	         final Uri CONTENT_URI = Uri.parse("content://" + AUTHORITY +  "/favorites?notify=true");
	         final Uri CONTENT_URI2 = Uri.parse("content://" + AUTHORITY2 + "/favorites?notify=true");

	 		//for before android.2.1
	        Cursor c = cr.query(
	                 CONTENT_URI,
	                 new String[] { "title", "iconResource" },
	                 "title=?",
	                 new String[] { mContext.getResources().getString(
	                         R.string.app_name) }, null);
	         if (c != null && c.getCount() > 0) {
	             isInstallShortcut = true;
	         }

			//for after android 2.2
	        Cursor c2 = cr.query(
	                 CONTENT_URI2,
	                 new String[] { "title", "iconResource" },
	                 "title=?",
	                 new String[] { mContext.getResources().getString(
	                         R.string.app_name) }, null);
	         if (c2 != null && c2.getCount() > 0) {
	             isInstallShortcut = true;
	         }

			 
	         return isInstallShortcut;
	     }


	   

	   public void createSystemSwitcherShortCut() {  
	        final Intent addIntent = new Intent( "com.android.launcher.action.INSTALL_SHORTCUT");  
	        final Parcelable icon = Intent.ShortcutIconResource.fromContext(  
	        		mContext, R.drawable.ic_launcher); // 获取快捷键的图标  
	        addIntent.putExtra("duplicate", false);  
	        
	        final Intent myIntent = new Intent(mContext, mContext.getClass());  
	        //myIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);  
	        //final Intent myIntent = new Intent(Intent.ACTION_MAIN);  
	        myIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK| Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);//关键的一步，设置启动模式为打开上一个activity

	        addIntent.putExtra(Intent.EXTRA_SHORTCUT_NAME, mContext.getString(R.string.app_name));// 快捷方式的标题  
	        addIntent.putExtra(Intent.EXTRA_SHORTCUT_ICON_RESOURCE, icon);// 快捷方式的图标  
	        addIntent.putExtra(Intent.EXTRA_SHORTCUT_INTENT, myIntent);// 快捷方式的动作  
	        mContext.sendBroadcast(addIntent);  
	    }  
	
}

