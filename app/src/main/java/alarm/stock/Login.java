package alarm.stock;

import java.net.MalformedURLException;
import java.net.URL;
import org.xml.sax.Attributes;


import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import alarm.stock.Update.VesionXMLContent;
import android.os.AsyncTask;
//2013. 5 双url登录 
public class Login {
	
	private Update m_update;
	public boolean logined;
	public String login_host;	//有两个url，要记录一个当前在用的
	public String reg_url;
	public String ver_url;
	public String apk_url;
	public String name;

	public String GetRegURL()
	{
		return login_host + reg_url;
	}
	public String GetVerURL()
	{
		return login_host + ver_url;
	}
	public String GetApkURL()
	{
		return login_host + apk_url;
	}	

	public Login(Update update)
	{
		logined = false;
		m_update = update;
	}
	public boolean IsLogin()
	{
		return logined;
	}
		
	public void Do()
	{
		LoginTask login = new LoginTask();
		login.Setpara(m_update, this);
		try
		{
			login.execute(Constants.url_login);	
		}
		catch(Exception e)
		{
			login.execute(Constants.url_login2);	
		}
	}
	
}
/*
1. AsyncTask的三个泛型参数说明（三个参数可以是任何类型） 
2.    第一个参数：传入doInBackground()方法的参数类型 
3.    第二个参数：传入onProgressUpdate()方法的参数类型 
4.    第三个参数：传入onPostExecute()方法的参数类型，也是doInBackground()方法返回的类型。
*/
	class LoginTask extends AsyncTask<String, Integer, String> {
		private Update m_update;
		private Login m_login;
		private String reg_url;
		private String ver_url;
		private String apk_url;
		private String name;
		
	public void Setpara(Update update, Login login)
	{
		m_update = update;
		m_login = login;
	}
	@Override
	protected String doInBackground(String... urlVer) {
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
			XMLContentLogin handler = new XMLContentLogin();
			saxParser.parse(inStream, handler);
			name = handler.getName();
			if(! name.equals("stockalarm") )
			{
				return "failed";
			}
			m_login.login_host = url.getHost();
			int port = 	url.getPort();
			if( port != -1)
			{
				m_login.login_host += ( ":"+  Integer.toString(port) ); 
			}
			m_login.login_host += "/";
			m_login.reg_url = handler.getRegUrl();
			m_login.apk_url = handler.getApkUrl();
			m_login.ver_url = handler.getVerUrl();
			m_login.logined = true;

			return "OK";
			
		}
		catch (Exception e) {
			e.printStackTrace();
			return "failed";
		}

	}

	@Override
	protected void onCancelled() {
		super.onCancelled();
	}

	@Override
	protected void onPostExecute(String result) {
		
		String now = StockTime.GetTime();
		if( m_login.logined && m_update.IsRequestUpdate() )
		{
			m_update.SetUrl(m_login.apk_url,m_login.ver_url );
			m_update.check();
		}
	}
	

class XMLContentLogin extends DefaultHandler {
	private String name; 
	private String ver;
	private String reg; 
	private String apk;
	private String preTag;// 前一个元素名称

	public String getVerUrl() {
		return ver;
	}
	public String getName() {
		return name;
	}
	public String getRegUrl() {
		return reg;
	}
	public String getApkUrl() {
		return apk;
	}
	@Override
	public void startDocument() throws SAXException {
		name =new String();
		ver =new String();
		reg =new String();
		apk =new String();
	}
	@Override
	public void characters(char[] ch, int start, int length)
	throws SAXException {
		String data = new String(ch, start, length);

		if ("ver".equals(preTag)) {
			ver = data;
		}
		if ("name".equals(preTag)) {
			name = data;
		}
		if ("reg".equals(preTag)) {
			reg = data;
		}
		if ("apk".equals(preTag)) {
			apk = data;
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
	
}

