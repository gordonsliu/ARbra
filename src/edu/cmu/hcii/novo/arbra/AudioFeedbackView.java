package edu.cmu.hcii.novo.arbra;

import java.util.Random;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.SweepGradient;
import android.util.AttributeSet;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

/**
 * 
 * 
 * @author Gordon
 *
 */
public class AudioFeedbackView extends SurfaceView implements SurfaceHolder.Callback {
	class AudioFeedbackThread extends Thread {
		private static final int NUMBER_OF_BARS = 8;
		private int BAR_MAXIMUM_OFFSET = 200; // how quickly bars grow in height
		private int BAR_MARGIN = 5; // size of margin between bars
		private int MINIMUM_RMS_READ = 1;


		private int viewWidth;	// width of view
		public int viewHeight;	// height of view 
		
		private Paint pBar;			// paint of bars
		private Paint pThreshold; 	// paint of threshold marker

		
		private int levels[] = new int[NUMBER_OF_BARS];					// pixel heights of bars (if directly corresponding the noise level)
		private int drawnLevels[] = new int[NUMBER_OF_BARS];			// pixel heights of bars (when actually drawn - this is different from the above for making animations smoother)
		public float levelThreshold = 0;								// pixel height of level threshold line
		private float[] drawnLevelSpeeds = new float[NUMBER_OF_BARS];	// pixel heights of bars (if directly corresponding the noise level)
		private int randomLevelIndex;

		private long lastMessageTime;
		
		private long lastDrawTime;
		private long lastRandomizeTime;
		
        /** Handle to the surface manager object we interact with */
        private SurfaceHolder mSurfaceHolder;

        /** Indicate whether the surface has been created & is ready to draw */
        private volatile boolean mRun = false;
        
        private boolean paused = false;
        
        public AudioFeedbackThread(SurfaceHolder surfaceHolder, Context context) {
            // get handles to some important objects
            mSurfaceHolder = surfaceHolder;
            mContext = context;
            
        }
        
        
        @Override
        public void run() {
            while (mRun) {
                Canvas c = null;
                try {
                    c = mSurfaceHolder.lockCanvas(null);
                    synchronized (mSurfaceHolder) {
                        if (!paused)
                        	doDraw(c);
                    }
                } finally {
                    // do this in a finally so that if an exception is thrown
                    // during the above, we don't leave the Surface in an
                    // inconsistent state
                    if (c != null) {
                        mSurfaceHolder.unlockCanvasAndPost(c);
                    }
                }
            }
        }

        /**
         * Used to signal the thread whether it should be running or not.
         * Passing true allows the thread to run; passing false will shut it
         * down if it's already running. Calling start() after this was most
         * recently called with false will result in an immediate shutdown.
         *
         * @param b true to run, false to shut down
         */
        public void setRunning(boolean b) {
            mRun = b;
        }
        
        /**
         * Sets current state
         * @param s current state
         */
        public void setState(int s){
        	//synchronized (mSurfaceHolder){
	        	state = s;
	        	if (state == STATE_ACTIVE){
	        		refreshThresholdLine();
	        	}
        	//}
        }
        
        /**
         * Gets current state
         * @return current state
         */
        public int getCurState(){
        	return state;
        }
        
        /**
         * Sets threshold to the top
         * Sets time for last received message (used to calculate y-coordinate of threshold line)
         */
        public void refreshThresholdLine(){
	    	levelThreshold = viewHeight;
	    	lastMessageTime = System.currentTimeMillis();
        	
        }
        
    	/**
    	 * Sets flag for whether to render the busy state animation
    	 * 
    	 * @param busyState
    	 */
    	public void setBusy(boolean busyState){
    		//synchronized (mSurfaceHolder){
    			busy = busyState;
    			refreshThresholdLine();
    		//}
    	}
    	
    	/**
    	 * Gets busy state
    	 * @return
    	 */
    	public boolean getBusy(){
    		return busy;
    	}
        
        /**
         * All drawing is handled here.
         * @param c
         */
		private void doDraw(Canvas c) {
			if (c==null)
				return;
			c.drawColor(Color.BLACK);
			//Log.v("hello","hello");
			
			if (busy){
				drawBusy(c);
			}else{
				if (state == STATE_INACTIVE){
					drawInactive(c);
				}else if (state == STATE_ACTIVE)
					drawThresholdLine(c);

				if (shift)
					drawAudioBars(c);
				else
					drawAudioBarsVert(c);
			}
		}
		
