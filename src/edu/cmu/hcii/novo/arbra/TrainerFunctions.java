package edu.cmu.hcii.novo.arbra;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import org.json.JSONArray;
import org.json.JSONObject;

import android.app.Activity;
import android.content.SharedPreferences;
import android.os.Environment;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Adapter;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.Toast;

/**
 * This class contains functions used by MainActivity for handling the custom dictionary for our SpeechRecognizer implementation
 * 
 * @author Gordon
 *
 */
public class TrainerFunctions {

	public static void initTrainerButtons(final Spinner commands, final Activity act, final SharedPreferences prefs, final ArrayList<String> data){
		Button trainButton = (Button) act.findViewById(R.id.trainButton);
		trainButton.setOnClickListener(new OnClickListener(){
			@Override
			public void onClick(View arg0) {
				String command = commands.getSelectedItem().toString();
				for (int i = 0; i<data.size(); i++){
					TrainerFunctions.writeTrainer(command,data.get(i), act, prefs);
				}
			}
			
		});
		
		
		Button getTrainedButton = (Button) act.findViewById(R.id.getTrainedButton);
		getTrainedButton.setOnClickListener(new OnClickListener(){
			@Override
			public void onClick(View arg0){
				String command = commands.getSelectedItem().toString();
				TrainerFunctions.readTrainer(command, act, prefs);	
				TrainerFunctions.exportTrainer(commands, command, act, prefs);
			}
		});
		
	}

	
	/**
	 * Places a (word, command) pair into our SharedPreferences
	 * Also adds a word onto a command array
	 * 
	 * @param command 
	 * @param word 
	 */
	public static void writeTrainer(String command, String word, Activity act, SharedPreferences prefs){
    	if (command.equals(word))
    		return;
    	
    	String result = prefs.getString(word, "none");
    	
    	if (result.equals("none")){
    		SharedPreferences.Editor editor = prefs.edit();
        	editor.putString(word, command);
        	
        	
        	Set<String> commandWords = prefs.getStringSet(command,null);
        	if (commandWords != null){
        		commandWords.add(word);
        		editor.putStringSet(command, commandWords);
        	}else{
        		Set<String> commandWordsSet = new HashSet<String>();
        		commandWordsSet.add(word);
        		editor.putStringSet(command, commandWordsSet);
        	}
        	editor.commit();	
	    }else{
	    	Toast.makeText(act, "Duplicate trained command: cmd = " +command+", word ="+word, Toast.LENGTH_LONG).show();
	    	
	    }
    	

    }
    	
    /**
     * Gets the command that the input word is associated with
     * @param word 
     * @return the command that the word is associated with
     */
    public static String getTrainer(String word, Activity act, SharedPreferences prefs){
    	Spinner spinner = (Spinner) act.findViewById(R.id.commands);
    	Adapter adapter = spinner.getAdapter();
    	for (int i = 0; i < adapter.getCount(); i++){
    		if (adapter.getItem(i).equals(word))
    			return word;
    	}
    	
    	String result = prefs.getString(word, "none");
    	if (!result.equals("none")){
    		return result;
    	}else{
	    	Toast.makeText(act, "Word not in database = "+ word, Toast.LENGTH_LONG).show();
	    	return "";
    	}
    }
    
    /**
     * Reads our dictionary and prints out all the words that can also be used for the command
     * @param command the command we are checking the dictionary for 
     */
    public static void readTrainer(String command, Activity act, SharedPreferences prefs){
    	Set<String> result = prefs.getStringSet(command, null);
		ListView listView = (ListView) act.findViewById(R.id.words); 

    	if (result == null){
    		listView.setAdapter(null);
	    	Toast.makeText(act, "nothing here boss", Toast.LENGTH_LONG).show();
    	}else{
    		Iterator<String> iterator = result.iterator();
		    final ArrayList<String> list = new ArrayList<String>();

    		while (iterator.hasNext()){
    		    list.add(iterator.next());
    		    // Log.v(TAG,"command = "+iterator.next());
    		}
    		final ArrayAdapter<String> adapter = new ArrayAdapter<String>(act, R.layout.listitem, list);
    		listView.setAdapter(adapter);

    	}
    }
    
    
    /**
     * Exports our dictionary trainer to a .txt file as JSON arrays
     */
    public static void exportTrainer(Spinner commands, String TAG, Activity act, SharedPreferences prefs){
    	try {
			String filePath = Environment.getExternalStorageDirectory().toString() + "/trainer.txt";
			File myFile = new File(filePath);
			myFile.createNewFile();
			
			FileWriter fw = new FileWriter(myFile.getAbsoluteFile());
			BufferedWriter bw = new BufferedWriter(fw);
			
			//myOutWriter.write("");
			JSONObject json = new JSONObject();

			
	    	for (int i = 0; i < commands.getCount(); i++){
	    		String command = commands.getItemAtPosition(i).toString();
	    		
	        	Set<String> result = prefs.getStringSet(command, null);
	        	if (result != null){
	        		
	        		JSONArray commandSet = new JSONArray();
	        		Iterator<String> iterator = result.iterator();
	        		while (iterator.hasNext()){
	        			String word = iterator.next();
	        		    Log.v(TAG,command +" , " +word);
	        			commandSet.put(word);
	        		}
	        		json.put(command, commandSet);
	        	}

	    			
	    	}
		    Log.v(TAG,json.toString());

		    bw.write(json.toString());
		    bw.close();
		    fw.close();	
		    
	    	Toast.makeText(act, "Done writing SD ", Toast.LENGTH_LONG).show();

    	} catch (Exception e) {
			Toast.makeText(act, e.getMessage(), Toast.LENGTH_SHORT).show();
		}
	
    }
	
}
