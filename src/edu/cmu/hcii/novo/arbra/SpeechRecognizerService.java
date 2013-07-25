package edu.cmu.hcii.novo.arbra;

import java.util.ArrayList;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.os.Binder;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.util.Log;

public class SpeechRecognizerService extends Service {
	private static String TAG = "SpeechRecognizerService";
	
	/* Binder for activities to access functions */
	private final IBinder myBinder = new LocalBinder();
	
	/* Variables for speech recognizer timeout*/
	public static int MINIMUM_RMS_REFRESH = 7;
	public static long COMMANDS_TIMEOUT_DURATION = 4000; // in millesconds
	public long lastRefreshTime;
	
	public int STATE_INACTIVE = 0;
	public int STATE_ACTIVE = 1;
    private int state = STATE_INACTIVE;
	public boolean busy = false;
	private boolean speechOn = false;
	
	/* Name of commands sent to ConnectionService and MainActivity */
	public static String MSG_TYPE_COMMAND = "command";
	public static String MSG_TYPE_AUDIO_LEVEL = "audioLevel";
	public static String MSG_TYPE_AUDIO_BUSY = "audioBusy";
	/* Name of commands sent to MainActivity */
	public static String MSG_TYPE_AUDIO_ERROR = "audioError";
	public static String MSG_TYPE_AUDIO_STATE = "audioState";
	public static String MSG_TYPE_COMMAND_LIST = "commandList";

	
	/* For handling confirmation string */
	private String CONFIRMATION_STRING = "ready";
	
	/* Speech recognition */
	private SpeechRecognizer speechRecognizer;
	private long lastSpeechRecognizerActionTime;

	/*
	 * For handling occasions where speechRecognizer doesn't not call
	 * onBeginningOfSpeech
	 */
	private long silenceStart;
	private boolean talked = false;
	
	/** To prevent "spamming" of onRmsChanged: **/
	public long lastActionTime = 0; // the time of the last message sent.
	private static int MIN_ACTION_TIME = 150; // the minimum amount of time (ms)
												// that must pass before another
												// voice rms update is sent
	
	/** Dictionary trainer data storage **/
	public static final String PREFS_NAME = "ARbraPrefs";
	SharedPreferences prefs;

	private boolean serviceStarted = false;
	
