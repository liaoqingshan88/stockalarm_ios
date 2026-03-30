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

//import android.util.Log;


/**
 * 新浪股票信息。
 *
 * @author <a href="http://www.blogjava.net/zh-weir/" target="_blank">zh.weir的技术博客</a>
 * </br>
 * </br>
 * <a href="http://weibo.com/1779382071?s=6uyXnP" target="_blank"><img border="0" src="http://service.t.sina.com.cn/widget/qmd/1779382071/9c2d28b9/1.png"/></a>
 *
 */
public class SinaStockInfo {

	//股票名字
	private String mName;
	//今日开盘价
	private float mTodayPrice;
	//昨日收盘价
	private float mYestodayPrice;
	//当前价
	private float mNowPrice;
	//今日最高价
	private float mHighestPrice;
	//今日最低价
	private float mLowestPrice;
	//买一价
	private float mBuy1Price;
	//卖一价
	private float mSell1Price;
	//成交股票数，单位“股”。100股为1手。
	private long mTradeCount;
	//成交额，单位“元”。一般需要转换成“万元”。
	private float mTradeMoney;  //modiy from long to float by ipdiy

	//买单情况
	private BuyOrSellInfo[] mBuy;

	//卖单情况
	private BuyOrSellInfo[] mSell;

	//日期
	private String mDate;
	//时间
	private String mTime;

	private SinaStockInfo(String name, float todayPrice, float yestodayPrice,
						  float nowPrice, float highestPrice, float lowestPrice,
						  float buy1Price, float sell1Price, long tradeCount, float tradeMoney,
						  BuyOrSellInfo[] buy, BuyOrSellInfo[] sell, String date, String time) {
		mName = name;
		mTodayPrice = todayPrice;
		mYestodayPrice = yestodayPrice;
		mNowPrice = nowPrice;
		mHighestPrice = highestPrice;
		mLowestPrice = lowestPrice;
		mBuy1Price = buy1Price;
		mSell1Price = sell1Price;
		mTradeCount = tradeCount;
		mTradeMoney = tradeMoney;
		mBuy = buy;
		mSell = sell;
		mDate = date;
		mTime = time;
	}

