package alarm.stock;

import android.os.Bundle;
import android.app.Activity;
import android.content.Intent;
import android.view.Menu;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

public class Help extends Activity {
	
	private static final CharSequence help_str = "    比如您要对万科A设置预警，假设万科A现价为11.91元，您想在它的股价达到13元，或跌到11.5元，或者当涨幅为5%，或者跌幅为4%时通知您，那么您可做如下设置。";
	private static final CharSequence help_str2 = "输入完成股票代码和设定值后点击'添加预警'。\n    这些条件可以同时设置，也可以只设置其中一部分，不要的条件设置为0即可。\n    股票添加完毕后点击'开始'，程序运行时如下图。";
	private static final CharSequence help_str3 = "    如果您想删除或临时暂停某支股票预警，可以长按该支股票不放，在弹出菜单中选择相应操作。\n    软件智能检测：只在周一至周五的股票交易时间检测，非交易时间不检测。所以您无需退出软件。";

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.help);
		
		TextView text1 = (TextView) findViewById(R.id.help_text);
		text1.setText(help_str);
		
		/* 取得 Button、ImageView对象 */
		ImageView mImageView01;
		mImageView01 = (ImageView) findViewById(R.id.help_img);
		/* 设置ImageView背景图 */
		mImageView01.setImageDrawable(getResources().getDrawable(R.drawable.help) );

		TextView text2 = (TextView) findViewById(R.id.help_text2);		
		text2.setText(help_str2);
		
		/* 取得 Button、ImageView对象 */
		ImageView mImageView02;
		mImageView02 = (ImageView) findViewById(R.id.help_img2);
		/* 设置ImageView背景图 */
		mImageView02.setImageDrawable(getResources().getDrawable(R.drawable.help2) );

		TextView text3 = (TextView) findViewById(R.id.help_text3);		
		text3.setText(help_str3);		
		
		
		//返回按钮
		Button btn_ret =(Button)findViewById(R.id.btn_return);
		btn_ret.setOnClickListener(new View.OnClickListener() {
			public void onClick(View v) {
	            //Intent intent=new Intent();   
	            //intent.setClass(Help.this, AlarmActivity.class);   
	            //startActivity(intent);   
	            Help.this.finish();   
			}
		});
	}

	/*
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.help, menu);
		return true;
	}
	*/

}