		/**
		 * Initializes paint
		 */
		private void init(){
			pBar = new Paint();
			pBar.setColor(Color.parseColor("#6bf8f3"));
			//p.setMaskFilter(new BlurMaskFilter(25, Blur.INNER));
			//p.setMaskFilter(new BlurMaskFilter(35, Blur.OUTER));
			//p.setMaskFilter(new BlurMaskFilter(15, Blur.SOLID));
			//pBar.setMaskFilter(new BlurMaskFilter(10, Blur.NORMAL));
			//p.setShadowLayer(70, 0, 0, Color.CYAN);
			
			pThreshold = new Paint();
			pThreshold.setColor(Color.YELLOW);
			pThreshold.setStrokeWidth(2.0f);
			//pThreshold.setShader(new LinearGradient(8f, 80f, 30f, 20f, Color.RED,Color.WHITE, TileMode.MIRROR));
		
			
			for (int i = 0; i < levels.length; i++ ){
				drawnLevelSpeeds[i] = 1;
				
			}
		}
		
        /* Callback invoked when the surface dimensions change. */
        public void setSurfaceSize(int width, int height) {
            // synchronized to make sure these all change atomically
            synchronized (mSurfaceHolder) {
                viewWidth = width;
                viewHeight = height;
            	init();
               
            	Log.v("setSurfaceSize","viewWidth,viewHeight "+width+","+height);
            }
        }
        
        private void drawInactive(Canvas c){
        	c.drawColor(Color.GRAY);
        }
        
    	private void drawBusy(Canvas c){
    		Paint paint = new Paint();
    		paint.setShader(new SweepGradient(viewWidth/2, viewHeight/2, Color.YELLOW, Color.BLACK));
//    		c.rotate(System.currentTimeMillis()/2%360,viewWidth/2,viewHeight/2);	
    		c.drawCircle(viewWidth/2, viewHeight/2, 50, paint);						
    	}
    	
    	/**
    	 * Draws the threshold line
    	 */
    	private void drawThresholdLine(Canvas c){
    		int maxDrawnLevel = getMaximumDrawnLevel();
    		
    		if (levelThreshold < maxDrawnLevel)
    			levelThreshold = maxDrawnLevel;
    		else{
    			levelThreshold = getThresholdLineHeight();

    		}
    		levelThreshold = Math.max(levelThreshold, 0);
    		
    		c.drawLine(0, viewHeight-levelThreshold, viewWidth, viewHeight-levelThreshold, pThreshold);

    		//c.drawRect(0, viewHeight-levelThreshold, viewWidth, viewHeight, pThreshold);

    		lastDrawTime = System.currentTimeMillis();
    		//Log.v("lastDrawTime, offset", lastDrawTime +", "+ levelThreshold);
	    	
    		//if (levelThreshold/(float)viewHeight <= 0f){
    			//setState(STATE_INACTIVE);
    		//}

    	}
    	
    	/**
    	 * Calculates the distance the line should move downwards when audio level is lower than threshold line
    	 * @return
    	 */
    	private float getThresholdLineOffset(){
    		return (((System.currentTimeMillis() - lastDrawTime) / ((float)SpeechRecognizerService.COMMANDS_TIMEOUT_DURATION)) * viewHeight);
    	}
    	
    	/**
    	 * Calculates the distance the line should move downwards when audio level is lower than threshold line
    	 * @return
    	 */
    	private float getThresholdLineHeight(){
    		return viewHeight - (((System.currentTimeMillis() - lastMessageTime) / ((float)SpeechRecognizerService.COMMANDS_TIMEOUT_DURATION)) * viewHeight);
    	}
    	

    	/**
    	 * Get maximum drawn bar height
    	 * @return
    	 */
    	private int getMaximumDrawnLevel(){
    		int maxDrawnLevel = 0;
    		// get maximum drawnLevel
    		for (int i = 0; i < drawnLevels.length; i++){
    			maxDrawnLevel = Math.max(maxDrawnLevel, drawnLevels[i]);
    		}
    		return maxDrawnLevel;
    	}
    	
    	
    	/**
    	 * Draws the audio bars (that move from left to right)
    	 * @param c
    	 */
    	private void drawAudioBars(Canvas c){
    		int barWidth = (viewWidth - BAR_MARGIN*levels.length) / levels.length; 
    		
    		for (int i = 0; i < levels.length; i++){
    			//Rect rect = new Rect(i*50+i*BAR_MARGIN,viewHeight-drawnLevels[i],i*50+50+i*BAR_MARGIN,viewHeight);
    			Rect rect = new Rect(i*barWidth+i*BAR_MARGIN,viewHeight-drawnLevels[i],i*barWidth+barWidth+i*BAR_MARGIN,viewHeight);

    			c.drawRect(rect, pBar);	
    		}
    	}
    	
