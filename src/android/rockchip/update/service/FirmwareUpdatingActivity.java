package android.rockchip.update.service;

import android.content.IntentFilter;
import android.content.Context;
import android.os.Bundle;
import android.os.Environment;

import java.util.Formatter;
import java.util.Locale;
import java.lang.StringBuilder;

import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.TextView;
import android.content.DialogInterface;
import android.content.BroadcastReceiver;
import android.util.Log;
import android.content.Intent;
import android.app.Activity;
import android.os.Build;

public class FirmwareUpdatingActivity extends Activity {
    static final String TAG = "FirmwareUpdatingActivity";
    private static final boolean DEBUG = true;
    private Context mContext;

    private static void LOG(String msg) {
        if (DEBUG) {
            Log.d(TAG, msg);
        }
    }

    private String mImageFilePath;
    private String mImageVersion;
    private String mCurrentVersion;

    private static StringBuilder sFormatBuilder = new StringBuilder();
    private static Formatter sFormatter = new Formatter(sFormatBuilder, Locale.getDefault());

    /*-------------------------------------------------------*/

    private BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            LOG("mReceiver.onReceive() : 'action' =" + intent.getAction());

            if (intent.getAction() == Intent.ACTION_MEDIA_UNMOUNTED) {
                String path = intent.getData().getPath();
                LOG("mReceiver.onReceive() : original mount point : " + path + "; image file path : " + mImageFilePath);

                if (mImageFilePath != null && mImageFilePath.contains(path)) {
                    LOG("mReceiver.onReceive() : Media that img file live in is unmounted, to finish this activity.");
                    finish();
                }
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        LOG("onCreate() : Entered.");
        super.onCreate(savedInstanceState);

        mContext = this;
        requestWindowFeature(Window.FEATURE_LEFT_ICON);
        setContentView(R.layout.notify_dialog);
        setFinishOnTouchOutside(false);
        getWindow().setFeatureDrawableResource(Window.FEATURE_LEFT_ICON, android.R.drawable.ic_dialog_alert);
        setTitle(getString(R.string.updating_title));

        Bundle extr = getIntent().getExtras();
        mImageFilePath = extr.getString(RKUpdateService.EXTRA_IMAGE_PATH);
        String tempPath = mImageFilePath;
        Log.d(TAG, "FirmwareUpdatingActivity mImageFilePath=" + mImageFilePath);
        if (Build.VERSION.SDK_INT >= 30) {
            if (mImageFilePath.startsWith("/data/media")) {
                tempPath = mImageFilePath.replace("/data/media", "/storage/emulated");
            } else if (mImageFilePath.startsWith("/mnt/media_rw")) {
                tempPath = mImageFilePath.replace("/mnt/media_rw", "/storage");
            }

        }
        mImageFilePath = tempPath;
        Log.d(TAG, "FirmwareUpdatingActivity update mImageFilePath =" + mImageFilePath);

        mImageVersion = extr.getString(RKUpdateService.EXTRA_IMAGE_VERSION);
        mCurrentVersion = extr.getString(RKUpdateService.EXTRA_CURRENT_VERSION);

        String messageFormat = getString(R.string.updating_message_formate);
        sFormatBuilder.setLength(0);
        sFormatter.format(messageFormat, mImageFilePath);

        TextView txt = (TextView) this.findViewById(R.id.notify);
        txt.setText(sFormatBuilder.toString());

        Button btn_ok = (Button) this.findViewById(R.id.button_ok);
        Button btn_cancel = (Button) this.findViewById(R.id.button_cancel);
        btn_ok.setText(getString(R.string.updating_button_install));
        btn_cancel.setText(getString(R.string.updating_button_cancel));

        btn_ok.setOnClickListener(new View.OnClickListener() {

            public void onClick(View v) {
                Intent intent = new Intent(mContext, UpdateAndRebootActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                intent.putExtra(RKUpdateService.EXTRA_IMAGE_PATH, mImageFilePath);
                startActivity(intent);

                finish();
            }
        });

        btn_cancel.setOnClickListener(new View.OnClickListener() {

            public void onClick(View v) {
                finish();
            }
        });

        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_MEDIA_UNMOUNTED);
        filter.addDataScheme("file");
        registerReceiver(mReceiver, filter);
    }

    @Override
    protected void onPause() {
        super.onPause();
        LOG("onPause() : Entered.");
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        LOG("onDestroy() : Entered.");

        unregisterReceiver(mReceiver);
    }

}
