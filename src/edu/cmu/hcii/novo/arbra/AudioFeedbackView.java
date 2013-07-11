package edu.cmu.hcii.novo.arbra;

import android.content.Context;
import android.graphics.BlurMaskFilter;
import android.graphics.BlurMaskFilter.Blur;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Shader.TileMode;
import android.util.AttributeSet;
import android.util.Log;
import android.view.SurfaceView;

/**
 * 
 * @author Gordon
 *
 */
public class AudioFeedbackView extends SurfaceView {
	private Paint p;
	private int levels[] = new int[8];
	private int drawnLevels[] = new int[8];

	private int viewWidth;
	private int viewHeight;
	private String TAG = "AudioFeedbackView";
	
	private int MAXIMUM_OFFSET = 50;  

	public AudioFeedbackView(Context context, AttributeSet attrs) {
		super(context, attrs);
		init();
	}

	private void init(){
		p = new Paint();
		p.setColor(Color.parseColor("#6bf8f3"));
		//p.setMaskFilter(new BlurMaskFilter(25, Blur.INNER));
		//p.setMaskFilter(new BlurMaskFilter(35, Blur.OUTER));
		//p.setMaskFilter(new BlurMaskFilter(15, Blur.SOLID));
		p.setMaskFilter(new BlurMaskFilter(10, Blur.NORMAL));
		//p.setShadowLayer(70, 0, 0, Color.CYAN);


	}
	
	
    @Override
    protected void onSizeChanged(int xNew, int yNew, int xOld, int yOld){
            super.onSizeChanged(xNew, yNew, xOld, yOld);
            viewWidth = xNew;
            viewHeight = yNew;
            
            Log.v(TAG,"w,h: "+viewWidth+", "+viewHeight);
    }
    
	

	@Override
	protected void onDraw(Canvas c){
		drawAudioBars(c);
		
		
	}
	
	private void drawAudioBars(Canvas c){
		for (int i = 0; i < levels.length; i++){
			Rect rect = new Rect(i*50+i*5,viewHeight-drawnLevels[i],i*50+50+i*5,viewHeight);
			c.drawRect(rect, p);	
		}
	}
	
	/**
	 * Updates audio feedback visualization
	 * @param rms raw rms value from SpeechRecognizer
	 */
	public void updateAudioFeedbackView(float rms){
		float percent = convertRmsToPercent(rms);
		int pixel = convertPercentToPixel(percent);
		shiftAudioLevelArray(pixel);
		convertAllPixelsToDrawnPixels();
		//drawnLevels[drawnLevels.length-1] = convertPixelToDrawnPixel(drawnLevels.length-1);
		
		invalidate();
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
	 * 
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
			if (pixelDiff > MAXIMUM_OFFSET){
				drawnPixel = drawnLevels[arrayIndex] + MAXIMUM_OFFSET;
			}else{
				drawnPixel = levels[arrayIndex];
			}
		}else if (pixelDiff < 0){
			if (Math.abs(pixelDiff) > MAXIMUM_OFFSET){
				drawnPixel = drawnLevels[arrayIndex] - MAXIMUM_OFFSET;
			}else{
				drawnPixel = levels[arrayIndex];
			}
		}
		
		return drawnPixel;
		
	}
	
	/**
	 * Converts raw rms value to an audio level percentage where 100% means the loudest and 0% means no audio	
	 * @param rms
	 * @return audio level percentage
	 */
	private float convertRmsToPercent(float rms){
		float percent;
		
		if (rms < 1)
			percent = 0;
		else
			percent = (rms - 1)/9;
		
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

}
