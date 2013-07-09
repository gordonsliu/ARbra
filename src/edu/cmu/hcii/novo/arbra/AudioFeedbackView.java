package edu.cmu.hcii.novo.arbra;

import android.content.Context;
import android.graphics.Canvas;
import android.view.View;

public class AudioFeedbackView extends View{
	float levels[] = new float[255];
	
	
	public AudioFeedbackView(Context context) {
		super(context);
		// TODO Auto-generated constructor stub
	}
	
	@Override
	protected void onDraw(Canvas c){
		
	}
	
	
	public void updateAudioFeedbackView(int level){
		
		
		
		
		invalidate();
		
		
	}
	
}
