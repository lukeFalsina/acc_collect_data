package dk.au.cs.accelerometercollectdata;

import java.util.ArrayList;
import java.util.List;
import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;

public class ComputationHandler {

	private List<float[]> results;
	private DescriptiveStatistics stats;
	
	public ComputationHandler() {
		results = new ArrayList<float[]>();
	}
	
	public List<float[]> getResults() {
		return results;
	}

	public void processData(List<float[]> incomingData) {
	
		if(incomingData == null) return;
		
		for(int i = 0; i < incomingData.get(0).length; i++) {
			
			// Initialize the statistics descriptor with the data
			stats = new DescriptiveStatistics(convertFloatsIndexToDoubles(incomingData, i));
			
			float[] tempResults = new float[3];
			// Store the minimum value
			tempResults[0] = (float) stats.getMin();
			// Store the maximum value
			tempResults[1] = (float) stats.getMax();
			// Store the standard deviation
			tempResults[2] = (float) stats.getStandardDeviation();
			
			// Min, max and std dev are stored into the results element.
			results.add(tempResults);
		}
	}
	
	private double[] convertFloatsIndexToDoubles(List<float[]> input, int index)
	{
		// Check not null input and that the position is smaller than 
		// the length of the float array
	    if (input == null || input.get(0).length < index)
	    {
	        return null;
	    }
	    
	    double[] output = new double[input.size()];
	    
	    for (int i = 0; i < input.size(); i++)
	    {
	        output[i] = input.get(i)[index];
	    }
	    return output;
	}
	
}