	/**
	 * 从一行响应字符串中解析得到SinaStockInfo数据结构。
	 *
	 * @param source
	 *      参数的格式如：
	 *      var hq_str_sh601006="大秦铁路,7.69,7.70,7.62,7.72,7.61,7.61,7.62,46358694,355190642,565201,7.61,984000,7.60,211900,7.59,476600,7.58,238500,7.57,295518,7.62,217137,7.63,241500,7.64,345900,7.65,419400,7.66,2012-02-29,15:03:07";
	 *
	 * @return SinaStockInfo
	 * @throws ParseStockInfoException
	 */
	public static SinaStockInfo parseStockInfo(String source) throws ParseStockInfoException {
		int start = source.indexOf('\"');
		if (start == -1) {
			android.util.Log.e("SinaStockInfo", "No quote found in: " + source);
			throw new ParseStockInfoException();
		}
		int end = source.lastIndexOf('\"');
		if (end == -1 || end <= start) {
			android.util.Log.e("SinaStockInfo", "Invalid quote format in: " + source);
			throw new ParseStockInfoException();
		}
		String targetString = source.substring(start+1, end);
		
		// 判断是腾讯API格式（~分隔）还是新浪API格式（,分隔）
		boolean isTencentFormat = targetString.contains("~");
		String[] infoStr;
		
		if (isTencentFormat) {
			// 腾讯API格式：使用~分隔
			infoStr = targetString.split("~");
			// 腾讯API至少需要30个字段
			if(infoStr.length < 30) {
				android.util.Log.e("SinaStockInfo", "Insufficient fields (Tencent): " + infoStr.length + " in: " + source.substring(0, Math.min(100, source.length())));
				throw new ParseStockInfoException();
			}
		} else {
			// 新浪API格式：使用,分隔
			infoStr = targetString.split(",");
			// 新浪API至少需要32个字段
			if(infoStr.length < 32) {
				android.util.Log.e("SinaStockInfo", "Insufficient fields (Sina): " + infoStr.length + " in: " + source.substring(0, Math.min(100, source.length())));
				throw new ParseStockInfoException();
			}
		}

		final String name;
		final float todayPrice;
		final float yestodayPrice;
		final float nowPrice;
		final float highestPrice;
		final float lowestPrice;
		final float buy1Price;
		final float sell1Price;
		final long tradeCount;
		final float tradeMoney;
		BuyOrSellInfo buy1, buy2, buy3, buy4, buy5;
		BuyOrSellInfo sell1, sell2, sell3, sell4, sell5;
		final String date;
		final String time;
		
		if (isTencentFormat) {
			// 腾讯API字段映射（根据实际返回格式调整）
			// 格式：v_sz000002="51~万  科Ａ~000002~4.64~4.67~4.66~419901~157253~262642~4.64~52156~4.63~34924~4.62~24973~4.61~15544~4.60~29157~4.65~19714~4.66~17432~4.67~23714~4.68~46347~4.69~23127~~20260312112351~-0.03~-0.6..."
			// 0: 未知（51）
			// 1: 股票名称（万  科Ａ）
			// 2: 股票代码（000002）
			// 3: 当前价格（4.64）
			// 4: 昨收（4.67）
			// 5: 今开（4.66）
			// 6: 成交量（419901）
			// 7: 外盘（157253）
			// 8: 内盘（262642）
			// 9: 买一价（4.64）
			// 10: 买一量（52156）
			// 11: 买二价（4.63）
			// 12: 买二量（34924）
			// 13: 买三价（4.62）
			// 14: 买三量（24973）
			// 15: 买四价（4.61）
			// 16: 买四量（15544）
			// 17: 买五价（4.60）
			// 18: 买五量（29157）
			// 19: 卖一价（4.65）
			// 20: 卖一量（19714）
			// 21: 卖二价（4.66）
			// 22: 卖二量（17432）
			// 23: 卖三价（4.67）
			// 24: 卖三量（23714）
			// 25: 卖四价（4.68）
			// 26: 卖四量（46347）
			// 27: 卖五价（4.69）
			// 28: 卖五量（23127）
			// 29: 空字段
			// 30: 日期时间（20260312112351，格式：YYYYMMDDHHMMSS）
			
			name = infoStr[1].trim();
			nowPrice = parseFloatSafely(infoStr[3], 0.0f);
			yestodayPrice = parseFloatSafely(infoStr[4], nowPrice);
			todayPrice = parseFloatSafely(infoStr[5], nowPrice);
			
			// 腾讯API可能不直接提供最高最低价，使用当前价作为默认值
			highestPrice = nowPrice > yestodayPrice ? nowPrice : yestodayPrice;
			lowestPrice = nowPrice < yestodayPrice ? nowPrice : yestodayPrice;
			
			buy1Price = parseFloatSafely(infoStr[9], 0.0f);
			sell1Price = parseFloatSafely(infoStr[19], 0.0f);
			tradeCount = parseLongSafely(infoStr[6], 0L);
			tradeMoney = parseFloatSafely(infoStr[6], 0.0f) * nowPrice; // 估算成交额
			
			buy1 = new BuyOrSellInfo(parseLongSafely(infoStr[10], 0L), parseFloatSafely(infoStr[9], 0.0f));
			buy2 = new BuyOrSellInfo(parseLongSafely(infoStr[12], 0L), parseFloatSafely(infoStr[11], 0.0f));
			buy3 = new BuyOrSellInfo(parseLongSafely(infoStr[14], 0L), parseFloatSafely(infoStr[13], 0.0f));
			buy4 = new BuyOrSellInfo(parseLongSafely(infoStr[16], 0L), parseFloatSafely(infoStr[15], 0.0f));
			buy5 = new BuyOrSellInfo(parseLongSafely(infoStr[18], 0L), parseFloatSafely(infoStr[17], 0.0f));
			
			sell1 = new BuyOrSellInfo(parseLongSafely(infoStr[20], 0L), parseFloatSafely(infoStr[19], 0.0f));
			sell2 = new BuyOrSellInfo(parseLongSafely(infoStr[22], 0L), parseFloatSafely(infoStr[21], 0.0f));
			sell3 = new BuyOrSellInfo(parseLongSafely(infoStr[24], 0L), parseFloatSafely(infoStr[23], 0.0f));
			sell4 = new BuyOrSellInfo(parseLongSafely(infoStr[26], 0L), parseFloatSafely(infoStr[25], 0.0f));
			sell5 = new BuyOrSellInfo(parseLongSafely(infoStr[28], 0L), parseFloatSafely(infoStr[27], 0.0f));
			
			// 解析日期时间：20260312112351 -> 日期：20260312，时间：112351
			if (infoStr.length > 30 && !infoStr[30].isEmpty()) {
				String dateTime = infoStr[30];
				if (dateTime.length() >= 8) {
					date = dateTime.substring(0, 8);
					time = dateTime.length() >= 14 ? dateTime.substring(8, 14) : "000000";
				} else {
					date = dateTime;
					time = "000000";
				}
			} else {
				date = "";
				time = "";
			}
		} else {
			// 新浪API格式（原有逻辑）
			name = infoStr[0];
			todayPrice = Float.parseFloat(infoStr[1]);
			yestodayPrice = Float.parseFloat(infoStr[2]);
			nowPrice = Float.parseFloat(infoStr[3]);
			highestPrice = Float.parseFloat(infoStr[4]);
			lowestPrice = Float.parseFloat(infoStr[5]);
			buy1Price = Float.parseFloat(infoStr[6]);
			sell1Price = Float.parseFloat(infoStr[7]);
			tradeCount = Long.parseLong(infoStr[8]);
			tradeMoney = Float.parseFloat(infoStr[9]);
			buy1 = new BuyOrSellInfo(Long.parseLong(infoStr[10]), Float.parseFloat(infoStr[11]));
			buy2 = new BuyOrSellInfo(Long.parseLong(infoStr[12]), Float.parseFloat(infoStr[13]));
			buy3 = new BuyOrSellInfo(Long.parseLong(infoStr[14]), Float.parseFloat(infoStr[15]));
			buy4 = new BuyOrSellInfo(Long.parseLong(infoStr[16]), Float.parseFloat(infoStr[17]));
			buy5 = new BuyOrSellInfo(Long.parseLong(infoStr[18]), Float.parseFloat(infoStr[19]));
			sell1 = new BuyOrSellInfo(Long.parseLong(infoStr[20]), Float.parseFloat(infoStr[21]));
			sell2 = new BuyOrSellInfo(Long.parseLong(infoStr[22]), Float.parseFloat(infoStr[23]));
			sell3 = new BuyOrSellInfo(Long.parseLong(infoStr[24]), Float.parseFloat(infoStr[25]));
			sell4 = new BuyOrSellInfo(Long.parseLong(infoStr[26]), Float.parseFloat(infoStr[27]));
			sell5 = new BuyOrSellInfo(Long.parseLong(infoStr[28]), Float.parseFloat(infoStr[29]));
			date = infoStr[30];
			time = infoStr[31];
		}
		SinaStockInfo stockInfo = new SinaStockInfo(name, todayPrice, yestodayPrice, nowPrice,
				highestPrice, lowestPrice, buy1Price, sell1Price,
				tradeCount, tradeMoney,
				new BuyOrSellInfo[] {buy1, buy2, buy3, buy4, buy5},
				new BuyOrSellInfo[] {sell1, sell2, sell3, sell4, sell5},
				date, time);
		return stockInfo;
	}

