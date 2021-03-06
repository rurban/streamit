package at.dms.kjc.spacedynamic;

import at.dms.kjc.flatgraph.FlatNode;
import java.util.HashMap;
import java.util.Vector;
import java.util.Iterator;
import java.util.Collection;

/** 
 * This class keeps the counters for weights of the splitter/joiners
 * and performs the test to check whether the simulation is finished 
 */
public class JoinerCounter {
    
    private HashMap<FlatNode, int[]> counts;
    
    public JoinerCounter() {
        counts = new HashMap<FlatNode, int[]>();
    }


    public int getCount(FlatNode node, int inputN) {
        /* Create counters in the hashmap if this node has not
           been visited already 
        */
        if (!counts.containsKey(node)) {
            int[] nodeCounters = new int[node.inputs];
            for (int i = 0; i < node.inputs; i++) {
                nodeCounters[i] = node.incomingWeights[i];
            }
            counts.put(node, nodeCounters);
        }
        //Get the counter and return the count for the given input
        int[] currentCounts = counts.get(node);
        return currentCounts[inputN];
    }
    
    public void decrementCount(FlatNode node, int inputN) 
    {
        int[] currentCounts = counts.get(node);
        if (currentCounts[inputN] > 0)
            currentCounts[inputN]--;
        else 
            System.err.println("Trying to decrement an input with a zero count.");
    
        counts.put(node, currentCounts);
    }
    
    public void resetCount(FlatNode node, int inputN) 
    {
        int[] currentCounts = counts.get(node);
        if (currentCounts[inputN] == 0)
            currentCounts[inputN] = node.incomingWeights[inputN];
        else
            System.err.println("Trying to reset a non-zero counter.");
        counts.put(node, currentCounts);
    }
    
    public boolean checkAllZero() 
    {
        Iterator<int[]> allCounts = counts.values().iterator();
        while (allCounts.hasNext()) {
            int[] currentCounters = allCounts.next();
            for (int i = 0; i < currentCounters.length; i++) {
                if (currentCounters[i] > 0) 
                    return false;
            }
        }
        return true;
    }
    
}

