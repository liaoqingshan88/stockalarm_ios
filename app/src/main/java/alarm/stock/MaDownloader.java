package alarm.stock;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpMethod;
import org.apache.commons.httpclient.HttpStatus;
import org.apache.commons.httpclient.methods.GetMethod;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * 均线价格下载器。
 * 从腾讯 K 线 API 获取历史日线数据，计算各周期均线价格。
 */
public class MaDownloader {

    // 腾讯 K 线 API（日线，最多请求 320 条）
    private static final String KLINE_API_URL = "https://ifzq.gtimg.cn/appstock/app/kline/kline?param=%s,day,,,320";

    private static final int CONNECTION_TIMEOUT = 5000;
    private static final int SO_TIMEOUT = 20000;

    private HttpClient mHttpClient;

    public MaDownloader() {
        mHttpClient = new HttpClient();
        mHttpClient.getHttpConnectionManager().getParams().setConnectionTimeout(CONNECTION_TIMEOUT);
        mHttpClient.getHttpConnectionManager().getParams().setSoTimeout(SO_TIMEOUT);
    }

    /**
     * 下载并计算某支股票的所有均线价格。
     *
     * @param code 股票代码（如 "000002"，不含 sh/sz 前缀）
     * @return float[7] 数组，顺序为 MA5, MA10, MA20, MA30, MA60, MA120, MA250。
     *         某周期数据不足时该值为 -1。
     */
    public float[] downloadAndCalculateMAs(String code) {
        float[] result = new float[MaPriceMng.MA_PERIODS.length];
        for (int i = 0; i < result.length; i++) {
            result[i] = -1f;
        }

        try {
            // 获取查询代码（sh/sz 前缀）
            String queryCode = getQueryCode(code);
            if (queryCode == null) {
                return result;
            }

            // 请求 K 线数据
            String url = String.format(KLINE_API_URL, queryCode);
            String response = httpGet(url);
            if (response == null || response.isEmpty()) {
                android.util.Log.w("MaDownloader", "Empty response for " + code);
                return result;
            }

            // 解析 JSON 获取收盘价列表
            List<Float> closes = parseKlineCloses(response, queryCode);
            if (closes.isEmpty()) {
                android.util.Log.w("MaDownloader", "No kline data for " + code);
                return result;
            }

            // 计算各周期均线
            int size = closes.size();
            for (int i = 0; i < MaPriceMng.MA_PERIODS.length; i++) {
                int period = MaPriceMng.MA_PERIODS[i];
                if (size >= period) {
                    float sum = 0f;
                    for (int j = size - period; j < size; j++) {
                        sum += closes.get(j);
                    }
                    result[i] = sum / period;
                }
            }

            android.util.Log.i("MaDownloader", "MAs for " + code + ": " +
                String.format("MA5=%.2f, MA10=%.2f, MA20=%.2f, MA30=%.2f, MA60=%.2f, MA120=%.2f, MA250=%.2f",
                    result[0], result[1], result[2], result[3], result[4], result[5], result[6]));

        } catch (Exception e) {
            android.util.Log.e("MaDownloader", "download MAs failed for " + code + ": " + e.getMessage(), e);
        }

        return result;
    }

    /**
     * 从腾讯 K 线 API 返回的 JSON 中解析出收盘价列表。
     * JSON 格式：{"data":{"sz000002":{"day":[["2025-03-18","7.570","7.530","7.590","7.490","893408"],...]}}}
     * day 数组每行格式：[date, open, close, high, low, volume]
     */
    private List<Float> parseKlineCloses(String jsonStr, String queryCode) throws Exception {
        List<Float> closes = new ArrayList<>();
        JSONObject root = new JSONObject(jsonStr);
        JSONObject data = root.optJSONObject("data");
        if (data == null) return closes;

        // 尝试用 queryCode 查询，再尝试遍历所有 key
        JSONObject stockData = data.optJSONObject(queryCode);
        if (stockData == null) {
            // 遍历 data 的所有 key（Android 的 org.json.JSONObject 没有 getNames）
            try {
                java.util.Iterator<String> keys = data.keys();
                while (keys.hasNext()) {
                    String key = keys.next();
                    stockData = data.optJSONObject(key);
                    if (stockData != null) break;
                }
            } catch (Exception e) {
                // ignore
            }
        }
        if (stockData == null) return closes;

        JSONArray dayArray = stockData.optJSONArray("day");
        if (dayArray == null) return closes;

        for (int i = 0; i < dayArray.length(); i++) {
            JSONArray kline = dayArray.optJSONArray(i);
            if (kline != null && kline.length() >= 3) {
                // kline[1] = 开盘, kline[2] = 收盘
                String closeStr = kline.optString(2);
                if (closeStr != null && !closeStr.isEmpty()) {
                    try {
                        closes.add(Float.parseFloat(closeStr));
                    } catch (NumberFormatException e) {
                        // 忽略解析错误
                    }
                }
            }
        }
        return closes;
    }

    /**
     * 执行 HTTP GET 请求，返回响应字符串。
     */
    private String httpGet(String url) {
        HttpMethod method = new GetMethod(url);
        method.setRequestHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36");
        method.setRequestHeader("Referer", "https://finance.qq.com/");

        InputStream is = null;
        BufferedReader reader = null;
        try {
            int statusCode = mHttpClient.executeMethod(method);
            if (statusCode != HttpStatus.SC_OK) {
                return null;
            }

            is = method.getResponseBodyAsStream();
            reader = new BufferedReader(new InputStreamReader(is, Charset.forName("UTF-8")));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
            return sb.toString();
        } catch (Exception e) {
            android.util.Log.e("MaDownloader", "HTTP GET failed: " + e.getMessage(), e);
            return null;
        } finally {
            try { if (reader != null) reader.close(); } catch (Exception ignore) {}
            try { if (is != null) is.close(); } catch (Exception ignore) {}
            method.releaseConnection();
        }
    }

    /**
     * 将股票代码转换为腾讯 K 线 API 可识别的格式。
     * 例如：000002 → sz000002，600000 → sh600000
     */
    private String getQueryCode(String code) {
        String c = code.trim();
        if (c.isEmpty()) return null;

        // 腾讯 K 线 API 使用小写前缀
        String lower = c.toLowerCase();
        if (lower.startsWith("sh") || lower.startsWith("sz") || lower.startsWith("hk")) {
            return lower;
        }

        // 纯数字
        if (c.matches("\\d+")) {
            if (c.length() == 6) {
                if (c.startsWith("6")) return "sh" + c;
                if (c.startsWith("4") || c.startsWith("8")) return "bj" + c;
                return "sz" + c;
            }
            if (c.length() == 5) {
                return "hk" + c;
            }
        }

        // 尝试去掉 .HK 后缀
        if (c.endsWith(".HK") || c.endsWith(".hk")) {
            String digits = c.substring(0, c.length() - 3).trim();
            if (digits.matches("\\d+")) {
                return "hk" + (digits.length() == 5 ? digits : String.format("%05d", Integer.parseInt(digits)));
            }
        }

        return lower;
    }
}