	/** For muting Jellybean's audio feedback for SpeechRecognizer **/
	private AudioManager mAudioManager;
	
	
    @Override
    public void onCreate() {
        super.onCreate();
        Log.v(TAG, "onCreate");
    }
    
    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.v(TAG, "onDestroy");
    }

	
	@Override
	public IBinder onBind(Intent arg0) {
		return myBinder;
	}
	
	
	/**
	 * These functions can be accessed by activities when the service is bound
	 * 
	 */
    public class LocalBinder extends Binder {
        
    	/**
         * Getter function for ConnectionService
         * @return ConnectionService
         */
    	public SpeechRecognizerService getService() {
            return SpeechRecognizerService.this;
        }
        
        /**
         * 
         */
        public void startService(){
        	SpeechRecognizerService.this.startService();
        }
        
        public boolean hasConfirmationString(String confirmationString, ArrayList<String> results){
        	return SpeechRecognizerService.this.hasConfirmationString(confirmationString, results);
        }

        public boolean isConnected(){
        	return SpeechRecognizerService.this.isConnected();
        }
        
        public String getConfirmationString(){
        	return SpeechRecognizerService.this.getConfirmationString();
        }
        
    }
    
    public String getConfirmationString(){
    	return CONFIRMATION_STRING;
    }
    
    public boolean isConnected(){
    	Log.v(TAG,"isConnected");

    	return serviceStarted;
    }
    
    public void startService(){
    	Log.v(TAG,"startService");
    	SpeechRecognizerService.this.initSpeechRecognizer();
    	SpeechRecognizerService.this.initSpeechRecognizerFreezeHandler();
    	serviceStarted = true;
		prefs = getSharedPreferences(PREFS_NAME, 0);

		mAudioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
		mAudioManager.setStreamMute(AudioManager.STREAM_SYSTEM, true);
    }
    
	/**
	 * Initializes speech recognizer
	 */
	private void initSpeechRecognizer() {
		speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this);
		speechRecognizer.setRecognitionListener(recognitionListener);
	}

	/**
	 * Sets up recognition Listener
	 */
	private RecognitionListener recognitionListener = new RecognitionListener() {

		public void refreshTimers(){
			speechOn = true;
			lastSpeechRecognizerActionTime = System.currentTimeMillis();
		}
		
		@Override
		public void onBeginningOfSpeech() {
			Log.v(TAG, "onBeginningOfSpeech");
			refreshTimers();
		}

		@Override
		public void onBufferReceived(byte[] arg0) {
			Log.v(TAG, "onBufferReceived");
			refreshTimers();
		}

		@Override
		public void onEndOfSpeech() {
			Log.v(TAG, "onEndOfSpeech");
			refreshTimers();
			sendBusy(true);
			if (state == STATE_ACTIVE)
				lastRefreshTime = System.currentTimeMillis();	
		}

		@Override
		public void onError(int error) {
			refreshTimers();
			
			String errorString = "";
			if (error == SpeechRecognizer.ERROR_NETWORK_TIMEOUT) {
				errorString = "ERROR_NETWORK_TIMEOUT";
			} else if (error == SpeechRecognizer.ERROR_AUDIO) {
				errorString = "ERROR_AUDIO";
				sendBusy(false);
			} else if (error == SpeechRecognizer.ERROR_CLIENT) {
				errorString = "ERROR_CLIENT";
			} else if (error == SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS) {
				errorString = "ERROR_INSUFFICIENT_PERMISSIONS";
			} else if (error == SpeechRecognizer.ERROR_NETWORK) {
				errorString = "ERROR_NETWORK";
			} else if (error == SpeechRecognizer.ERROR_NO_MATCH) {
				errorString = "ERROR_NO_MATCH";
				sendBusy(false);
			} else if (error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY) {
				errorString = "ERROR_RECOGNIZER_BUSY";
				cancelListener();
			} else if (error == SpeechRecognizer.ERROR_SERVER) {
				errorString = "ERROR_SERVER";
			} else if (error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT) {
				errorString = "ERROR_SPEECH_TIMEOUT";
			}
			//textView.setText("error:" + errorString);
			Log.v(TAG, "onError:" + errorString);
			sendErrorMsg(errorString);
			startListening();

		}

		@Override
		public void onEvent(int arg0, Bundle arg1) {
			Log.v(TAG, "onEvent");
			refreshTimers();
		}

		@Override
		public void onPartialResults(Bundle arg0) {
			Log.v(TAG, "onPartialResults");
			refreshTimers();
		}

		@Override
		public void onReadyForSpeech(Bundle arg0) {
			Log.v(TAG, "onReadyForSpeech");
			refreshTimers();
			sendBusy(false);

		}

		/**
		 * This function is called when receiving a result
		 */
		@Override
		public void onResults(Bundle results) {
			refreshTimers();
			talked = false;
			Log.d(TAG, "onResults " + results);

			ArrayList<String> data = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
			
			// If in active listening state (has said confirmation message)
			sendResultsMsg(results, MSG_TYPE_COMMAND_LIST);
			
			if (state == STATE_ACTIVE){
				lastRefreshTime = System.currentTimeMillis();
				/** sends messages to connected device **/
				String trainedString = TrainerFunctions.getTrainer(
						data.get(0), SpeechRecognizerService.this, prefs);
				sendBroadcastMsg(trainedString, MSG_TYPE_COMMAND);
				if (TrainerFunctions.isCommand(trainedString, SpeechRecognizerService.this))
					playCommandAudioFeedback();
				
				// If said confirmation message
			} else if (hasConfirmationString(CONFIRMATION_STRING, data)) {
				playCommandAudioFeedback();
				sendState(STATE_ACTIVE);
				lastRefreshTime = System.currentTimeMillis();
				
				/** sends messages to connected device **/
				sendBroadcastMsg(TrainerFunctions.getTrainer(data.get(0), SpeechRecognizerService.this, prefs), MSG_TYPE_COMMAND);

			} else {

			}

			startListening(); // loops again

		}

		@Override
		public void onRmsChanged(float noise) {
			Log.v(TAG, "onRmsChanged: " + noise);
			refreshTimers();
			
			
			sendAudioLevel(noise);

			// Handles instances where the voice recognition doesn't call
			// onBeginningOfSpeech
			if (noise > 8) {
				talked = true;
			} else if (talked && noise < 4) {
				if (silenceStart == 0) {
					silenceStart = System.currentTimeMillis();
				} else if (System.currentTimeMillis() - silenceStart > 500) {
					silenceStart = 0;
					talked = false;
					speechRecognizer.stopListening();
					sendBusy(true);
				}
			} else {
				silenceStart = 0;
			}

			
		}

	};
	
	/**
	 * Checks ArrayList for a particular string
	 * 
	 * @param cmd
	 *            string that we are looking for
	 * @param data
	 *            the ArrayList we are iterating through
	 * @return true if the cmd string is found within the data ArrayList,
	 *         false otherwise
	 */
	public boolean hasConfirmationString(String cmd, ArrayList<String> data) {
		for (int i = 0; i < data.size(); i++) {
			if (cmd.equals(data.get(i))
					|| cmd.equals(TrainerFunctions.getTrainer(data.get(i),
							SpeechRecognizerService.this, prefs))) {
				Log.v("confiramtion", "S");
				return true;
			}
		}
		return false;

	}
	
	/**
	 * Tells the speech recognizer to start listening
	 */
	private void startListening() {
		speechOn = false;
		
		Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
		intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
				RecognizerIntent.LANGUAGE_MODEL_WEB_SEARCH);
		intent.putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE,
				"voice.recognition.test");
		// intent.putExtra(RecognizerIntent.EXTRA_CONFIDENCE_SCORES, true);
		// intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS,5);

		speechRecognizer.startListening(intent);
		Log.i("111111", "11111111");
	}

	/**
	 * Tells the speech recognizer to cancel
	 */
	private void cancelListener() {
		speechRecognizer.cancel();
	}

	/**
	 * Workaround for when speechRecognizer.startListening doesn't do anything
	 * 
	 * 
	 */
	private void initSpeechRecognizerFreezeHandler() {

		final Handler freezeHandler = new Handler();
		Runnable freezeRunner = new Runnable() {
			@Override
			public void run() {
				if (System.currentTimeMillis() - lastSpeechRecognizerActionTime >= 200 &&
					!busy){
					sendBusy(true);
				}
				if (System.currentTimeMillis() - lastSpeechRecognizerActionTime >= 500 && !speechOn) {
					speechRecognizer.cancel();
					startListening();
					Log.v(TAG, "freezeHandler: startListening failed");

				}else if (System.currentTimeMillis() - lastSpeechRecognizerActionTime >= 4000 && speechOn) {
					speechRecognizer.cancel();
					startListening();
					Log.v(TAG, "freezeHandler: recognizer died and restarted");
				}
				
				freezeHandler.postDelayed(this, 500);
			}

		};
		freezeHandler.postDelayed(freezeRunner, 0);

	}
    
	/**
	 * Plays a beep sound
	 */
	private Handler mHandler = new Handler();

	private void playCommandAudioFeedback() {

		final ToneGenerator tg = new ToneGenerator(
				AudioManager.STREAM_ALARM, 100);
		tg.startTone(ToneGenerator.TONE_PROP_BEEP);

		mHandler.postDelayed(new Runnable() {
			public void run() {
				tg.release();
				return;
			}
		}, 200);

		
	}
	

	/**
	 * Sends recognized words from speech recognizer to MainActivity
	 * 
	 * @param msg
	 * @param bundle
	 */
	private void sendResultsMsg(Bundle bundle, String type){
        Intent intent = new Intent("speech");
        intent.putExtra("type", type);
        intent.putExtra("results", bundle);
        sendBroadcast(intent);
	}
	
	/**
	 * Sends broadcast message
	 * 
	 * @param msg
	 */
	private void sendBroadcastMsg(String msg, String type){
		String message = msg.toLowerCase();

        Intent intent = new Intent("speech");
        intent.putExtra("type", type);
        intent.putExtra("msg", message);

        sendBroadcast(intent);
	}
	
	/**
	 * Send broadcast message on busy state
	 * 
	 * @param busy
	 */
	private void sendBusy(boolean busy){
		sendBroadcastMsg(""+busy,MSG_TYPE_AUDIO_BUSY);
	}
	
	/**
	 * Sends broadcast message describing audio RMS level
	 * 
	 * @param rms
	 */
	private void sendAudioLevel(float rms){
		long curActionTime = System.currentTimeMillis();

		if (curActionTime - lastActionTime < MIN_ACTION_TIME) {
			return;
		}
		
		if (rms > MINIMUM_RMS_REFRESH && state == STATE_ACTIVE)
			lastRefreshTime = curActionTime;
		if (curActionTime - lastRefreshTime > COMMANDS_TIMEOUT_DURATION){
			sendState(STATE_INACTIVE);
		}
		lastActionTime = curActionTime;
		
		sendBroadcastMsg(""+rms,MSG_TYPE_AUDIO_LEVEL);
	}
	
	/**
	 * Sends broadcast message describing a recognizer error
	 * 
	 * @param error
	 */
	private void sendErrorMsg(String error){
		sendBroadcastMsg(error,MSG_TYPE_AUDIO_ERROR);
	}
	
	private void sendState(int s){
		state = s;
		sendBroadcastMsg(""+s,MSG_TYPE_AUDIO_STATE);
	}
	
	
}
