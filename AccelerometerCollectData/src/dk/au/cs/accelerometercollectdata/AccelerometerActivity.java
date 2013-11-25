package dk.au.cs.accelerometercollectdata;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Context;
import android.util.Log;
import android.view.Menu;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

public class AccelerometerActivity extends Activity {

	private static final String TAG = AccelerometerActivity.class.getSimpleName();
	
	// This constant set the length of the sliding window
	private static final int windowSize = 128;
	
	private SensorManager sensorManager;
	private final SensorEventListener accelerometerSensorEventListener = new AccelerometerSensorEventListener();

	// This data structure contains the linear accelerations retrieved 
	// by the accelerometer sensor.
	private static List<float[]> linAccHistory = new ArrayList<float[]>();
	
	// This handler is used to update the GUI on separate threads
	private Handler GUIHandler;
	
	// This handler is used to manage the computation of the data on the sliding windows
	private static ComputationHandler compHandler;
	
	// File used to store min, max and std dev of the collected samples
	private File resultsFile;
	
	private ProgressDialog dialog;
	
	private static Button saveButton;
	
	/* Checks if external storage is available for read and write */
	public boolean isExternalStorageWritable() {
	    String state = Environment.getExternalStorageState();
	    if (Environment.MEDIA_MOUNTED.equals(state)) {
	        return true;
	    }
	    return false;
	}
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_accelerometer);
		
		sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
		
		compHandler = new ComputationHandler();
		
		if(!isExternalStorageWritable()) Log.e(TAG, "No permission to write external files!");
		else {
			// Open the file to store the results..
			resultsFile = new File(Environment.getExternalStorageDirectory(), "File_Results.txt");
		}
		
		// The Export button is made unclickable until enough data are collected..
		saveButton = (Button) findViewById(R.id.SaveButton);
		saveButton.setClickable(false);
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.accelerometer, menu);
		return true;
	}
	
	@Override
	protected void onResume() {
		Sensor accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
		if (!sensorManager.registerListener(accelerometerSensorEventListener, accelerometer, SensorManager.SENSOR_DELAY_FASTEST)) {
			Log.w(TAG, "Couldn't register accelerometer.");
		}
	}
 
	@Override
	protected void onPause() {
		sensorManager.unregisterListener(accelerometerSensorEventListener);
	}
	
	private static final class AccelerometerSensorEventListener implements SensorEventListener {

		private float[] gravity = {0.0f, 0.0f, 0.0f}, linear_acceleration = {0.0f, 0.0f, 0.0f};
		
		@Override
		public void onAccuracyChanged(Sensor sensor, int accuracy) {
			// Do something when the accuracy has changed
			Log.w(TAG, "Accuracy of sensor " + sensor.getName() + " is changed of " + accuracy);
		}
 
		@Override
		public void onSensorChanged(SensorEvent event) {
			
			// alpha is calculated as t / (t + dT)
	        // with t, the low-pass filter's time-constant
	        // and dT, the event delivery rate

	        final float alpha = 0.8f;
	        
			// A low pass filter is used to retrieve the gravity values
	        gravity[0] = alpha * gravity[0] + (1 - alpha) * event.values[0];
	        gravity[1] = alpha * gravity[1] + (1 - alpha) * event.values[1];
	        gravity[2] = alpha * gravity[2] + (1 - alpha) * event.values[2];

	        linear_acceleration[0] = event.values[0] - gravity[0];
	        linear_acceleration[1] = event.values[1] - gravity[1];
	        linear_acceleration[2] = event.values[2] - gravity[2];
	        
	        // The results of this samples are stored into the history object
	        linAccHistory.add(linear_acceleration);
	        checkWriteTime();
		}
 
	}

	public static void checkWriteTime() {
		
		if (linAccHistory.size() >= windowSize) {
			
			final List<float[]> dataToProcess = linAccHistory.subList(0, windowSize - 1);
			
			// Initialize a new thread to perform the heavy computation on data..
			Thread computationThread = new Thread() {
				
				@Override
				public void run() {
					
					// Min, max and standard deviation of the current window are 
					// calculated and stored
					compHandler.processData(dataToProcess);
				}
			};
			
			// Discard the first windowSize / 2 items in the sliding window.
			linAccHistory = linAccHistory.subList((int) windowSize / 2 , linAccHistory.size() - 1);
			
			// Start the computation thread..
			computationThread.start();
			
			// Check the save button and if needed make it clickable.
			// In fact enough data have been collected so a save operation could eventually start.
			if (!saveButton.isClickable()) saveButton.setClickable(true);
		}
	}
	
	public void onSaveResultsOnExternalStorage(View view) {
		
		// A progress dialog is called to let the user know about file creation
    	dialog = ProgressDialog.show(this, "Loading", "Exporting the results file..");
    	
    	Thread fileThread = new Thread() {
    		
    		@Override
    		public void run() {
    			
    			List<float[]> results = compHandler.getResults();
    			
    			if (!results.isEmpty()) {
    				
    				// Write results in the previously open external file
    			    try {
    			        FileOutputStream f = new FileOutputStream(resultsFile);
    			        PrintWriter pw = new PrintWriter(f);
    			        
    			        // Results are scanned and written on the file..
    			        Iterator<float[]> resultsIterator = results.iterator();
    			        int resultsCounter = 0;
    			        
    			        pw.println("List of the parameters read from the accelerometer");
    			        pw.println("Sliding window dimension: " + windowSize + " samples\n");
    			        
    			        while (resultsIterator.hasNext()) {
    			        	
							float[] currentElement = (float[]) resultsIterator.next();
							
							// Depending on the number or iteration a different dimension of the 
							// linear acceleration is retrieved and stored.
							switch(resultsCounter % 3) {
							
								case 0:		pw.println("Sliding window nr. " + ((int) resultsCounter / 3 + 1));
											pw.println("Linear acceleration - X dimension -> Min: " + currentElement[0] + 
													"; Max: " + currentElement[1] + "; Std Dev: " + currentElement[2] + ";");
											break;
								
								case 1:		pw.println("Linear acceleration - Y dimension -> Min: " + currentElement[0] + 
													"; Max: " + currentElement[1] + "; Std Dev: " + currentElement[2] + ";");
											break;
											
								case 2:		pw.println("Linear acceleration - Z dimension -> Min: " + currentElement[0] + 
											"; Max: " + currentElement[1] + "; Std Dev: " + currentElement[2] + ";\n");
											break;
							}
							
							// The counter is increased after each iteration
							resultsCounter ++;
						}
    			        
    			        pw.flush();
    			        pw.close();
    			        f.close();
    			        
    	    			// The GUI handler is started to update the GUI
    	    			GUIHandler.post(new Runnable () {

    						@Override
    						public void run() {
    					        
    					        // The progress dialog is dismissed here..
    					        dialog.dismiss();
    							
    					        // A toast element is invoked to inform that the 
    					        // operation was completed with success..
    					        Toast.makeText(AccelerometerActivity.this,
    									"File export completed successfull in " + Environment.getExternalStorageDirectory().toString(),
    									Toast.LENGTH_SHORT).show();
    						}
    	    				
    	    			});
    	    			
    			    } catch (FileNotFoundException e) {
    			        e.printStackTrace();
    			        Log.i(TAG, "******* File not found.");
    			        
    	    			// The GUI handler is started to update the GUI
    	    			GUIHandler.post(new Runnable () {

    						@Override
    						public void run() {
    					        
    					        // The progress dialog is dismissed here..
    					        dialog.dismiss();
    							
    					        // A toast element is invoked to inform about the error..
    					        Toast.makeText(AccelerometerActivity.this,
    									"Error! File not found",
    									Toast.LENGTH_SHORT).show();
    						}
    	    				
    	    			});
    	    			
    			    } catch (IOException e) {
    			        e.printStackTrace();
    			        
       	    			// The GUI handler is started to update the GUI
    	    			GUIHandler.post(new Runnable () {

    						@Override
    						public void run() {
    					        
    					        // The progress dialog is dismissed here..
    					        dialog.dismiss();
    							
    					        // A toast element is invoked to inform about the error..
    					        Toast.makeText(AccelerometerActivity.this,
    									"Error! Input/Output Exception",
    									Toast.LENGTH_SHORT).show();
    						}
    	    				
    	    			});
    			    }  
    			}
    		}
    	};
    	
    	// Make run the file thread.
    	fileThread.start();
	}

}