	/**
	 * 获取股票名称
	 * @return 股票名称
	 */
	public String getName() {
		return mName;
	}

	/**
	 * 获取今日开盘价
	 * @return 今日股票开盘价
	 */
	public float getTodayPrice() {
		return mTodayPrice;
	}

	/**
	 * 获取昨日收盘价
	 * @return 昨日收盘价
	 */
	public float getYestodayPrice() {
		return mYestodayPrice;
	}

	/**
	 * 获取当前股价
	 * @return 当前股价
	 */
	public float getNowPrice() {
		return mNowPrice;
	}

	/**
	 * 获取今日最高价
	 * @return 今日最高价
	 */
	public float getHighestPrice() {
		return mHighestPrice;
	}

	/**
	 * 获取今日最低价
	 * @return 今日最低价
	 */
	public float getLowestPrice() {
		return mLowestPrice;
	}

	/**
	 * 获取股票交易量。单位为“股”，100股为1手，请注意转换。
	 * @return 股票交易量
	 */
	public long getTradeCount() {
		return mTradeCount;
	}

	/**
	 * 获取股票交易额。单位为“元”，如需显示“万元”，请注意转换。
	 * @return 股票交易额
	 */
	public float getTradeMoney() {
		return mTradeMoney;
	}

	/**
	 * 获取买手信息。
	 * @return 买手信息。数组从前到后依次为：买一、买二、买三、买四、买五。
	 */
	public BuyOrSellInfo[] getBuyInfo() {
		return mBuy;
	}

