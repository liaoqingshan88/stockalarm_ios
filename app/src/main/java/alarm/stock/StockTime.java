package alarm.stock;

import java.util.Calendar;

/**
 * 交易时段判定（含港股）。
 *
 * 时段划分（北京时间，工作日）：
 *   A股核心   : 9:30-11:30、13:00-15:00
 *   港股      : 9:30-12:00、13:00-16:00（其中 11:30-12:00、15:00-16:00 为港股延长交易时段，
 *                                          若股票列表含港股，这段时间只检查港股）
 *
 * 无参数版 IsTradeTime() 保持向后兼容（仅 A股核心时段，略放宽容忍 9:25 集合竞价），
 * 供既有调用点（AlarmActivity 等）使用；含港股判断请用 IsTradeTime(boolean hasHk)。
 */
public class StockTime {

	static int hour, minute, second, WeekOfYear;
    static String[] week = {"日", "一", "二", "三", "四", "五", "六", };

    // 交易时段边界（秒）
    private static final int T_A_OPEN   = sec(9,30,0);   // A股/港股 开盘
    private static final int T_A_AM_END = sec(11,30,0);  // A股上午收盘
    private static final int T_HK_AM_END= sec(12,0,0);   // 港股上午收盘
    private static final int T_PM_OPEN  = sec(13,0,0);   // 下午开盘
    private static final int T_A_PM_END = sec(15,0,0);   // A股下午收盘
    private static final int T_HK_PM_END= sec(16,0,0);   // 港股下午收盘

	private static  void getTime() {
		Calendar ca = Calendar.getInstance();
		hour=ca.get(Calendar.HOUR_OF_DAY);//小时
		minute=ca.get(Calendar.MINUTE);//分
		second=ca.get(Calendar.SECOND);//秒
		WeekOfYear = ca.get(Calendar.DAY_OF_WEEK);
		WeekOfYear --;//from 0 to 6
		//hour += 12;
	}

	public static String GetTime() {
		getTime();
		return "星期"+ week[WeekOfYear] + " " + hour +":"+minute+":"+second ;
	}

	/**
	 * 无参会话：仅 A股核心时段（含 9:25 集合竞价缓冲），向后兼容。
	 * 注意：若股票列表含港股，港股延长时段(11:30-12:00、15:00-16:00)不包含在内，
	 *       请使用 IsTradeTime(hasHk)。
	 */
	public static boolean IsTradeTime() {
		return IsTradeTime(false);
	}

	/**
	 * 整体是否处于交易时段。
	 * @param hasHk 股票列表是否含港股；为 true 时，上午延至 12:00、下午延至 16:00
	 */
	public static boolean IsTradeTime(boolean hasHk) {
		getTime();

		if( WeekOfYear >= 1 && WeekOfYear <= 5 ) {
			int cur = GetSecond(hour, minute, second);

			// A股核心时段（9:30-11:30、13:00-15:00；沿用 9:25 缓冲）
			if( (cur >= sec(9,25,0) && cur <= T_A_AM_END)
					|| (cur >= T_PM_OPEN && cur <= T_A_PM_END) ) {
				return true;
			}

			// 港股延长时段：11:30-12:00、15:00-16:00
			if (hasHk) {
				if ( (cur > T_A_AM_END && cur <= T_HK_AM_END)
						|| (cur > T_A_PM_END && cur <= T_HK_PM_END) ) {
					return true;
				}
			}
		}

		return false;
	}

	/**
	 * 判断某只股票代码当前是否处于其交易时段。
	 * 港股按港股时段(9:30-12:00、13:00-16:00)，A股按核心时段(9:30-11:30、13:00-15:00)。
	 * @param code 股票代码（如 sh600000 / 600000 / hk00700 / 00700 / 00700.HK）
	 */
	public static boolean IsTradeTimeFor(String code) {
		return isHkStock(code) ? IsTradeTime(true) && isHkHour(GetCurTime()) : IsTradeTime(false);
	}

	/** 当前是否处于港股交易时段（任意港股在交易中） */
	private static boolean isHkHour(int cur) {
		return (cur >= sec(9,25,0) && cur <= T_HK_AM_END)
				|| (cur >= T_PM_OPEN && cur <= T_HK_PM_END);
	}

