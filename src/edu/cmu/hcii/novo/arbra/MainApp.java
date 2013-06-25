package edu.cmu.hcii.novo.arbra;

import android.app.Application;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;

public class MainApp extends Application{
	
	public boolean connected = false;

	
	private DataUpdateReceiver dataUpdateReceiver; // receives broadcast messages from ConnectionService
	private static String TAG = "ControlPanelApp";
	
	@Override
	public void onCreate(){
		super.onCreate();
		// sets up data update receiver on creation
		if (dataUpdateReceiver == null) 
        	dataUpdateReceiver = new DataUpdateReceiver();
        IntentFilter intentFilter = new IntentFilter("connection");
        registerReceiver(dataUpdateReceiver, intentFilter);
	}
 
    // receives broadcast messages from ConnectionService
    private class DataUpdateReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
        	Log.v(TAG, "onReceive");
            if (intent.getAction().equals("connection")) {
            	Bundle b = intent.getExtras();
            	String msg = b.getString("msg");
	            if (msg.equals("connected")) {
	            	 connected = true;
	            }else if (msg.equals("disconnected")) {
	                 connected = false;
	            }
            }
            
        }
    }
    

}
