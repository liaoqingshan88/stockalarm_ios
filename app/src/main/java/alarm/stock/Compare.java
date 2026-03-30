package alarm.stock;

import java.util.List;

//import org.apache.commons.httpclient.HttpException;

import android.os.AsyncTask;
import android.util.Log;
import android.widget.ListView;
import android.widget.TextView;
import static alarm.stock.Constants.*;

// 预警信息类，包含消息和触发预警的股票索引
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


/*
 1. AsyncTask的三个泛型参数说明（三个参数可以是任何类型） 
 2.    第一个参数：传入doInBackground()方法的参数类型 
 3.    第二个参数：传入onProgressUpdate()方法的参数类型 
 4.    第三个参数：传入onPostExecute()方法的参数类型，也是doInBackground()方法返回的类型。

 */
class Compare extends AsyncTask<StockMng, TextView, List<SinaStockInfo>>{
   
    TextView te=null;
    ListView listview = null;
    String[] codes = null;
    StockMng stockMng = null;
    AlarmActivity act = null;
    
    public Compare(AlarmActivity act,TextView te, ListView listview) {
        super();
        this.te = te;
        this.listview = listview;
        this.act = act;
        //te.append("init ");
    }

   @Override
    protected List<SinaStockInfo> doInBackground(StockMng... params)  {
	   try {
        // TODO Auto-generated method stub
		   System.out.println("do enter");
		   this.stockMng = params[0];
		   if(null == this.stockMng )
		   {
		   		Log.e("cmp", "compare para err\n");
		   		return null;
		   }
		   this.codes = stockMng.GetCodes();
		   SinaStockClient mClient;
		   mClient = SinaStockClient.getInstance();
		   List<SinaStockInfo>  stockinfo  = mClient.getStockInfo(codes );
		   System.out.println("do return ok");
		   return stockinfo;
	   }
		catch (Exception e) {
			//eView.setText("cmp "+e.toString());//超时不输出

			 System.out.println("do return failed");
			return null;
		}
	   // publishProgress(te);
		//System.out.println("do return imposible");
    }
   
   //finished
   @Override
    protected void onPostExecute(List<SinaStockInfo> result){
        // TODO Auto-generated method stub
		try{
		    super.onPostExecute(result);
	        if( null == result )
	        {
	        	AlarmActivity.is_http_timeout = true;
	        	return;
	        }
	        
	        AlarmActivity.is_http_timeout = false;
	        AlarmActivity.ReflashStockPrice(listview, result);
	        AlarmInfo alarmInfo = compareAllStocks(stockMng, result);
			if( alarmInfo != null && alarmInfo.message.length() > 0 )
			{
				te.append("\n"+alarmInfo.message);
				act.Notification(alarmInfo.message);
				// 定位到触发预警的股票并高亮显示
				act.scrollToAlarmStock(listview, alarmInfo.stockIndex, alarmInfo.stockCode);
			}
     	}catch (Exception e) {
     		Log.e("onPostExecute", "error: " + e.getMessage(), e);
     	}
		//System.out.println("postExecute---double---"+result);
    }

   	//prepare
   @Override
    protected void onPreExecute() {
        // TODO Auto-generated method stub\
        //System.out.println("pretExecute------");
        super.onPreExecute();
    }

   @Override
    protected void onProgressUpdate(TextView... values) {
        // TODO Auto-generated method stub
      // super.onProgressUpdate(values);
    }
   

	public static AlarmInfo compareAllStocks(StockMng stockMng, List<SinaStockInfo>  stockinfo ) {
		try{
				
			List<StockMng> listStocks = stockMng.getStocks();
			int i=0,j =0;

			for( i = 0; i< listStocks.size(); i++) {
				StockMng stock = listStocks.get(i);
				if( !stock.IsValid())
				{
					continue;
				}
				if( j>= stockinfo.size() )
				{
					//在运行中添加股票，可能出现列表中股票数大于已经取得的股票信息数，就需要跳过此次，下次循环就取到新的股价了。2013.5.18
					break;
				}
				SinaStockInfo stock1 = stockinfo.get(j++);
				int ret = stock.Compare(stock1.getNowPrice(), stock1.getYestodayPrice());

				if( CMP_RESULT_NONE != ret ) {
					// 返回预警信息，包含消息、股票索引和股票代码
					return new AlarmInfo(stock1.getName()+"-"+stock.GetCmpareResult(), i, stock.GetCode());
				}

			}


			return null;
		}
		catch (Exception e)
		{
			Log.e("compareAllStocks", "error: " + e.getMessage(), e);
			
			return null;
		}
	}


}

