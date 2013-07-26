package edu.cmu.hcii.novo.arbra;

import java.io.IOException;
import java.util.ArrayList;

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
import android.media.AudioManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.IBinder;
import android.speech.SpeechRecognizer;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.RelativeLayout;
import android.widget.Spinner;
import android.widget.TextView;

import com.metaio.sdk.MetaioDebug;
import com.metaio.tools.io.AssetsManager;

import edu.cmu.hcii.novo.arbra.AudioFeedbackView.AudioFeedbackThread;

public class MainActivity extends Activity {

	private DataUpdateReceiver dataUpdateReceiver;
	private static final String TAG = "MainActivity_client"; // used for logging
								
	private boolean showGUI = true;
	
	/** Dictionary trainer & IP address data storage **/
	public static final String PREFS_NAME = "ARbraPrefs";
	SharedPreferences prefs;
	public String ip_address;


	/** ConnectionService **/
	private ConnectionService mBoundConnectionService;
	private boolean mConnectionIsBound;

	/** Speech Recognizer Service **/
	private SpeechRecognizerService mBoundSpeechService;
	private boolean mSpeechIsBound;

	
	/** Speech recognition **/
	//private SpeechRecognizer speechRecognizer;
	private TextView textView;
	private ArrayList<String> data; // current results

	/** Audio feedback visualizer **/
	private AudioFeedbackView audioFeedbackView;
	private AudioFeedbackThread audioFeedbackThread;

	/** For muting Jellybean's audio feedback for SpeechRecognizer **/
	private AudioManager mAudioManager;
	
	/**
	 * Task that will extract all the assets
	 */
	private AssetsExtracter extractor;
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
		getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
		requestWindowFeature(Window.FEATURE_NO_TITLE);
		
		setContentView(R.layout.control_panel);
		Log.v(TAG, "onCreate");

		// to begin ConnectionService (connection to Moverio)
		if (!isSpeechServiceRunning())
			startService(new Intent(MainActivity.this, SpeechRecognizerService.class));
		if (!isConnectionServiceRunning())
			startService(new Intent(MainActivity.this, ConnectionService.class));
		doBindSpeechService();
		doBindConnectionService();

		// stores data on android tablet (in this case, we are storing the IP
		// address for future use)
		prefs = getSharedPreferences(PREFS_NAME, 0);
		ip_address = prefs.getString("machine_ip", "none");

		textView = (TextView) findViewById(R.id.textView);

		initSpeechButtons();
		initTrainerButtons();

		audioFeedbackView = (AudioFeedbackView) findViewById(R.id.visualizerView);
		audioFeedbackThread = audioFeedbackView.getThread();
		initAudioFeedbackButton();

		mAudioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
		//mAudioManager.setStreamMute(AudioManager.STREAM_SYSTEM, true);
		mAudioManager.setStreamSolo(AudioManager.STREAM_NOTIFICATION, true);
		