    	/**
    	 * Draws audio bars (that go up and down)
    	 * @param c
    	 */
    	private void drawAudioBarsVert(Canvas c){
    		int barWidth = (viewWidth - BAR_MARGIN*levels.length) / levels.length; 
    		
    		for (int i = 0; i < levels.length; i++){
    			Rect rect;
    			rect = new Rect(i*barWidth+i*BAR_MARGIN, viewHeight-drawnLevels[i],i*barWidth+barWidth+i*BAR_MARGIN,viewHeight);
    			
    			c.drawRect(rect, pBar);	
    		}
    	}
    	
    	
    	/**
    	 * Shifts audio level array by adding on the most recent audio height
    	 * @param pixel most recent audio height
    	 */
    	private void shiftAudioLevelArray(int pixel){
    		for (int i = 0; i < levels.length; i++){
    			if (i == levels.length-1){
    				levels[levels.length-1] = pixel;
    			}else{
    				levels[i] = levels[i+1];
    			}
    		}
    	
    	}
    	
    	
    	/**
    	 * Fills audio levels with the most recent pixel level value and random values based off of this pixel level
    	 */
    	private void fillAudioLevelArrayRandom(int pixel){
			for (int i = 0; i < levels.length; i++){
				if (randomLevelIndex == i)
					levels[i] = pixel;
				else
					levels[i] = (int) (pixel * drawnLevelSpeeds[i] * 2);
			}

    	}
    	
    	
    	/**
    	 * Converts pixel heights of bars to their heights when drawn
    	 */
    	private void convertAllPixelsToDrawnPixels(){
    		for (int i = 0; i < levels.length; i++){
    			drawnLevels[i] = convertPixelToDrawnPixel(i);
    		}
    	}
    	
    	/**
    	 * Converts the pixel size of visualizer that corresponds exactly to audio level to 
    	 * the pixel level that is drawn so the animation is smoother
    	 * 
    	 * @param arraySlot the # visualizer being drawn (corresponds to levels and drawnLevels arrays)
    	 * @return 
    	 */
    	private int convertPixelToDrawnPixel(int arrayIndex){
    		
    		int pixelDiff = levels[arrayIndex] - drawnLevels[arrayIndex];
    		int drawnPixel = drawnLevels[arrayIndex];
    				
    		if (pixelDiff > 0){
    			if (pixelDiff > BAR_MAXIMUM_OFFSET){
    				drawnPixel = (int) (drawnLevels[arrayIndex] + BAR_MAXIMUM_OFFSET * drawnLevelSpeeds[arrayIndex]);
    			}else{
    				drawnPixel = levels[arrayIndex];
    			}
    		}else if (pixelDiff < 0){
    			if (Math.abs(pixelDiff) > BAR_MAXIMUM_OFFSET){
    				drawnPixel = (int) (drawnLevels[arrayIndex] - BAR_MAXIMUM_OFFSET * (drawnLevelSpeeds[arrayIndex] / 2));
    			}else{
    				drawnPixel = levels[arrayIndex];
    			}
    		}
    		
    		return drawnPixel;
    	}
    	
    	/**
    	 * Randomly generates bar speeds
    	 */
    	private void setBarRandomSpeeds(){
    		if (System.currentTimeMillis() - lastRandomizeTime > 200){
    		
    			Random rand = new Random();
    			randomLevelIndex = rand.nextInt(levels.length);
    		
    			for (int i = 0; i < levels.length; i++){
    				if (i == randomLevelIndex)
    					drawnLevelSpeeds[i] = 1f;
    				else{
    					int speed = rand.nextInt(10);
    					drawnLevelSpeeds[i] = speed/20.0f+.25f;
    				}
    			}
    			lastRandomizeTime = System.currentTimeMillis();

    		}
    		
    	}
    	
    	/**
    	 * Converts raw rms value to an audio level percentage where 100% means the loudest and 0% means no audio	
    	 * @param rms
    	 * @return audio level percentage
    	 */
    	private float convertRmsToPercent(float rms){
    		float percent;
    		
    		if (rms < MINIMUM_RMS_READ)
    			percent = 0;
    		else
    			percent = (rms - MINIMUM_RMS_READ)/(10 - MINIMUM_RMS_READ);
    		
    		percent = Math.max(Math.min(1,percent),0);
    		    		
    		return percent;
    	}
    	
