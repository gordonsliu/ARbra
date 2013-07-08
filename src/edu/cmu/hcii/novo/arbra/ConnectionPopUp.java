package edu.cmu.hcii.novo.arbra;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.WindowManager.LayoutParams;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

// We enter this activity when the connection menu button is pressed. This activity can tell ConnectionService to
// begin a socket connection and displays connection information.
public class ConnectionPopUp extends Activity{
	private String TAG ="ConnectionPopUp";
	
	// connection service
	protected ConnectionService mBoundService;
	private boolean mIsBound= false;
	
	// shared preferences (for storing ip_address data)
	public static final String PREFS_NAME = "SasetPrefs";
	SharedPreferences prefs;
	private MainApp MainApp;

	// ip address
	private String ip_address;
	
	// User interface views
	private EditText ip;
	private TextView mResultText;
	private TextView connectedText;

	
	private DataUpdateReceiver dataUpdateReceiver;
	
	@Override
	public void onCreate(Bundle savedInstanceState) {
	    super.onCreate(savedInstanceState);
	    // these flags allow touches outside of the ConnectionPopUp to be detected. In this case, outside touches will close the pop-up.
	    getWindow().setFlags(LayoutParams.FLAG_NOT_TOUCH_MODAL, LayoutParams.FLAG_NOT_TOUCH_MODAL);
	    getWindow().setFlags(LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH, LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH);
	    Log.v(TAG, "onCreate");
	    
	    setContentView(R.layout.connect_popup);
	    setTitle(R.string.Connection_Settings);
	
	    // binds connectionService to access its connection functions
	    if (!mIsBound)
	    	doBindService();
	    
	    // opens shared preferences to see if there is already an ip address saved on the android device
	    prefs = getSharedPreferences(PREFS_NAME, 0);
        ip_address = prefs.getString("machine_ip","none");
        
        // so we can access variables within controlPanelApp
        MainApp = (MainApp) ConnectionPopUp.this.getApplication();   
	}	

	  @Override
	  public boolean onTouchEvent(MotionEvent event) {
	    // If we've received a touch notification that the user has touched outside the pop-up, finish and close the activity.
	    if (MotionEvent.ACTION_OUTSIDE == event.getAction()) {
	      finish();
	      return true;
	    }
	    
	    // Delegate everything else to Activity.
	    return super.onTouchEvent(event);
	  }
	  
	// initializes and gets pointers to the user interface widgets, texts, colors, and values
	private void initValues() {
		ip = (EditText) this.findViewById(R.id.IP_String);
		mResultText = (TextView) this.findViewById(R.id.Disconnect_Msg);
		connectedText = (TextView) this.findViewById(R.id.ConnectStatus);
	
		if (ip_address.equals("none")){ // if there is no ip address already stored on the android
			mResultText.setText(R.string.Connect_Msg1); // then ask the user for the IP address
		}else{ 
			ip.setText(ip_address); // otherwise, place the stored ip address into the input text box
		}
		
		// if the connection is active, display "Connected" as status, "Not Connected" otherwise
	   	if (!mBoundService.isConnected()){
	   		MainApp.connected = false;
			connectedText.setText(R.string.NotConnected);
			connectedText.setTextColor(Color.parseColor("#fd6e6e"));
	   	}else{
	   		MainApp.connected = true;
	   		connectedText.setText(R.string.Connected);
			connectedText.setTextColor(Color.parseColor("#4bdf71"));
			mResultText.setText(R.string.Connect_Msg4);
	   	}
		
		Button connectButton = (Button) this.findViewById(R.id.Connect);
		Button backButton = (Button) this.findViewById(R.id.Back);
		connectButton.setOnClickListener(connect_button_listener);
		backButton.setOnClickListener(back_button_listener);
	}
	
	// handles the behavior of the connect button
	private OnClickListener connect_button_listener = new OnClickListener() {
	    	public void onClick(View v) {
	    		ip_address = ip.getText().toString();
	    		// places the ip_address
	        	SharedPreferences.Editor editor = prefs.edit();
	        	editor.putString("machine_ip", ip_address);
	        	editor.commit();
	        	
	    		mBoundService.connect(ip_address);
	    		startActivity(new Intent(mBoundService, ConnectionPopUp.class));
	    		
	    		onBackPressed();
	    	}
	    };
	
	// handles the behavior of the back button
    private OnClickListener back_button_listener = new OnClickListener() {
    	public void onClick(View v) {
    		finish(); // closes activity
    	}
    };
	    
    // sets up the bound service connection
    private ServiceConnection mConnection = new ServiceConnection() {
    	public void onServiceConnected(ComponentName className, IBinder service) {
        	 Log.v("set mBoundService", "hello");
             mBoundService = ((ConnectionService.LocalBinder)service).getService();
             initValues();
        }
        public void onServiceDisconnected(ComponentName className) {
             mBoundService = null;
        }
    };
    
    @Override
	protected void onStart() {
        super.onStart();
        Log.v(TAG, "onStart");
        // The activity is about to become visible.
       
    }
    @Override
    protected void onResume() {
        super.onResume();
        Log.v(TAG, "onResume");
        // The activity has become visible (it is now "resumed").
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
    
    private void doBindService() {
    	Log.v(TAG, "bind service");
        bindService(new Intent(ConnectionPopUp.this, ConnectionService.class), mConnection, Context.BIND_AUTO_CREATE);
        mIsBound = true;
    }


    private void doUnbindService() {
        if (mIsBound) {
        	Log.v(TAG, "unbind service");
            // Detach our existing connection.
            unbindService(mConnection);
            mIsBound = false;
        }
    }

    private class DataUpdateReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
        	Log.v(TAG, "on receive");
            if (intent.getAction().equals("connection")) {
            	Bundle b = intent.getExtras();
            	String msg = b.getString("msg");
            	if (msg.equals("connected")){
                // if ConnectionService confirms that the connection has been established
	            	runOnUiThread(new Runnable() {
	            	      public void run() { 
	            	    	  Log.v(TAG, "on receive - connected");
	            		   		MainApp.connected = true;
	            		   		connectedText.setText(R.string.Connected);
	            				connectedText.setTextColor(Color.parseColor("#4bdf71"));
	            				mResultText.setText(R.string.Connect_Msg4);
	            	      }
	            	    });
	            }else if (msg.equals("disconnected")) {
		        // if ConnectionService says a disconnect has occurred
	                 runOnUiThread(new Runnable(){
	                	  public void run() { 
	                		   Log.v(TAG, "on receive - disconnected");
	               	   		MainApp.connected = false;
	            			connectedText.setText(R.string.NotConnected);
	            			connectedText.setTextColor(Color.parseColor("#fd6e6e"));
	                	  }
	                	 });
	            }
            	
          }
        }
    }
    
}
