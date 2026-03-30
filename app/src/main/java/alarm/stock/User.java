package alarm.stock;

import android.content.Context;
import android.telephony.TelephonyManager;

public class User {
	private String name;
	private String pwd;

	public User()
	{}

	void Regist() {
	}

	void login() {
	}
	void logout() {
	}

	void Save() {
	}

	void Get() {
	}


	public static String getDeviceid(Context context) {
		TelephonyManager mTelephonyMgr;
		mTelephonyMgr = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
		String str = mTelephonyMgr.getDeviceId();
		//str += "!"+mTelephonyMgr.getLine1Number();
		//str+= "!"+mTelephonyMgr.getCellLocation();
		//str += "!"+mTelephonyMgr.getNetworkCountryIso();
		return str;
	}

}