	/**
	 * 获取卖手信息。
	 * @return 卖手信息。数组从前到后依次为：卖一、卖二、卖三、卖四、卖五。
	 */
	public BuyOrSellInfo[] getSellInfo() {
		return mSell;
	}

	/**
	 * 获取对应股票信息的日期。例如周末，或者其他休市期间获取的数据将不是实时的。
	 * @return 获取对应股票信息的日期。
	 */
	public String getDate() {
		return mDate;
	}

	/**
	 * 获取对应股票信息的时间。例如周末，或者其他休市期间获取的数据将不是实时的。
	 * @return 获取对应股票信息的时间。
	 */
	public String getTime() {
		return mTime;
	}


	// 安全解析浮点数，如果解析失败返回默认值
	private static float parseFloatSafely(String str, float defaultValue) {
		if (str == null || str.trim().isEmpty()) {
			return defaultValue;
		}
		try {
			return Float.parseFloat(str.trim());
		} catch (NumberFormatException e) {
			return defaultValue;
		}
	}
	
	// 安全解析长整数，如果解析失败返回默认值
	private static long parseLongSafely(String str, long defaultValue) {
		if (str == null || str.trim().isEmpty()) {
			return defaultValue;
		}
		try {
			return Long.parseLong(str.trim());
		} catch (NumberFormatException e) {
			return defaultValue;
		}
	}

	public static class ParseStockInfoException extends Exception {
		public ParseStockInfoException() {
			super("Parse StockInfo error!");
		}
	}

	@Override
	/*modify by ipdiy for alarm
	public String toString() {
	    // TODO Auto-generated method stub
	    StringBuilder sb = new StringBuilder();
	    sb.append("股票名称： " + getName() + "\n");
	    sb.append("今日开盘价： " + getTodayPrice() + "元\n");
	    sb.append("昨日收盘价： " + getYestodayPrice() + "元\n");
	    sb.append("当前股价： " + getNowPrice() + "元\n");
	    sb.append("今日最高价： " + getHighestPrice() + "元\n");
	    sb.append("今日最低价： " + getLowestPrice() + "元\n");
	    sb.append("今日交易量： " + getTradeCount() + "股\n");
	    sb.append("今日成交量： " + getTradeMoney() + "元\n");

	    BuyOrSellInfo[] buy = getBuyInfo();
	    sb.append("买一：\n" + buy[0] + "\n");
	    sb.append("买二：\n" + buy[1] + "\n");
	    sb.append("买三：\n" + buy[2] + "\n");
	    sb.append("买四：\n" + buy[3] + "\n");
	    sb.append("买五：\n" + buy[4] + "\n");

	    BuyOrSellInfo[] sell = getSellInfo();
	    sb.append("卖一：\n" + sell[0] + "\n");
	    sb.append("卖二：\n" + sell[1] + "\n");
	    sb.append("卖三：\n" + sell[2] + "\n");
	    sb.append("卖四：\n" + sell[3] + "\n");
	    sb.append("卖五：\n" + sell[4] + "\n");

	    sb.append("时间： " + getTime() + "\n");
	    sb.append("日期： " + getDate() + "\n");

	    return sb.toString();
	}
	*/
	public String toString() {
		// TODO Auto-generated method stub
		StringBuilder sb = new StringBuilder();
		/*
		sb.append("股票名称： " + getName() + "\n");
		sb.append("今日开盘价： " + getTodayPrice() + "元\n");
		sb.append("昨日收盘价： " + getYestodayPrice() + "元\n");
		sb.append("当前股价： " + getNowPrice() + "元\n");
		sb.append("今日最高价： " + getHighestPrice() + "元\n");
		sb.append("今日最低价： " + getLowestPrice() + "元\n");
		*/
		sb.append(getName() +": "+ getNowPrice() + "元\n");
		return sb.toString();
	}

	//买单或卖单信息。
	private static class BuyOrSellInfo {
		//数量。单位为“股”。100股为1手。
		long mCount;
		//价格。
		float mPrice;

		public BuyOrSellInfo(long count, float price) {
			mCount = count;
			mPrice = price;
		}

		@Override
		public String toString() {
			StringBuilder sb = new StringBuilder();
			sb.append("数量： " + mCount + "股    ");
			sb.append("价格： " + mPrice + "元");
			return sb.toString();
		}
	}
}
