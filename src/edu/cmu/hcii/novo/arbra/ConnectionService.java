package edu.cmu.hcii.novo.arbra;

import java.io.BufferedOutputStream;
import java.io.BufferedWriter;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.Writer;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;

import org.apache.http.util.ByteArrayBuffer;

import android.app.Service;
import android.content.Intent;
import android.graphics.Color;
import android.os.Binder;
import android.os.Environment;
import android.os.IBinder;
import android.util.Log;
import android.widget.Toast;

public class ConnectionService extends Service {
	
	// socket
	Socket socket;
	public String ip;
	private static int port = 5550;
	private boolean run = false; 		// is connected or not
	
	// connect/send thread
	private Runnable connectRunnable	= null;
	private Thread sendThread           = null;
	private DataOutputStream streamOut  = null;
	
	// receive thread
	private Runnable recRunnable		= null;
	private Thread recThread            = null;
	private DataInputStream streamIn	= null;
	
	public boolean streaming = false; // True if in the middle of an image frame. 
									  // False if all the data for the current image frame has been read or if the stream has not started.
	public boolean getImage = false;
	public int frames = 0; // counter for total number of frames received
	
	// image dimensions
	public int width;
	public int height;
	
	// binder for activities to access functions
	private final IBinder myBinder = new LocalBinder();
	
	private static String TAG = "ConnectionService";

	@Override
	public IBinder onBind(Intent arg0) {
		return myBinder;
	}
	
	// these functions can be accessed by activities when the service is bound
    public class LocalBinder extends Binder {
        public ConnectionService getService() {
            return ConnectionService.this;
        }
        public void sendMsg(String msg){
        	ConnectionService.this.sendMsg(msg);
        }
        public void stop(){
        	ConnectionService.this.stop();
        }
        public void connect(String ip_address){
        	ConnectionService.this.connect(ip_address);
        }
        public boolean isConnected(){
        	return ConnectionService.this.isConnected();
        }
    }
    
    @Override
    public void onCreate() {
        super.onCreate();
        Log.v(TAG, "onCreate");
        socket = new Socket();
    }

    @Override
    public void onStart(Intent intent, int startId){
        super.onStart(intent, startId);
    }
    
    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.v(TAG, "onDestroy");
        stop();
        socket = null;
    }

	public boolean isConnected() {
		return run;
	}

    // Opens a socket and attempts to connect to the input ip address
    	// called by activities (ConnectionPopUp & ControlPanelActivity) when this service is bound
    public void connect(String ip_address){
    	run = false;
    	sendBroadcastMsg("disconnected");
	    try {
	    	if (socket!= null) socket.close();
	    	if (streamOut != null) streamOut.close();
	    	if (streamIn != null) streamIn.close();
	    } catch (IOException e) {
			e.printStackTrace();
	    }
    	ip=ip_address;
    	socket = new Socket();
    	connectRunnable = new connectSocket();
    	sendThread = new Thread(connectRunnable);
    	sendThread.start();
    	
    }
    
    // thread sets up socket connection
    class connectSocket implements Runnable {
        @Override
        public void run() {
        	Log.v(TAG, "connectSocket_run");
            SocketAddress socketAddress = new InetSocketAddress(ip, port );
            try {               
                socket.connect(socketAddress, 1000);
                streamOut = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()));
                recRunnable = new receiveSocket();
                recThread = new Thread(recRunnable);
                recThread.start();
            } catch (IOException e) {
            	//stop();
                e.printStackTrace();
            }
        }    
    }
    
    // sends bytes to output stream
    public void sendMsg(String msg){
    	if (run){ // if system is connected
		  	try {
				streamOut.writeBytes(msg+"\n");
				streamOut.flush();
			} catch (IOException e) {
				e.printStackTrace();
			}
    	}else{
    		// if system is not connected, make connection pop up show up
    		 Intent dialogIntent = new Intent(getBaseContext(), ConnectionPopUp.class);
			 dialogIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
			 getApplication().startActivity(dialogIntent);
    	}
    }    
    
    // thread receives messages
    class receiveSocket implements Runnable {
		private long beginTime;

		@Override
		public synchronized void run() {
			try
		      {  Log.v(TAG, "receive socket ");
				 streamIn  = new DataInputStream(socket.getInputStream()); // sets up input stream
				 run = true; // sets connection flag to true
			     sendBroadcastMsg("connected"); // lets the other activities know that a successful connection has been made
			 
		      }
		      catch(IOException ioe)
		      {  Log.v(TAG, "Error getting input stream: " + ioe);
		         
		      }
			// when connected, this thread will stay in this while loop
			while (run )
		      {
				
			/*	try
		         {  beginTime = System.currentTimeMillis();
	            	long timeElapsed = System.currentTimeMillis() - beginTime;
	            	
	            	int bytesRead = streamIn.read(buffer); // reads bytes from the input stream

		         	Log.v("bytesRead",""+bytesRead);
		         	Log.v("size",""+size);
		         	
			        if (bytesRead > 0){ 
			         	
			        }
		         	
		         }
		         catch(IOException ioe)
		         {  Log.v(TAG, "Listening error: " + ioe.getMessage());
		            
		         } */
		      }
		}
    }
    
  
    // Forces the socket to disconnect
    public void stop(){
    	run = false;
    	sendBroadcastMsg("disconnected");

	    try {
	    	if (socket!= null) socket.close();
	    	if (streamOut != null) streamOut.close();
	    	if (streamIn != null) streamIn.close();
	    } catch (IOException e) {
			e.printStackTrace();
	    }
	    Intent dialogIntent = new Intent(getBaseContext(), ConnectionPopUp.class);
	    dialogIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
	    getApplication().startActivity(dialogIntent);
	    //startActivity(new Intent(this, ConnectionPopUp.class));
    }    
    
	// sends a broadcast message to be read by other classes
	private void sendBroadcastMsg(String msg){
        Intent intent = new Intent("connection");
        intent.putExtra("msg", msg);
        sendBroadcast(intent);
	}
}