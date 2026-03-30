/*
 * Copyright zh.weir.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package alarm.stock;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringReader;
//import java.io.Reader;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpException;
import org.apache.commons.httpclient.HttpMethod;
import org.apache.commons.httpclient.HttpStatus;
import org.apache.commons.httpclient.methods.GetMethod;

import alarm.stock.SinaStockInfo.ParseStockInfoException;

//import android.R.string;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.text.TextUtils;
//import android.util.Log;

/**
 * 新浪数据获取客户端，提供个股行情查询、大盘指数查询、个股分时图、K线图等基本数据查询服务。
 *
 * @author <a href="http://www.blogjava.net/zh-weir/" target="_blank">zh.weir的技术博客</a>
 * </br>
 * </br>
 * <a href="http://weibo.com/1779382071?s=6uyXnP" target="_blank"><img border="0" src="http://service.t.sina.com.cn/widget/qmd/1779382071/9c2d28b9/1.png"/></a>
 *
 */
public final class SinaStockClient {

	// 使用腾讯股票API（HTTP版本）
	private final static String STOCK_URL = "http://qt.gtimg.cn/q=";

	private final static String STOCK_MINITE_URL = "http://image.sinajs.cn/newchart/min/n/";
	private final static String STOCK_DAILY_URL = "http://image.sinajs.cn/newchart/daily/n/";
	private final static String STOCK_WEEKLY_URL = "http://image.sinajs.cn/newchart/weekly/n/";
	private final static String STOCK_MONTHLY_URL = "http://image.sinajs.cn/newchart/monthly/n/";

	public final static int IMAGE_TYPE_MINITE = 0x85;
	public final static int IMAGE_TYPE_DAILY = 0x86;
	public final static int IMAGE_TYPE_WEEKLY = 0x87;
	public final static int IMAGE_TYPE_MONTHLY = 0x88;

	private final static int CONNECTION_TIMEOUT = 5000;
	private final static int SO_TIMEOUT = 30000;

	private HttpClient mHttpClient;

	private static SinaStockClient mInstance;

	private SinaStockClient() {
		mHttpClient = new HttpClient();
		mHttpClient.getHttpConnectionManager().getParams().setConnectionTimeout(CONNECTION_TIMEOUT);
		mHttpClient.getHttpConnectionManager().getParams().setSoTimeout(SO_TIMEOUT);
	}

	/**
	 * 获取客户端实例。
	 *
	 * @return SinaStockClient
	 */

	public synchronized static SinaStockClient getInstance() {
		if(mInstance != null) {
			return mInstance;
		}

		return new SinaStockClient();
	}

	/**
	 * 通过股票代码，获取行情信息。
	 *
	 * @param stockCodes
	 *      股票代码数组。沪市股票代码以"sh+股票代码", 深市股票代码以"sz+股票代码"。
	 *
	 * @return 成功返回List<SinaStockInfo>，失败返回null。
	 *
	 * @throws IOException
	 * @throws HttpException
	 * @throws ParseStockInfoException
	 */
	public List<SinaStockInfo> getStockInfo(String[] stockCodes) throws HttpException, IOException, ParseStockInfoException {
		//int i = 1/0;
		String url = STOCK_URL + generateStockCodeRequest(stockCodes);
		HttpMethod method = new GetMethod(url);
		// 添加请求头，避免被服务器拒绝
		method.setRequestHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36");
		method.setRequestHeader("Referer", "https://finance.qq.com/");
		method.setRequestHeader("Accept", "*/*");
		
		InputStream is = null;
		InputStreamReader reader = null;
		BufferedReader bReader = null;
		
		try {
			int statusCode = mHttpClient.executeMethod(method);

			if(statusCode != HttpStatus.SC_OK) {
				method.releaseConnection();
				return null;
			}

			is = method.getResponseBodyAsStream();
			// 腾讯API使用GBK编码
			reader = new InputStreamReader(new BufferedInputStream(is), Charset.forName("GBK"));
			bReader = new BufferedReader(reader);
			
			// 读取所有行
			StringBuilder responseBuilder = new StringBuilder();
			String line;
			while((line = bReader.readLine()) != null) {
				if (!line.trim().isEmpty()) {
					responseBuilder.append(line).append("\n");
				}
			}
			
			String response = responseBuilder.toString().trim();
			if (response.isEmpty()) {
				android.util.Log.e("SinaStockClient", "Empty response from API");
				throw new ParseStockInfoException();
			}
			
			// 调试：记录API返回的内容（前200个字符）
			android.util.Log.d("SinaStockClient", "API response: " + (response.length() > 200 ? response.substring(0, 200) + "..." : response));
			
			// 使用StringReader来解析
			BufferedReader responseReader = new BufferedReader(new StringReader(response));
			List<SinaStockInfo> list = parseSinaStockInfosFromReader(responseReader);
			responseReader.close();
			return list;
		} finally {
			// 确保资源被关闭
			if (bReader != null) {
				try {
					bReader.close();
				} catch (IOException e) {
					// 忽略关闭时的异常
				}
			}
			if (reader != null) {
				try {
					reader.close();
				} catch (IOException e) {
					// 忽略关闭时的异常
				}
			}
			if (is != null) {
				try {
					is.close();
				} catch (IOException e) {
					// 忽略关闭时的异常
				}
			}
			method.releaseConnection();
		}
	}

