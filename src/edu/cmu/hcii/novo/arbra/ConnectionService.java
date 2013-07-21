package edu.cmu.hcii.novo.arbra;

import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;

import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.util.Log;

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
	//private Runnable recRunnable		= null;
	//private Thread recThread            = null;
	//private DataInputStream streamIn	= null;
	
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
	
	/**
	 * These functions can be accessed by activities when the service is bound
	 * 
	 */
    public class LocalBinder extends Binder {
        
    	/**
         * Getter function for ConnectionService
         * @return ConnectionService
         */
    	public ConnectionService getService() {
            return ConnectionService.this;
        }
    	
    	/**
    	 * Sends a message String to connected device
    	 * @param msg the String to send to connected device
    	 */
        public void sendMsg(String msg){
        	ConnectionService.this.sendMsg(msg);
        }
        
        /**
         * Disconnects the socket
         */
        public void stop(){
        	ConnectionService.this.stop();
        }
        
        /**
         * Creates a socket connection
         * @param ip_address the IP address of the server
         */
        public void connect(String ip_address){
        	ConnectionService.this.connect(ip_address);
        }
        
        /**
         * Gets whether the socket is connected.
         * @return true if the socket is connected; false otherwise
         */
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

    /**
     * Opens a socket and attempts to connect to the input IP address
     * Called by activities (ConnectionPopUp & MainActivity) when this service is bound
     * @param ip_address the IP address of the server socket
     */	
    public void connect(String ip_address){
    	run = false;
    	sendBroadcastMsg("disconnected");
	    try {
	    	if (socket!= null) socket.close();
	    	if (streamOut != null) streamOut.close();
	    	//if (streamIn != null) streamIn.close();
	    } catch (IOException e) {
			e.printStackTrace();
	    }
    	ip=ip_address;
    	socket = new Socket();
    	connectRunnable = new connectSocket();
    	sendThread = new Thread(connectRunnable);
    	sendThread.start();
    	
    }
    
    /**
     * Thread sets up socket connection
     * 
     */
    class connectSocket implements Runnable {
        @Override
        public void run() {
        	Log.v(TAG, "connectSocket_run");
            SocketAddress socketAddress = new InetSocketAddress(ip, port );
            try {               
                socket.connect(socketAddress, 1000);
                streamOut = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()));
                sendBroadcastMsg("connected");
                run = true;
                //recRunnable = new receiveSocket();
                //recThread = new Thread(recRunnable);
                //recThread.start();
            } catch (IOException e) {
            	//stop();
                e.printStackTrace();
            }
        }    
    }
    
    /**
     * Sends a string to output stream
     * @param msg the string to be sent
     */
    public void sendMsg(final String msg){
    	if (run){ // if system is connected
    		
		  	Runnable run = new Runnable(){
				@Override
				public void run() {
		    		try {
						streamOut.writeBytes(msg+"\n");
						streamOut.flush();

						return;
					} catch (IOException e) {
						e.printStackTrace();
					}					
				}
		  	};
		  	Thread t = new Thread(run);
		  	t.start();

    	}else{
    		// if system is not connected, make connection pop up show up
    		/* 
    		 Intent dialogIntent = new Intent(this, ConnectionPopUp.class);
			 dialogIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
			 getApplication().startActivity(dialogIntent);
			 */
    	}
    }    
    
    /**
     * Thread receives messages
     * 
     */
    /*
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

		      
		}
    }
    */
  
    /**
     * Forces the socket to disconnect
     * Also opens ConnectionPopUp
     */
    public void stop(){
    	run = false;
    	sendBroadcastMsg("disconnected");

	    try {
	    	if (socket!= null) socket.close();
	    	if (streamOut != null) streamOut.close();
	    	//if (streamIn != null) streamIn.close();
	    } catch (IOException e) {
			e.printStackTrace();
	    }
	    Intent dialogIntent = new Intent(this, ConnectionPopUp.class);
	    dialogIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
	    getApplication().startActivity(dialogIntent);
	    //startActivity(new Intent(this, ConnectionPopUp.class));
    }    
    
    
	/**
	 * Sends a broadcast message to be read by other classes
	 * @param msg the string to be sent
	 */
	private void sendBroadcastMsg(String msg){
        Intent intent = new Intent("connection");
        intent.putExtra("msg", msg);
        sendBroadcast(intent);
	}
}