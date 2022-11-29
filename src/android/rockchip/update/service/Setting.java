package android.rockchip.update.service;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.os.Bundle;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.ImageButton;
import android.widget.Switch;
import android.widget.TextView;

public class Setting extends Activity {
	private static final String TAG = "RKUpdateService.Setting";
	private Context mContext;
	private ImageButton mBtn_CheckNow;
	private SharedPreferences mAutoCheckSet;
	private TextView mTxtProduct;
	private TextView mTxtVersion;
	
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		requestWindowFeature(Window.FEATURE_NO_TITLE);
		setContentView(R.layout.setting);
		mContext = this;
		mBtn_CheckNow = (ImageButton)this.findViewById(R.id.btn_check_now);
		mTxtProduct = (TextView)this.findViewById(R.id.txt_product);
		mTxtVersion = (TextView)this.findViewById(R.id.txt_version);
		mTxtProduct.setText(RKUpdateService.getOtaProductName());
		mTxtVersion.setText(RKUpdateService.getSystemVersion());
		
		mAutoCheckSet = getSharedPreferences("auto_check", MODE_PRIVATE);
		
		mBtn_CheckNow.setOnClickListener(new View.OnClickListener() {

			@Override
			public void onClick(View v) {
				Intent serviceIntent;
				serviceIntent = new Intent(mContext, RKUpdateService.class);
                serviceIntent.putExtra("command", RKUpdateService.COMMAND_CHECK_REMOTE_UPDATING_BY_HAND);
                mContext.startService(serviceIntent);
			}
			
		});
	}
	
}