	/**
	 * 获取股票分时图或K线图。
	 *
	 * @param stockCode
	 *      股票代码。沪市股票代码以"sh+股票代码", 深市股票代码以"sz+股票代码"。
	 * @param imageType
	 *      指明请求的图像类型。
	 *      IMAGE_TYPE_MINITE 为分时图。
	 *      IMAGE_TYPE_DAILY 为日K线图。
	 *      IMAGE_TYPE_WEEKLY 为周K线图。
	 *      IMAGE_TYPE_MONTHLY 为月K线图。
	 * @return 如果成功则返回Bitmap图像，失败返回null。
	 * @throws IOException
	 * @throws HttpException
	 */
	public Bitmap getStockImage(String stockCode, int imageType) throws HttpException, IOException {
		//add by ipdiy
		stockCode = getQueryCode(stockCode);
		String baseRequestUrl = null;

		switch(imageType) {
			case IMAGE_TYPE_MINITE:
				baseRequestUrl = STOCK_MINITE_URL;
				break;

			case IMAGE_TYPE_DAILY:
				baseRequestUrl = STOCK_DAILY_URL;
				break;

			case IMAGE_TYPE_WEEKLY:
				baseRequestUrl = STOCK_WEEKLY_URL;
				break;

			case IMAGE_TYPE_MONTHLY:
				baseRequestUrl = STOCK_MONTHLY_URL;
				break;
		}

		if(TextUtils.isEmpty(baseRequestUrl)) {
			return null;
		}

		String fullRequestUrl = baseRequestUrl + stockCode + ".gif";
		return getBitmapFromUrl(fullRequestUrl);
	}

	private String generateStockCodeRequest(String[] stockCodes) {
		if(stockCodes == null || stockCodes.length == 0) {
			return null;
		}

		StringBuilder sb = new StringBuilder(getQueryCode(stockCodes[0]));
		final int length = stockCodes.length;

		for(int i = 1; i < length; ++i) {
			if( stockCodes[i]!=null) { //add   by ipdiy .
				sb.append(',');
				sb.append(getQueryCode(stockCodes[i]));//add getQueryCode  by ipdiy .
			}
		}

		return sb.toString();
	}

	private List<SinaStockInfo> parseSinaStockInfosFromReader(BufferedReader reader) throws IOException, ParseStockInfoException {
		ArrayList<SinaStockInfo> list = new ArrayList<SinaStockInfo>(10);
		String sourceLine = null;

		while((sourceLine = reader.readLine()) != null) {
			list.add(SinaStockInfo.parseStockInfo(sourceLine));
		}

		return list;
	}

	private Bitmap getBitmapFromUrl(String url) throws HttpException, IOException {
		HttpMethod method = new GetMethod(url);
		InputStream in = null;
		BufferedInputStream bis = null;
		
		try {
			int statusCode = mHttpClient.executeMethod(method);

			if(statusCode != HttpStatus.SC_OK) {
				method.releaseConnection();
				return null;
			}

			in = method.getResponseBodyAsStream();
			bis = new BufferedInputStream(in);
			Bitmap bm = BitmapFactory.decodeStream(bis);
			return bm;
		} finally {
			// 确保资源被关闭
			if (bis != null) {
				try {
					bis.close();
				} catch (IOException e) {
					// 忽略关闭时的异常
				}
			}
			if (in != null) {
				try {
					in.close();
				} catch (IOException e) {
					// 忽略关闭时的异常
				}
			}
			method.releaseConnection();
		}
	}

	//example:from "000002" to "sz000002" (腾讯API格式)
	private String getQueryCode(String code) {
		if (code == null) {
			return null;
		}

		String c = code.trim();
		if (c.isEmpty()) {
			return null;
		}

		// 兼容常见写法：00700.HK / HK00700 / hk00700
		c = c.replace("．", "."); // 全角点
		if (c.contains(".")) {
			String[] parts = c.split("\\.");
			if (parts.length >= 2) {
				String left = parts[0];
				String right = parts[parts.length - 1];
				if (right != null && right.equalsIgnoreCase("HK")) {
					c = "hk" + left;
				} else {
					c = left;
				}
			}
		}

		String lower = c.toLowerCase();
		if (lower.startsWith("hk")) {
			String digits = lower.substring(2).replaceAll("\\D+", "");
			if (digits.length() == 0) {
				return null;
			}
			if (digits.length() < 5) {
				digits = String.format("%05d", Integer.parseInt(digits));
			} else if (digits.length() > 5) {
				digits = digits.substring(digits.length() - 5);
			}
			return "hk" + digits;
		}

		// 纯数字：6位按A股处理；1-5位按港股处理（腾讯：hk00700）
		String digits = lower.replaceAll("\\D+", "");
		if (digits.length() == 6) {
			if (digits.startsWith("6")) {
				return "sh" + digits;
			}
			return "sz" + digits;
		}
		if (digits.length() >= 1 && digits.length() <= 5) {
			String padded = digits.length() == 5 ? digits : String.format("%05d", Integer.parseInt(digits));
			return "hk" + padded;
		}

		// 兜底：保持原样（可能是腾讯其他市场编码，如 usAAPL 等）
		return lower;
	}

}
