/**
 *
 */
package alarm.stock;

/*
import static alarm.stock.Constants.F_CODE;
import static alarm.stock.Constants.F_NAME;
import static alarm.stock.Constants.TABLE_NAME;
*/
//import static android.provider.BaseColumns._ID;
import android.os.Environment;
import android.provider.BaseColumns;

/**
 * @author ipdiy
 *
 */
public interface Constants extends BaseColumns {
	/** Called when the activity is first created. */

	String DB_NAME = "alarm.stock.ipdiy";
	int DB_VERSION = 5;
	String TABLE_NAME = "stock_info";
	String F_CODE = "code";
	String F_NAME = "name";
	String F_PRICE_H = "price_h";
	String F_PRICE_L = "price_l";
	String F_RISE = "rise";
	String F_DROP = "drop_";
	String F_VALID = "valid";

	String TABLE_CREATE = "CREATE TABLE "+
		TABLE_NAME+"("+_ID+" INTEGER PRIMARY KEY AUTOINCREMENT,"+
		F_CODE+" TEXT,"+
		F_NAME+" TEXT NOT NULL, " + 
		F_PRICE_H+" smallmoney, " + 
		F_PRICE_L+" smallmoney, "+
		F_RISE +" tinyint, " + 
		F_DROP + " tinyint, " + 
		F_VALID + " tinyint)";

	//pubilc static final String[] = FROM={_ID,F_CODE,F_NAME};
	String list_title = "名称      现价    最高价   最低价  涨幅  跌幅";

	int ADD_RESULT_OK = 0;
	int ADD_RESULT_EXIST = 1;



	int CMP_RESULT_NONE         = 0;
	int CMP_RESULT_PRICE_H  = 0x0001;
	int CMP_RESULT_PRICE_L  = 0x0010;
	int CMP_RESULT_RISE     = 0x0100;
	int CMP_RESULT_DROP     = 0x1000;

	int  max_stock_count =  64;

	String path_install = Environment.getExternalStorageDirectory() +"/stockalarm/";
	String path_install_child = path_install + "download/";
	String install_name = "stock_install.apk";
	/*
	public static final String url_login = "http://mysoft.ipdiy.net/mysoft/login.jsp?softid=3";
	public static final String url_login2 = "http://mysoft.ipdiy.net:8080/mysoft/login.jsp?softid=3";
	*/

	String url_login = "http://ipdiy.j1.fjjsp.net/mysoft/login.jsp?softid=3";
	String url_login2 = "http:ipdiy.j1.fjjsp.net:8080/mysoft/login.jsp?softid=3";
	
//
String  url_logout = url_login;

	String time_of_auto_update = "11:35:";
	String help_info = "        声动金来是一个股价预警软件。您先设置好预警条件，无需盯盘。当股价到达您设置的条件时，软件自动发出提示！\n说明：本软件只提供报警功能，不能买卖股票！";
	String free_info = "\n本软件尽最大努力提供准确可靠的信息。但对于使用本软件给您造成经济或精神上的损失，本软件及其作者不负任何法律责任！\n版权所有 深圳言和行软件 www.yanhexing.cn";
    String share_info = "我正在用一个股价预警软件，名字叫‘声动金来’，可以对股票设定好价钱，到目标价了就提醒我，挺好用的。特意分享给你试试。可以到这里下载:http://www.yanhexing.cn/download/stockalarm/stockalarm.apk";

}

