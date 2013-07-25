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
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.os.Environment;
import android.util.Log;
import android.view.View;
import android.widget.Adapter;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

/**
 * This class contains functions used by MainActivity for handling the custom dictionary for our SpeechRecognizer implementation
 * 
 * @author Gordon
 *
 */
public class TrainerFunctions {
	
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
    public static String getTrainer(String word, Context ctx, SharedPreferences prefs){
    	String[] commandsArray = ctx.getResources().getStringArray(R.array.commands);
    	
   	    
    	for (int i = 0; i < commandsArray.length; i++){
    		if (commandsArray[i].equals(word))
    			return word;
    	}
    	
    	String result = prefs.getString(word, "none");
    	if (!result.equals("none")){
    		return result;
    	}else{
	    	//Toast.makeText(act, "Word not in database = "+ word, Toast.LENGTH_LONG).show();
	    	return word;
    	}
    }
    
    
    /**
     * Checks if the word is one of the commands
     * 
     * @param word
     * @param act
     * @return true if the word is a command, false otherwise
     */
    public static boolean isCommand(String word, Context ctx){
   	    String[] commandsArray = ctx.getResources().getStringArray(R.array.commands);
    
    	for (int i = 0; i < commandsArray.length; i++){
    		if (commandsArray[i].equals(word))
    			return true;
    	}
    	
    	return false;
    }
    
    /**
     * Reads our dictionary and prints out all the words that can also be used for the command
     * @param command the command we are checking the dictionary for 
     */
    public static void readTrainer(final String command, final Activity act, final SharedPreferences prefs){
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
    		listView.setOnItemClickListener(new OnItemClickListener(){

				@Override
				public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
					// TODO make this delete the entry
			        final String word = ((TextView)view).getText().toString();
					//Log.v("word",((TextView)view).getText().toString());
					
			        AlertDialog.Builder alertDialog = new AlertDialog.Builder(act);
			        alertDialog.setTitle("Confirm Delete...");
			        alertDialog.setMessage("Are you sure you want delete this word: "+ word);
			        
			        // Setting Positive "Yes" Btn
			        alertDialog.setPositiveButton("YES",
			                new DialogInterface.OnClickListener() {
			                    public void onClick(DialogInterface dialog, int which) {
			                    	removeWord(word,act,prefs);
			                    	readTrainer(command,act,prefs);
			                    }
			                });
			        // Setting Negative "NO" Btn
			        alertDialog.setNegativeButton("NO",
			                new DialogInterface.OnClickListener() {
			                    public void onClick(DialogInterface dialog, int which) {
			                    	dialog.cancel();
			                    }
			                });
			         
			        // Showing Alert Dialog
			        alertDialog.show();
				}
				
    			
    		});
    	}
    }
    
    
    /**
     * Removes a word from the dictionary.
     * 
     * @param word
     * @param act
     * @param prefs
     * @return
     */
    public static boolean removeWord(String word, Activity act, SharedPreferences prefs){
    	String command = prefs.getString(word, "none");
 
    	if (!command.equals("none")){
    		SharedPreferences.Editor editor = prefs.edit();
        	editor.remove(word);
        	
        	Set<String> result = prefs.getStringSet(command, null);
        	result.remove(word);
        	
    		editor.putStringSet(command, result);
    		editor.commit();
        	return true;
    	}
    	
    	return false;
    	
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
		    
	    	//Toast.makeText(act, "Done writing SD ", Toast.LENGTH_LONG).show();

    	} catch (Exception e) {
			Toast.makeText(act, e.getMessage(), Toast.LENGTH_SHORT).show();
		}
	
    }
	
}
