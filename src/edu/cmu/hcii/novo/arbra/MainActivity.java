package edu.cmu.hcii.novo.arbra;


import org.json.JSONException;
import org.json.JSONObject;

import edu.cmu.hcii.novo.arbra.R;
import android.os.Bundle;
import android.os.IBinder;
import android.app.Activity;
import android.app.ActivityManager;
import android.app.ActivityManager.RunningServiceInfo;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;

public class MainActivity extends Activity {

	private DataUpdateReceiver dataUpdateReceiver;
	private static final String TAG = "MainActivity_client";	// used for logging purposes
	
	private MainApp MainApp;

	//IP address data storage
	public static final String PREFS_NAME = "moverioPrefs";
	SharedPreferences prefs;
	public String ip_address;
	
	
	/**To prevent "spamming" of control panel commands: **/
	public long lastActionTime = 0; // the time of the last command sent by the android device.
	private static int MIN_ACTION_TIME = 150; // the minimum amount of time (ms) that must pass before the android device can send another command

	
	/** ConnectionService **/
	private ConnectionService mBoundService;
	private boolean mIsBound;
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.control_panel);
        Log.v(TAG, "onCreate");
        
        // to view variables stored in MainApp
        MainApp = (MainApp) MainActivity.this.getApplication();   

        // to begin ConnectionService (connection to ultrasound system)
       	if (!isMyServiceRunning())
       		startService(new Intent(MainActivity.this,ConnectionService.class));
       	doBindService();

		
       	// stores data on android tablet (in this case, we are storing the IP address of ultrasound system for future use)
       	prefs = getSharedPreferences(PREFS_NAME, 0);
        ip_address = prefs.getString("machine_ip","none");

       	
	}
	
    // The activity is about to become visible.
    @Override
    protected void onStart() {
        super.onStart();
        Log.v(TAG, "onStart");   
    }
	
    // The activity has become visible (it is now "resumed").     
    @Override
    protected void onResume() {
        super.onResume();
        Log.v(TAG, "onResume");
        // sets up data update receiver for receiving broadcast messages from ConnectionService
        if (dataUpdateReceiver == null) 
        	dataUpdateReceiver = new DataUpdateReceiver();
        IntentFilter intentFilter = new IntentFilter("connection");
        registerReceiver(dataUpdateReceiver, intentFilter);
    }
    
    @Override
    protected void onPause(){
    	super.onPause();
    	Log.v(TAG, "onPause");
    	if (dataUpdateReceiver != null) 
    		unregisterReceiver(dataUpdateReceiver);
    }
    
    @Override
    protected void onStop() {
        super.onStop();
        Log.v(TAG, "onStop");
        // The activity is no longer visible (it is now "stopped")
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.v(TAG, "onDestroy");
        doUnbindService();
        // The activity is about to be destroyed.
    }
    
    // Pressing the back button on the android device will perform this function
    /*@Override
    public void onBackPressed() {
    	return;
    }*/
    
    
    @Override
    public void onSaveInstanceState(Bundle savedInstanceState) {
    	super.onSaveInstanceState(savedInstanceState);
    }
     
    @Override
    public void onRestoreInstanceState(Bundle savedInstanceState) {
    	super.onRestoreInstanceState(savedInstanceState);
    }


	// Adds items to the menu bar (currently used for managing the receive socket)
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
	    Log.v(TAG, "menu create");
		menu.add("Connect");		
		return true;
	}
	
	// Called when a menu item is selected (starts the socket)
	public boolean onOptionsItemSelected (MenuItem item){
        startActivity(new Intent(this, ConnectionPopUp.class));	
		return false;
	}
	
	// Called any time the bottom menu pops up
	@Override
	public boolean onPrepareOptionsMenu(Menu menu){
	   	 Log.v(TAG, "menu prepare");

     super.onPrepareOptionsMenu(menu);
   	 return true;
	}
	
    // declares service and connects
    private ServiceConnection mConnection = new ServiceConnection() {
    	public void onServiceConnected(ComponentName className, IBinder service) {
        	 Log.v("TAG", "set mBoundService");
            mBoundService = ((ConnectionService.LocalBinder)service).getService();
           	if (!mBoundService.isConnected()){
           		mBoundService.connect(ip_address);
           	}
        }
        public void onServiceDisconnected(ComponentName className) {
            mBoundService = null;
        }
    };

    // Binds the service to the activity 
    // Allows access service functions/variables available to binded activities.
    	// See LocalBinder class in ConnectionService
    private void doBindService() {
    	Log.v(TAG, "bind service");
        bindService(new Intent(MainActivity.this, ConnectionService.class), mConnection, Context.BIND_AUTO_CREATE);
        mIsBound = true;
    }


    // unbinds the service and activity
    private void doUnbindService() {
        if (mIsBound) {
        	Log.v(TAG, "unbind service");
            // Detach our existing connection.
            unbindService(mConnection);
            mIsBound = false;
        }
    }
    
    // returns whether or not the service is running
    private boolean isMyServiceRunning() {
        ActivityManager manager = (ActivityManager) getSystemService(ACTIVITY_SERVICE);
        for (RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
            if (ConnectionService.class.getName().equals(service.service.getClassName())) {
                return true;
            }
        }
        return false;
    }
	
	// Listens to broadcast messages
    private class DataUpdateReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
        	Log.v(TAG, "on receive");
            if (intent.getAction().equals("connection")) {
            	Bundle b = intent.getExtras();
            	String msg = b.getString("msg");
            	if (msg.equals("connected")){
                // if ConnectionService confirms that the connection to the ultrasound system, the connection icon on the top right turns green
	            	runOnUiThread(new Runnable() {
	            	      public void run() { 
	            	    	  Log.v(TAG, "on receive - connected");
	            	      }
	            	    });
	            }
            	
          }
        }
    }
    
    
    
    public void send_1(View view) throws JSONException{
    	JSONObject j = new JSONObject();
    	j.put("type", "command");
    	j.put("content", "1");
 
    	sendMsg(j.toString());
    }
    public void send_2(View view) throws JSONException{
    	JSONObject j = new JSONObject();
    	j.put("type", "command");
    	j.put("content", "2");
 
    	sendMsg(j.toString());
    }

    
    /**
     * Call this function to send a message to the Moverio
     * @param msg
     * @return
     */
    private boolean sendMsg(String msg){
    	long curActionTime = System.currentTimeMillis();
    	
    	// to prevent "spamming" of control panel commands    	
    	if (curActionTime - lastActionTime < MIN_ACTION_TIME){
    		Log.v("action time filter", "skip");
    		return false;
    	}   			
    	mBoundService.sendMsg(msg);	
    	Log.v("action time filter", "sent");
    	lastActionTime = curActionTime;
    	return true;
    }

}
