// Copyright 2007-2013 metaio GmbH. All rights reserved.
package edu.cmu.hcii.novo.arbra;

import java.util.HashMap;
import java.util.Map;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;

import com.metaio.sdk.ARViewActivity;
import com.metaio.sdk.MetaioDebug;
import com.metaio.sdk.jni.ETRACKING_STATE;
import com.metaio.sdk.jni.EVISUAL_SEARCH_STATE;
import com.metaio.sdk.jni.IGeometry;
import com.metaio.sdk.jni.IMetaioSDKCallback;
import com.metaio.sdk.jni.IVisualSearchCallback;
import com.metaio.sdk.jni.ImageStruct;
import com.metaio.sdk.jni.TrackingValues;
import com.metaio.sdk.jni.TrackingValuesVector;
import com.metaio.sdk.jni.Vector3d;
import com.metaio.sdk.jni.VisualSearchResponseVector;
import com.metaio.tools.io.AssetsManager;

/**
 * This activity uses the Metaio SDK for our augmented reality demos of
 * data recording and overlays that assist with manual tasks.
 * 	 
 * @author Chris
 *
 */
public class ARMode extends ARViewActivity {
	private static final String TAG = "RecordData";
	
	public static final String MSG_TYPE_AR_READ = "readObject"; // message type of message sent to ConnectionService and connected device
	
	private MetaioSDKCallbackHandler mCallbackHandler; 
	private Map<String, String> idValues; // map of id's and data values 
	private Map<String, IGeometry> markerMovies; 
	
	private static final String idMovie = "identify.3g2";
	private static final String swabMovie = "swab.3g2";
	private static final int numSwabSites = 3;
	
	
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		mCallbackHandler = new MetaioSDKCallbackHandler();
		
		idValues = new HashMap<String, String>();
		idValues.put("Identify1", "50");
		idValues.put("Identify2", "500");
		