	/** 判断股票代码是否为港股 */
	public static boolean isHkStock(String code) {
		if (code == null) return false;
		String c = code.trim();
		if (c.isEmpty()) return false;
		String lower = c.toLowerCase();

		// 显式 HK 前缀 / .HK 后缀
		if (lower.startsWith("hk")) return true;
		if (lower.endsWith(".hk")) return true;

		// 纯数字：去除非数字后，1-5 位按港股处理（6 位按 A股）
		String digits = lower.replaceAll("[^0-9]", "");
		if (digits.isEmpty()) return false;
		int len = digits.length();
		if (len >= 1 && len <= 5) return true;   // 5位内数字 → 港股
		return false;
	}

	/**
	 * 距下一交易日（或今日下午/上午）开盘还有多少毫秒。
	 * 用于非交易时段安排低频调度：返回从当前位置到下一个「需要恢复高频检查」的时间点。
	 * 规则（以含港股与否的最大开盘 9:30 为准）：
	 *   - 交易日盘中/盘前 → 尽快（返回 0，表示应立即恢复高频）
	 *   - 交易日午休(11:30-13:00) → 距今日下午 13:00
	 *   - 交易日盘后(15:00 后) → 距下一交易日 9:30
	 *   - 周末/节假日 → 距下一工作日 9:30
	 * @return 毫秒数；若当前本就在交易时段返回 0
	 */
	public static long millisUntilNextTradeStart() {
		return millisUntilNextTradeStart(IsTradeTime(true));
	}

	/**
	 * @param currentlyInTrade 当前是否已在交易时段（由调用方按含港股与否判断）
	 */
	public static long millisUntilNextTradeStart(boolean currentlyInTrade) {
		long now = System.currentTimeMillis();
		Calendar ca = Calendar.getInstance();
		ca.setTimeInMillis(now);

		if (currentlyInTrade) {
			return 0L;
		}

		int dow = ca.get(Calendar.DAY_OF_WEEK); // 1=周日..7=周六
		int cur = ca.get(Calendar.HOUR_OF_DAY) * 3600 + ca.get(Calendar.MINUTE) * 60 + ca.get(Calendar.SECOND);

		// 工作日：判断是午休还是盘后
		boolean workday = dow >= Calendar.MONDAY && dow <= Calendar.FRIDAY;

		if (workday) {
			if (cur < T_A_OPEN) {
				// 盘前（早于 9:30）→ 今日开盘
				return until(now, sec(9,30,0));
			}
			if (cur < T_PM_OPEN && cur >= T_A_AM_END) {
				// 午休 11:30-13:00 → 今日下午开盘
				return until(now, sec(13,0,0));
			}
			// 盘后（15:00/16:00 后）或其它 → 下一交易日 9:30
			return nextWorkdayOpenAt9_30(now) - now;
		}

		// 周末 → 下一工作日(周一) 9:30
		return nextWorkdayOpenAt9_30(now) - now;
	}

	/** 计算「今天 09:30」相对 now 的毫秒数（用于盘前/午休的短延时） */
	private static long until(long now, int targetSecondOfDay) {
		Calendar ca = Calendar.getInstance();
		ca.setTimeInMillis(now);
		ca.set(Calendar.HOUR_OF_DAY, targetSecondOfDay / 3600);
		ca.set(Calendar.MINUTE, (targetSecondOfDay % 3600) / 60);
		ca.set(Calendar.SECOND, 0);
		ca.set(Calendar.MILLISECOND, 0);
		return ca.getTimeInMillis() - now;
	}

	/** 下一个工作日 9:30 的绝对毫秒时间戳（今天盘后则为明天起第一个工作日） */
	private static long nextWorkdayOpenAt9_30(long now) {
		Calendar ca = Calendar.getInstance();
		ca.setTimeInMillis(now);
		// 从明天开始找第一个工作日
		ca.add(Calendar.DAY_OF_YEAR, 1);
		while (ca.get(Calendar.DAY_OF_WEEK) == Calendar.SATURDAY
				|| ca.get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY) {
			ca.add(Calendar.DAY_OF_YEAR, 1);
		}
		ca.set(Calendar.HOUR_OF_DAY, 9);
		ca.set(Calendar.MINUTE, 30);
		ca.set(Calendar.SECOND, 0);
		ca.set(Calendar.MILLISECOND, 0);
		return ca.getTimeInMillis();
	}

	/** 是否为周末（周六/周日） */
	public static boolean isWeekend() {
		getTime();
		return WeekOfYear == 0 || WeekOfYear == 6;
	}

	static int GetSecond(int hour, int minute, int second) {
		return hour* 3600 + minute * 60 + second;
	}

	private static int sec(int h, int m, int s) {
		return h * 3600 + m * 60 + s;
	}

	public static int GetCurTime()
	{
		getTime();
		return hour* 3600 + minute * 60 + second;
	}

}
