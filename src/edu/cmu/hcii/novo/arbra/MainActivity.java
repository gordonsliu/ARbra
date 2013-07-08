package edu.cmu.hcii.novo.arbra;


import java.util.ArrayList;

import org.json.JSONException;
import org.json.JSONObject;

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
import android.media.MediaRecorder;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.TextView;

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
	
	
	/** Speech recognition **/
	private SpeechRecognizer speechRecognizer;
	private TextView textView;
	
	/* For handling occasions where speechRecognizer doesn't not call onBeginningOfSpeech */
	private long silenceStart;
	private boolean talked = false;
	
	/* For handling confirmation string */
	private boolean confirm = false;
	private String CONFIRMATION_STRING = "hello";
	
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
		setContentView(R.layout.control_panel);
        Log.v(TAG, "onCreate");
        
        // to view variables stored in MainApp
        MainApp = (MainApp) MainActivity.this.getApplication();   

        // to begin ConnectionService (connection to Moverio)
       	if (!isMyServiceRunning())
       		startService(new Intent(MainActivity.this,ConnectionService.class));
       	doBindService();

		
       	// stores data on android tablet (in this case, we are storing the IP address for future use)
       	prefs = getSharedPreferences(PREFS_NAME, 0);
        ip_address = prefs.getString("machine_ip","none");

        textView = (TextView) findViewById(R.id.textView);
        
        initSpeechRecognizer();
        initTalkButton();
        MediaRecorder mr = new MediaRecorder();
        
	}
	
	/**
	 * Initializes speech recognizer
	 */
    private void initSpeechRecognizer(){
    	speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this);
        speechRecognizer.setRecognitionListener(new RecognitionListener(){

			@Override
			public void onBeginningOfSpeech() {
				Log.v(TAG, "onBeginningOfSpeech");				
			}

			@Override
			public void onBufferReceived(byte[] arg0) {
				Log.v(TAG, "onBufferReceived");				
			}

			@Override
			public void onEndOfSpeech() {
				Log.v(TAG, "onEndOfSpeech");				
			}

			@Override
			public void onError(int error) {
            	confirm = false;

				String errorString ="";
				if (error == SpeechRecognizer.ERROR_NETWORK_TIMEOUT){
					errorString = "ERROR_NETWORK_TIMEOUT";
				}else if (error == SpeechRecognizer.ERROR_AUDIO){
					errorString = "ERROR_AUDIO";
				}else if (error == SpeechRecognizer.ERROR_CLIENT){
					errorString = "ERROR_CLIENT";
				}else if (error == SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS){
					errorString = "ERROR_INSUFFICIENT_PERMISSIONS";
				}else if (error == SpeechRecognizer.ERROR_NETWORK){
					errorString = "ERROR_NETWORK";
				}else if (error == SpeechRecognizer.ERROR_NO_MATCH){
					errorString = "ERROR_NO_MATCH";
				}else if (error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY){
					errorString = "ERROR_RECOGNIZER_BUSY";
				}else if (error == SpeechRecognizer.ERROR_SERVER){
					errorString = "ERROR_SERVER";
				}else if (error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT){
					errorString = "ERROR_SPEECH_TIMEOUT";
				}
                textView.setText("error:" + errorString);
                Log.v(TAG, "onError:" + errorString);	
                
            	startListening();

                
               // cancelListener();
                /*final Handler handler = new Handler();
                final Runnable runnable = new Runnable() {   
                    public void run() {
                    	startListening();
                    	//initSpeechRecognizer();
                    	handler.postDelayed(this, 5000);
                    	
                    } 
                };
                handler.post(runnable);
                */
                
			}

			@Override
			public void onEvent(int arg0, Bundle arg1) {
				Log.v(TAG, "onEvent");				
			}

			@Override
			public void onPartialResults(Bundle arg0) {
				Log.v(TAG, "onPartialResults");				
			}

			@Override
			public void onReadyForSpeech(Bundle arg0) {
				Log.v(TAG, "onReadyForSpeech");				
			}

			/**
			 * This function is called when receiving a result
			 */
			@Override
			public void onResults(Bundle results) {
				talked = false;
				Log.v(TAG, "onResults");		
                String str = new String();
                Log.d(TAG, "onResults " + results);
                                
                ArrayList<String> data = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                float[] scores =
                        results.getFloatArray(SpeechRecognizer.CONFIDENCE_SCORES);
                for (int i = 0; i < data.size(); i++)
                {
                          Log.d(TAG, "result " + data.get(i) + scores[i]);
                          str += data.get(i);
                }
                textView.setText(str);
                
                if (confirm){
                	confirm = false;
	                try {
						sendCommand(str);
					} catch (JSONException e) {
						e.printStackTrace();
					}
                }else if (str.equals(CONFIRMATION_STRING)){
                	confirm = true;
                }
                startListening();
			}

			@Override
			public void onRmsChanged(float noise) {
				Log.v(TAG, "onRmsChanged: " + noise);	
				
				if (noise > 8){
					talked = true;
				}else if (talked && noise < 3){
					if (silenceStart == 0)
						silenceStart = System.currentTimeMillis();
					else if (System.currentTimeMillis() - silenceStart > 2000){
						silenceStart = 0;
						talked = false;
						speechRecognizer.stopListening();
					}
				}else{
					silenceStart = 0;
				}
			}
        	
        });
    }

    /**
     * Tells the speech recognizer to start listening
     */
    private void startListening(){
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);        
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,RecognizerIntent.LANGUAGE_MODEL_WEB_SEARCH);
        intent.putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE,"voice.recognition.test");
        //intent.putExtra(RecognizerIntent.EXTRA_CONFIDENCE_SCORES, true);

        intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS,5); 
        speechRecognizer.startListening(intent);
             Log.i("111111","11111111");
    }
    
    /**
     * Tells the speech recognizer to reset
     */
    private void cancelListener(){
    	speechRecognizer.cancel();
    }
	
	private void initTalkButton(){
		Button talkButton = (Button) findViewById(R.id.talkButton);
		talkButton.setOnClickListener(new OnClickListener(){
			@Override
			public void onClick(View arg0) {
				startListening();
			}
		});
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


	/**
	 * Adds items to the menu bar (currently used for managing the receive socket)
	 */
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
	    Log.v(TAG, "menu create");
		menu.add("Connect");		
		return true;
	}
	
	/**
	 * Called when a menu item is selected.
	 * Menu currently only has one option, which starts the socket.
	 */
	public boolean onOptionsItemSelected (MenuItem item){
        startActivity(new Intent(this, ConnectionPopUp.class));	
        
		return false;
	}
	
	/**
	 * Called any time the bottom menu pops up
	 */
	@Override
	public boolean onPrepareOptionsMenu(Menu menu){
	   	 Log.v(TAG, "menu prepare");

     super.onPrepareOptionsMenu(menu);
   	 return true;
	}
	
    /**
     *  Declares service and connects
     */
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

    /**
     * 	Binds the service to the activity 
   	 *	Allows access service functions/variables available to binded activities.
     *	(See LocalBinder class in ConnectionService for accessible functions)
     */
    private void doBindService() {
    	Log.v(TAG, "bind service");
        bindService(new Intent(MainActivity.this, ConnectionService.class), mConnection, Context.BIND_AUTO_CREATE);
        mIsBound = true;
    }


    /**
     *  Unbinds the service and activity
     */
    private void doUnbindService() {
        if (mIsBound) {
        	Log.v(TAG, "unbind service");
            // Detach our existing connection.
            unbindService(mConnection);
            mIsBound = false;
        }
    }
    
    /**
     * Returns whether or not the service is running
     * @return true if connection service is running, false otherwise
     */
    private boolean isMyServiceRunning() {
        ActivityManager manager = (ActivityManager) getSystemService(ACTIVITY_SERVICE);
        for (RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
            if (ConnectionService.class.getName().equals(service.service.getClassName())) {
                return true;
            }
        }
        return false;
    }
	
	/**
	 * Listens to broadcast messages
	 */
    private class DataUpdateReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
        	Log.v(TAG, "on receive");
            if (intent.getAction().equals("connection")) {
            	Bundle b = intent.getExtras();
            	String msg = b.getString("msg");
            	if (msg.equals("connected")){
                // if ConnectionService confirms that the connection
	            	runOnUiThread(new Runnable() {
	            	      public void run() { 
	            	    	  Log.v(TAG, "on receive - connected");
	            	      }
	            	});
	            }
            	
          }
        }
    }
    
    
    /*
     * Sends test input
     */
    public void send_1(View view) throws JSONException{
    	JSONObject j = new JSONObject();
    	j.put("type", "command");
    	j.put("content", "1");
 
    	sendMsg(j.toString());
    }
    
    /*
     * Sends test input
     */
    public void send_2(View view) throws JSONException{
    	JSONObject j = new JSONObject();
    	j.put("type", "command");
    	j.put("content", "2");
 
    	sendMsg(j.toString());
    }

    
    /**
     * Call this function to send a message to the Moverio
     * @param msg
     * @return true if message was sent; false otherwise
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


    /**
     * Call this function to send a command to the Moverio
     * @param msg
     * @return true if message was sent; false otherwise
     * @throws JSONException 
     */
    private boolean sendCommand(String msg) throws JSONException{
    	long curActionTime = System.currentTimeMillis();
    	JSONObject j = new JSONObject();
    	j.put("type", "command");
    	j.put("content", msg);
    	
    	// to prevent "spamming" of control panel commands    	
    	if (curActionTime - lastActionTime < MIN_ACTION_TIME){
    		Log.v("action time filter", "skip");
    		return false;
    	}   			
    	mBoundService.sendMsg(j.toString());	
    	Log.v("action time filter", "sent");
    	lastActionTime = curActionTime;
    	return true;
    }

}