		markerMovies = new HashMap<String, IGeometry>();
	}

	
	@Override
	protected int getGUILayout() {
		// Attaching layout to the activity
		return R.layout.template; 
	}
	
	/**
	 * Draws the AR visuals
	 */
	@Override
	public void onDrawFrame() {
		super.onDrawFrame();
		
		if (metaioSDK != null) {
			// get all detected poses/targets
			TrackingValuesVector poses = metaioSDK.getTrackingValues();
					
			//Log.i(TAG, "Tracking " + poses.size() + " objects");
			for (int i = 0; i < poses.size(); i++) {
				TrackingValues pose = poses.get(i);
				String name = pose.getCosName();
				IGeometry movie = markerMovies.get(name);
				
				if (movie != null) {
					movie.setCoordinateSystemID(pose.getCoordinateSystemID());
					
					if (pose.getState() == ETRACKING_STATE.ETS_FOUND) {										
						movie.startMovieTexture(movie.getMovieTextureStatus().getLooping());
												
						if (idValues.containsKey(name)) {
							sendBroadcastMsg(idValues.get(name));
						}
						
					} else if (pose.getState() == ETRACKING_STATE.ETS_LOST) {
						Log.i(TAG, "Pausing movie");
						movie.pauseMovieTexture();
					}
				}
			}
		}
	}


	public void onButtonClick(View v) {
		finish();
	}
	
	
	@Override
	protected void loadContents() {
		Log.i(TAG, "Loading contents");
		try	{
			// Getting a file path for tracking configuration XML file
			String trackingConfigFile = AssetsManager.getAssetPath("Tracking/TrackingData_MarkerlessFast.xml");
			
			// Assigning tracking configuration
			boolean result = metaioSDK.setTrackingConfiguration(trackingConfigFile); 
			MetaioDebug.log("Tracking data loaded: " + result); 
			
			
			String idPath = AssetsManager.getAssetPath("Tracking/" + idMovie);
			String swabPath = AssetsManager.getAssetPath("Tracking/" + swabMovie);
			
			if (idPath != null && swabPath != null) {
				
				for (String key : idValues.keySet()) {
					IGeometry idGeo = metaioSDK.createGeometryFromMovie(idPath, true);
					if (idGeo != null) {
						MetaioDebug.log("Loaded geometry " + idPath);
						
						idGeo.startMovieTexture();
						markerMovies.put(key, idGeo);
					}
					else {
						MetaioDebug.log(Log.ERROR, "Error loading geometry: " + idPath);
					}
				}
				
				
				for (int i = 0; i < numSwabSites; i++) {
					IGeometry swabGeo = metaioSDK.createGeometryFromMovie(swabPath, true);
					if (swabGeo != null) {
						MetaioDebug.log("Loaded geometry " + swabPath);
						
						//mMoviePlane.setScale(1.5f);
						swabGeo.setTranslation(new Vector3d(0f, -75f, 0f));
						
						swabGeo.startMovieTexture(true);	
						markerMovies.put("Swab" + (i+1), swabGeo);
					}
					else {
						MetaioDebug.log(Log.ERROR, "Error loading geometry: " + swabPath);
					}
				}
			}
			
		}       
		catch (Exception e)	{
			Log.e(TAG, "Error loading contents", e);
		}
		
		Log.i(TAG, markerMovies.toString());
	}
	
  
	@Override
	protected void onGeometryTouched(IGeometry geometry) {
		
	}


	@Override
	protected IMetaioSDKCallback getMetaioSDKCallbackHandler() {
		return mCallbackHandler;
	}
	
	final class MetaioSDKCallbackHandler extends IMetaioSDKCallback {

		@Override
		public void onSDKReady() {
			MetaioDebug.log("The SDK is ready");
		}
		
		@Override
		public void onAnimationEnd(IGeometry geometry, String animationName) {
			MetaioDebug.log("animation ended" + animationName);
		}
		
		@Override
		public void onMovieEnd(IGeometry geometry, String name)	{
			MetaioDebug.log("movie ended" + name);
		}
		
		@Override
		public void onNewCameraFrame(ImageStruct cameraFrame) {
			MetaioDebug.log("a new camera frame image is delivered" + cameraFrame.getTimestamp());
		}
		
		@Override 
		public void onCameraImageSaved(String filepath)	{
			MetaioDebug.log("a new camera frame image is saved to" + filepath);
		}
		
		@Override
		public void onScreenshotImage(ImageStruct image) {
			MetaioDebug.log("screenshot image is received" + image.getTimestamp());
		}
		
		@Override
		public void onScreenshotSaved(String filepath) {
			MetaioDebug.log("screenshot image is saved to" + filepath);
		}
		
		@Override
		public void onTrackingEvent(TrackingValuesVector trackingValues) {
			MetaioDebug.log("The tracking time is:" + trackingValues.get(0).getTimeElapsed());
			
			for (int i = 0; i < trackingValues.size(); i++) {
				String COS = trackingValues.get(i).getCosName();
				Log.d(TAG, "Found marker: " + COS + " Value: " + idValues.get(COS) + " State: " + trackingValues.get(i).getState());
			}
		}

		@Override
		public void onInstantTrackingEvent(boolean success, String file) {
			if (success) {
				MetaioDebug.log("Instant 3D tracking is successful");
			}
		}
	}
	
	final class VisualSearchCallbackHandler extends IVisualSearchCallback {

		@Override
		public void onVisualSearchResult(VisualSearchResponseVector response, int errorCode) {
			if (errorCode == 0)	{
				MetaioDebug.log("Visual search is successful");
			}
		}

		@Override
		public void onVisualSearchStatusChanged(EVISUAL_SEARCH_STATE state) {
			MetaioDebug.log("The current visual search state is: " + state);
		}


	}
	
	
	/**
	 * Sends a broadcast message to be read by other classes
	 * @param msg the string to be sent
	 */
	private void sendBroadcastMsg(String msg) {
		Log.d(TAG, "Sending broadcast message: " + msg);
        Intent intent = new Intent("ar");
        intent.putExtra("type", MSG_TYPE_AR_READ);
        intent.putExtra("msg", msg);
        sendBroadcast(intent);
	}
}