    	/**
    	 * Converts an audio level percentage to pixel size
    	 * @param percent
    	 * @return pixel size of audio feedback visualizer
    	 */
    	private int convertPercentToPixel(float percent){
    		int pixel;
    		pixel = (int) (percent * viewHeight);
    		return pixel;
    	}
 
    	/**
    	 * Updates audio feedback visualization numbers
    	 * @param rms raw rms value from SpeechRecognizer
    	 */
    	private void updateAudioValues(float rms){
    		synchronized (mSurfaceHolder){
	    		float percent = convertRmsToPercent(rms);
	    		int pixel = convertPercentToPixel(percent);
	    		
	    		if (shift){
	    			shiftAudioLevelArray(pixel);
	    		}else{
	    			setBarRandomSpeeds();
	    			fillAudioLevelArrayRandom(pixel);
	    		}
	    		convertAllPixelsToDrawnPixels();
	    		
	    		if (rms > SpeechRecognizerService.MINIMUM_RMS_REFRESH)
	    			lastMessageTime = System.currentTimeMillis();
	    		
	    		//drawnLevels[drawnLevels.length-1] = convertPixelToDrawnPixel(drawnLevels.length-1);
    	
    		}
    	}
    	
    	/**
    	 * Pauses drawing
    	 */
    	public void pause(){
    		synchronized (mSurfaceHolder) {
    			paused = true;
    		}
    	}
    	
    	/**
    	 * Unauses drawing
    	 */
    	public void unpause(){
    		synchronized (mSurfaceHolder) {
    			paused = false;
    		}
    	}
	}
	
	private String TAG = "AudioFeedbackView";

	public boolean shift = false; // if shift is true, we show the left/right moving visualizer, if false, then we show the up/down bars
	
	public int STATE_INACTIVE = 0;
	public int STATE_ACTIVE = 1;
	public boolean busy = false;
    private int state = STATE_INACTIVE;

    /** The thread that actually draws the animation */
    private AudioFeedbackThread thread;
    /** Handle to the application context, used to e.g. fetch Drawables. */
    private Context mContext;
    private SurfaceHolder mHolder;
    private boolean threadClosed = false;
    
	public AudioFeedbackView(Context context, AttributeSet attrs) {
		super(context, attrs);
        // register our interest in hearing about changes to our surface
		mHolder = getHolder();
		mHolder.addCallback(this);
        mContext = context;
        
        // create thread only; it's started in surfaceCreated()
        thread = new AudioFeedbackThread(mHolder, mContext);

        setFocusable(true); // make sure we get key events
	}
	
    /**
     * Fetches the animation thread corresponding to this LunarView.
     *
     * @return the animation thread
     */
    public AudioFeedbackThread getThread() {
        return thread;
    }

	
    @Override
    protected void onSizeChanged(int xNew, int yNew, int xOld, int yOld){
        super.onSizeChanged(xNew, yNew, xOld, yOld);
        thread.viewWidth = xNew;
        thread.viewHeight = yNew;

        Log.v(TAG,"w,h: "+thread.viewWidth+", "+thread.viewHeight);
    }
    
	/**
	 * Updates audio feedback visualization
	 * @param rms raw rms value from SpeechRecognizer
	 */
	public void updateAudioFeedbackView(float rms){
		thread.updateAudioValues(rms);
	}

	@Override
	public void surfaceChanged(SurfaceHolder arg0, int format, int width, int height) {
        thread.setSurfaceSize(width, height);
    	Log.v("setSurfaceSize","viewWidth,viewHeight "+width+","+height);

	}

	@Override
	public void surfaceCreated(SurfaceHolder arg0) {
        // start the thread here so that we don't busy-wait in run()
        // waiting for the surface to be created
		if (threadClosed)
			thread = new AudioFeedbackThread(mHolder, mContext);
		thread.setRunning(true);
        thread.start();
	}

	@Override
	public void surfaceDestroyed(SurfaceHolder arg0) {
        // we have to tell thread to shut down & wait for it to finish, or else
        // it might touch the Surface after we return and explode
        boolean retry = true;
        thread.setRunning(false);
        while (retry) {
            try {
                thread.join();
                retry = false;
                threadClosed = true;
            } catch (InterruptedException e) {
            }
        }		
	}
	


}
