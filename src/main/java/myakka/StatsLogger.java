package myakka;

import java.util.*;
import java.util.concurrent.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.jamonapi.Monitor;
import com.jamonapi.MonitorFactory;

/** 
 * This class writes in-memory JAMon statistics to a log file, and then
 * resets JAMon statistics collection.
 * 
 * In a production-quality variant of this class, the data should really be 
 * written to a system equipped to manage time-series statistics.
 */
public class StatsLogger {
	private final static Logger log = 
	        LoggerFactory.getLogger(StatsLogger.class);

	private final static ScheduledExecutorService scheduler =
			Executors.newScheduledThreadPool(1);
	
	/** 
	 * No public constructor, class is intended to be used statically.
	 * See logAndClearStats() method. 
	 */
	private StatsLogger() {}
	
    /**
     * This method pulls data from MonitorFactory and then writes to a
     * logger. Once written, the in-memory stats are 'reset' so that
     * a new running average, stdev, max, min, etc. is calculated during
     * every interval.
     * 
     * <p>IMPORTANT CAVEAT:
     *     Some stats may be accumulated between the time that a Monitor object
     *     is interrogated and when it's reset. But hey, they're just stats, 
     *     not customer data!
     */
	public static void logAndClearStats() {
	    // lead each log burst with a header line
	    log.info(header());

	    // Note: monitors aren't sorted. JAMon uses a ConcurrentHashMap
	    // as the backing object (which you can override) so there is
	    // no guarantee of ordering.
	    Collection<Monitor> monitors = MonitorFactory.getMap().values();
	    
	    // Sorting to facilitate easy log reading.
	    List<Monitor> sorted = new ArrayList<Monitor>(monitors);
	    Collections.sort(sorted, new AscendingLabelComparator());
	    
	    for (Monitor mon : sorted) {
	        // skip 'empty' monitors
	        if (mon.getHits() < 1)
	            continue;

	        StringBuffer sb = new StringBuffer();
	        append(sb, mon.getLabel(), 50);
	        append(sb, mon.getUnits(), 10);
	        appendI(sb, mon.getHits(), 10);
	        appendI(sb, mon.getMin(), 10);
	        appendI(sb, mon.getMax(), 10);
	        appendF(sb, mon.getAvg(), 10);
	        appendF(sb, mon.getStdDev(), 10);
	        appendI(sb, mon.getLastValue(), 10);
            appendI(sb, mon.getTotal(), 15);

	        log.info(sb.toString());
	        
	        // purge as we write
	        mon.reset();
	        MonitorFactory.remove(mon.getMonKey());
	    }
	}

	private static String header() {
	    StringBuffer sb = new StringBuffer();
	    append(sb, "Label", 50);
	    append(sb, "Units", 10);
	    append(sb, "Hits", 10);
	    append(sb, "Min", 10);
	    append(sb, "Max", 10);
	    append(sb, "Avg", 10);
	    append(sb, "StdDev", 10);
	    append(sb, "Last", 10);
        append(sb, "Total", 15);
	    return sb.toString();
	}

	/* Some string helpers below, to facilitate pretty-printing. */

	private static void appendI(StringBuffer sb, double d, int len) {
	    append(sb, String.format("%.0f", d), len);
	}

	private static void appendF(StringBuffer sb, double d, int len) {
	    append(sb, String.format("%.2f", d), len);
	}

	private static void append(StringBuffer sb, String str, int len) {
	    // clip and pad as necessary
	    if (str.length() > len) {
	        sb.append(str.substring(0, len));
	    } else {
	        sb.append(String.format("%1$-" + len + "s", str));
	    }
	}
	
	/* Sorting, for readability (not necessary if writing to a DB, etc.) */
	static class AscendingLabelComparator implements Comparator<Monitor> {
	    public int compare(Monitor m1, Monitor m2) {
	    	int diff = m1.getLabel().compareTo(m2.getLabel());
	    	if (diff == 0) {
	    		diff = m1.getUnits().compareTo(m2.getUnits());
	    	}
	    	return diff;
	    }
	}
}