		// extract all the assets
		extractor = new AssetsExtracter();
		extractor.execute(0);
	}

	private void initSpeechButtons() {
		Button speechClose = (Button) findViewById(R.id.speechCloseButton);
		speechClose.setOnClickListener(new OnClickListener() {

			@Override
			public void onClick(View arg0) {
				mBoundSpeechService.cancelListener();
			}

		});
	}

	/**
	 * Initializes trainer buttons and widgets
	 * 
	 * @param act
	 * @param prefs
	 * @param data
	 */
	public void initTrainerButtons() {
		final Spinner commands = (Spinner) findViewById(R.id.commands);

		Button trainButton = (Button) findViewById(R.id.trainButton);
		trainButton.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View arg0) {
				String command = commands.getSelectedItem().toString();
				for (int i = 0; i < data.size(); i++) {
					TrainerFunctions.writeTrainer(command, data.get(i),
							MainActivity.this, prefs);
				}
			}

		});

		Button getTrainedButton = (Button) findViewById(R.id.getTrainedButton);
		getTrainedButton.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View arg0) {
				String command = commands.getSelectedItem().toString();
				TrainerFunctions.readTrainer(command, MainActivity.this, prefs);
				TrainerFunctions.exportTrainer(commands, command,
						MainActivity.this, prefs);
			}
		});

	}

	/**
	 * Initializes buttons for changing audio feedback visualizer type
	 */
	private void initAudioFeedbackButton() {
		Button b = (Button) findViewById(R.id.audioFeedbackButton);
		b.setOnClickListener(new OnClickListener() {

			@Override
			public void onClick(View arg0) {
				audioFeedbackView.shift = !audioFeedbackView.shift;
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
		// sets up data update receiver for receiving broadcast messages from
		// ConnectionService
		if (dataUpdateReceiver == null)
			dataUpdateReceiver = new DataUpdateReceiver();
		IntentFilter intentFilter = new IntentFilter();
		intentFilter.addAction("connection");
		intentFilter.addAction("speech");
		registerReceiver(dataUpdateReceiver, intentFilter);

		audioFeedbackThread.unpause();
		
	}

	@Override
	protected void onPause() {
		super.onPause();
		Log.v(TAG, "onPause");
		if (dataUpdateReceiver != null)
			unregisterReceiver(dataUpdateReceiver);
		audioFeedbackThread.pause();
		
		
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
		doUnbindConnectionService();
		doUnbindSpeechService();
		// The activity is about to be destroyed.
	}

	// Pressing the back button on the android device will perform this function
	/*
	 * @Override public void onBackPressed() { return; }
	 */

	@Override
	public void onSaveInstanceState(Bundle savedInstanceState) {
		super.onSaveInstanceState(savedInstanceState);
	}

	@Override
	public void onRestoreInstanceState(Bundle savedInstanceState) {
		super.onRestoreInstanceState(savedInstanceState);
	}

	/**
	 * Adds items to the menu bar (currently used for managing the receive
	 * socket)
	 */
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		Log.v(TAG, "menu create");
		menu.add("Connect");
		menu.add("AR Mode");
		return true;
	}

	/**
	 * Called when a menu item is selected. Menu currently only has one option,
	 * which starts the socket.
	 */
	public boolean onOptionsItemSelected(MenuItem item) {
		if (item.getTitle().equals("Connect"))
			startActivity(new Intent(this, ConnectionPopUp.class));
		else if (item.getTitle().equals("AR Mode")){
			//showGUI = false;
			//hideGUI();
			startActivity(new Intent(this, ARMode.class));
		}else if (item.getTitle().equals("Trainer Mode")){
			//showGUI = true;
			//showGUI();
		}
		return false;
	}

	/**
	 * Called any time the bottom menu pops up
	 */
	@Override
	public boolean onPrepareOptionsMenu(Menu menu) {
		Log.v(TAG, "menu prepare");
		if (showGUI)
			menu.getItem(1).setTitle("AR Mode");
		else
			menu.getItem(1).setTitle("Trainer Mode");

		super.onPrepareOptionsMenu(menu);
		return true;
	}
	
	/**
	 * 
	 */
	private void hideGUI(){
		RelativeLayout rl = (RelativeLayout) findViewById(R.id.cp1);
		rl.setVisibility(View.GONE);
		audioFeedbackThread.pause();
	}
	
	/**
	 * 
	 */
	private void showGUI(){
		RelativeLayout rl = (RelativeLayout) findViewById(R.id.cp1);
		rl.setVisibility(View.VISIBLE);
		audioFeedbackThread.unpause();
	}

	/**
	 * Declares service and connects
	 */
	private ServiceConnection mSpeechConnection = new ServiceConnection() {
		public void onServiceConnected(ComponentName className, IBinder service) {
			Log.v(TAG, "set mBoundSpeechService");
			mBoundSpeechService = ((SpeechRecognizerService.LocalBinder) service)
					.getService();
			if (!mBoundSpeechService.isConnected()) {
				mBoundSpeechService.startService();
			}
		}

		public void onServiceDisconnected(ComponentName className) {
			mBoundSpeechService = null;
		}
	};
	
	/**
	 * Declares service and connects
	 */
	private ServiceConnection mConnectionConnection = new ServiceConnection() {
		public void onServiceConnected(ComponentName className, IBinder service) {
			Log.v(TAG, "set mBoundConnectionService");
			mBoundConnectionService = ((ConnectionService.LocalBinder) service)
					.getService();
			if (!mBoundConnectionService.isConnected()) {
				mBoundConnectionService.connect(ip_address);
			}
		}

		public void onServiceDisconnected(ComponentName className) {
			mBoundConnectionService = null;
		}
	};
	


	/**
	 * Binds the service to the activity Allows access service
	 * functions/variables available to binded activities. (See LocalBinder
	 * class in service classes for accessible functions)
	 */
	private void doBindConnectionService() {
		Log.v(TAG, "bind connection service");
		bindService(new Intent(MainActivity.this, ConnectionService.class),
				mConnectionConnection, Context.BIND_AUTO_CREATE);
		mConnectionIsBound = true;
	}
	
	private void doBindSpeechService() {
		Log.v(TAG, "bind speech service");
		bindService(new Intent(MainActivity.this, SpeechRecognizerService.class),
				mSpeechConnection, Context.BIND_AUTO_CREATE);
		mSpeechIsBound = true;
	}


	/**
	 * Unbinds the service and activity
	 */
	private void doUnbindConnectionService() {
		if (mConnectionIsBound) {
			Log.v(TAG, "unbind service");
			// Detach our existing connection.
			unbindService(mConnectionConnection);
			mConnectionIsBound = false;
		}
	}
	private void doUnbindSpeechService() {
		if (mSpeechIsBound) {
			Log.v(TAG, "unbind service");
			// Detach our existing connection.
			unbindService(mSpeechConnection);
			mSpeechIsBound = false;
		}
	}
	
	/**
	 * Returns whether or not the service is running
	 * 
	 * @return true if connection service is running, false otherwise
	 */
	private boolean isConnectionServiceRunning() {
		ActivityManager manager = (ActivityManager) getSystemService(ACTIVITY_SERVICE);
		for (RunningServiceInfo service : manager
				.getRunningServices(Integer.MAX_VALUE)) {
			if (ConnectionService.class.getName().equals(
					service.service.getClassName())) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Returns whether or not the service is running
	 * 
	 * @return true if speech service is running, false otherwise
	 */
	private boolean isSpeechServiceRunning() {
		ActivityManager manager = (ActivityManager) getSystemService(ACTIVITY_SERVICE);
		for (RunningServiceInfo service : manager
				.getRunningServices(Integer.MAX_VALUE)) {
			if (SpeechRecognizerService.class.getName().equals(
					service.service.getClassName())) {
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
			Bundle b = intent.getExtras();

			if (intent.getAction().equals("connection")) {
				String msg = b.getString("msg");
				if (msg.equals("connected")) {
					// if ConnectionService confirms that the connection
					runOnUiThread(new Runnable() {
						public void run() {
							Log.v(TAG, "on receive - connected");
						}
					});
				}
				

			}else if (intent.getAction().equals("speech")){
				handleSpeechBroadcast(b);
			}
		}
	}
	
	/**
	 * Handles messages sent from speech recognizer service
	 * 
	 * @param b
	 */
	private void handleSpeechBroadcast(Bundle b){
		String type = b.getString("type");
		
		if (type.equals(SpeechRecognizerService.MSG_TYPE_COMMAND_LIST)){
			Bundle results = b.getBundle("results");
			outputSpeechResults(results);
			if (audioFeedbackThread.getCurState() == audioFeedbackView.STATE_ACTIVE)
				audioFeedbackThread.refreshThresholdLine();
			
		}else if (type.equals(SpeechRecognizerService.MSG_TYPE_AUDIO_BUSY)){
			boolean busyState = Boolean.parseBoolean(b.getString("msg"));
			audioFeedbackThread.setBusy(busyState);
		}else if (type.equals(SpeechRecognizerService.MSG_TYPE_AUDIO_LEVEL)){
			float rms = Float.parseFloat(b.getString("msg"));
			audioFeedbackView.updateAudioFeedbackView(rms);
		}else if (type.equals(SpeechRecognizerService.MSG_TYPE_AUDIO_ERROR)){
			String errorString = b.getString("msg");
			textView.setText("error:" + errorString);
			//audioFeedbackThread.refreshThresholdLine();
		}else if (type.equals(SpeechRecognizerService.MSG_TYPE_AUDIO_STATE)){
			int s = Integer.parseInt(b.getString("msg"));
			audioFeedbackThread.setState(s);
		}
		
	}
	
	/**
	 * Prints results from speech recognizer onto GUI TextView	
	 * @param results
	 */
	private void outputSpeechResults(Bundle results){
		String str = new String();

		/** For outputting text to UI **/
		data = results
				.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
		
		
		float[] scores = results
				.getFloatArray(SpeechRecognizer.CONFIDENCE_SCORES);
		for (int i = 0; i < data.size(); i++) {
			Log.d(TAG,
					"result "
							+ data.get(i)
							+ " = "
							+ TrainerFunctions.getTrainer(data.get(i),
									MainActivity.this, prefs) + scores[i]);
			str += data.get(i)
					+ " = "
					+ TrainerFunctions.getTrainer(data.get(i),
							MainActivity.this, prefs) + "\n";
		}
		textView.setText(str);
	}
	
	/**
	 * This task extracts all the assets to an external or internal location
	 * to make them accessible to metaio SDK
	 */
	private class AssetsExtracter extends AsyncTask<Integer, Integer, Boolean>
	{

		@Override
		protected void onPreExecute() 
		{
		}
		
		@Override
		protected Boolean doInBackground(Integer... params) 
		{
			try 
			{
				// TODO: Extract all assets and override existing files
				AssetsManager.extractAllAssets(getApplicationContext(), true);
			} 
			catch (IOException e) 
			{
				MetaioDebug.log(Log.ERROR, "Error extracting assets: "+e.getMessage());
				MetaioDebug.printStackTrace(Log.ERROR, e);
				return false;
			}
			
			return true;
		}		
	}
}
