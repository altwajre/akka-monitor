package myakka;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * This very simplistic scheduler will configure calls to StatsLogger
 * at regular intervals. It's meant to be started and stopped only
 * once.
 */
public class StatsLoggerScheduler {

	private final static ScheduledExecutorService scheduler =
			Executors.newScheduledThreadPool(1);

	/**
	 * Private constructor. This class is intended to be used statically.
	 */
	private StatsLoggerScheduler() {}
	
	/**
	 * Starts a timer that periodically logs and expunges JAMon's in-memory
	 * statistics data.
	 *  
	 * Call this exactly once at VM startup.
	 */
	public static void start(long intervalMillis) 
			throws IllegalArgumentException {
		if (intervalMillis < 1) {
			throw new IllegalArgumentException(
			        "intervalMillis must be greater than zero");
		}
		
		scheduler.scheduleAtFixedRate(new StatsRunnable(), 
				intervalMillis, intervalMillis, TimeUnit.MILLISECONDS);		
	}
	
	/** 
	 * Halts stats collection/logging. This simple implementation cannot be 
	 * re-started once stopped.
	 */
	public static void stop() {
		scheduler.shutdownNow();
	}
	
	/**
	 * This very simple class wraps a call to logAndClearStats() in a Runnable.
	 */
	static class StatsRunnable implements Runnable {
		StatsRunnable() {}
	
		public void run() {
		    StatsLogger.logAndClearStats();
		}
	}
}
