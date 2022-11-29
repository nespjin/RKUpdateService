/* 
**
** Copyright 2007, The Android Open Source Project
**
** Licensed under the Apache License, Version 2.0 (the "License"); 
** you may not use this file except in compliance with the License. 
** You may obtain a copy of the License at 
**
**     http://www.apache.org/licenses/LICENSE-2.0 
**
** Unless required by applicable law or agreed to in writing, software 
** distributed under the License is distributed on an "AS IS" BASIS, 
** WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. 
** See the License for the specific language governing permissions and 
** limitations under the License.
*/

package android.rockchip.update.service;

import android.content.Context;
import android.content.Intent;
import android.content.BroadcastReceiver;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.net.wifi.WifiManager;
import android.os.Environment;
import android.os.Bundle;
import android.os.Message;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;
import android.widget.Button;
import android.content.Intent;
import android.view.View;
import android.hardware.usb.UsbManager;
import android.app.Activity;
import java.io.File;
import android.util.Log;

public class RKUpdateReceiver extends BroadcastReceiver
{
    private final static String TAG = "RKUpdateReceiver";
    private static boolean isBootCompleted = false;

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        Log.d(TAG, "action = " + action);
        Intent serviceIntent;
        if (action.equals(Intent.ACTION_BOOT_COMPLETED)) {
            Log.d(TAG, "RKUpdateReceiver recv ACTION_BOOT_COMPLETED.");
            //serviceIntent = new Intent("android.rockchip.update.service");
            serviceIntent = new Intent(context, RKUpdateService.class);
            serviceIntent.putExtra("command", RKUpdateService.COMMAND_CHECK_LOCAL_UPDATING);
            serviceIntent.putExtra("delay", 20000);
            context.startService(serviceIntent);
            
            //serviceIntent = new Intent("android.rockchip.update.service");
            serviceIntent = new Intent(context, RKUpdateService.class);
            serviceIntent.putExtra("command", RKUpdateService.COMMAND_CHECK_REMOTE_UPDATING);
            serviceIntent.putExtra("delay", 25000);
            context.startService(serviceIntent);
            
            isBootCompleted = true;
        }else if( action.equals(Intent.ACTION_MEDIA_MOUNTED) && isBootCompleted) {              
            String[] path = { intent.getData().getPath() };
            //serviceIntent = new Intent("android.rockchip.update.service");
            serviceIntent = new Intent(context, RKUpdateService.class);
            serviceIntent.putExtra("command", RKUpdateService.COMMAND_CHECK_LOCAL_UPDATING);
            serviceIntent.putExtra("delay", 5000);
            context.startService(serviceIntent);
            Log.d(TAG, "media is mounted to '" + path[0] + "'. To check local update." );
 
        }else if(action.equals(UsbManager.ACTION_USB_STATE) && isBootCompleted) {
        	Bundle extras = intent.getExtras();
            boolean connected = extras.getBoolean(UsbManager.USB_CONNECTED);
            boolean configured = extras.getBoolean(UsbManager.USB_CONFIGURED);
            boolean mtpEnabled = extras.getBoolean(UsbManager.USB_FUNCTION_MTP);
            boolean ptpEnabled = extras.getBoolean(UsbManager.USB_FUNCTION_PTP);
            // Start MTP service if USB is connected and either the MTP or PTP function is enabled
            if ((!connected) && mtpEnabled && (!configured)) {
            	//serviceIntent = new Intent("android.rockchip.update.service");
            	serviceIntent = new Intent(context, RKUpdateService.class);
                serviceIntent.putExtra("command", RKUpdateService.COMMAND_CHECK_LOCAL_UPDATING);
                serviceIntent.putExtra("delay", 5000);
                context.startService(serviceIntent);
            }
        }else if(action.equals(ConnectivityManager.CONNECTIVITY_ACTION) && isBootCompleted) {
        	ConnectivityManager cmanger = (ConnectivityManager)context.getSystemService(context.CONNECTIVITY_SERVICE);
        	NetworkInfo netInfo = cmanger.getActiveNetworkInfo();
        	if(netInfo != null) {
        		if((netInfo.getType() == ConnectivityManager.TYPE_WIFI || netInfo.getType() == ConnectivityManager.TYPE_ETHERNET)
        				&& netInfo.isConnected()) {
	        		//serviceIntent = new Intent("android.rockchip.update.service");
        			serviceIntent = new Intent(context, RKUpdateService.class);
	                serviceIntent.putExtra("command", RKUpdateService.COMMAND_CHECK_REMOTE_UPDATING);
	                serviceIntent.putExtra("delay", 5000);
	                context.startService(serviceIntent);
        		}
        	}
        }
    }
}


