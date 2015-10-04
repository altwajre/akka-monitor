package myakka;

import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import akka.actor.ActorRef;
import akka.dispatch.*;

import com.jamonapi.MonitorFactory;

/**
 * This class implements the standard akka UnboundedMessageQueueSemantics,
 * (FIFO, unbounded) but adds in additional logging/monitoring capability.
 *  
 * <p>Queue statistics are aggregated into an in-memory statistics collection
 * API called JAMon. See the <a href="http://jamonapi.sourceforge.net/">JAMon
 * Sourceforge</a> page for more info.
 * 
 * <p>The following JAMon statistics are collected by this class:
 * 
 * <ul>
 * <li><code>msgstats.[prefix].queueDepth</code> - measures the depth of the 
 *     queue at the time of message arrival (before insertion.)</li>
 * <li><code>msgstats.[prefix].queueLatency</code> - measures the amount of
 *     time each message takes to travel from the back to the front of the
 *     queue.</li>
 * </ul>
 *
 * <p>For debugging purposes, each queue has its own logger, with corresponding
 * logger path msgstats.[prefix]. To log all reporting events, turn up logging
 * to TRACE level for this class.
 */
public class MonitoredQueue 
	implements MessageQueue, UnboundedMessageQueueSemantics 
{
	/*
	 * We will want to create a custom logger for each actor,
	 * so that we can turn on/off low level debugging at will.
	 * The naming convention in this class is:
	 * 
	 * msgstats.<actorPath>
	 */
	private final Logger log;
	
	/* 
	 * This is the prefix added to every logged/monitored
	 * event. Ideally, this prefix is unique to a class of
	 * actor, but not a specific actor instance (unless
	 * you need that level of granularity). For every unique
	 * prefix, there is in-memory overhead in the stats
	 * collection engine (JAMon).
	 */
	private final String prefix;
	
	/*
	 * Two concurrency-handling objects are needed. The first is
	 * a ConcurrentLinkedQueue to handle the message, and the 
	 * second is an AtomicInteger to keep track of the current
	 * queue depth. Akka's default unbounded queue implementation
	 * also utilizes (actually, extends) ConcurrentLinkedQueue.
	 */
	private final ConcurrentLinkedQueue<TimedEnvelope> queue =
			new ConcurrentLinkedQueue<TimedEnvelope>();
	
	/*
	 * We track queue depth in this AtomicInteger.
	 * 
     * This approach doubles the offer() and poll() processing 
     * times, although they still remain O(1) operations. Note 
     * that it is technically possible that this strategy will
     * cut the total possible throughput of this queue in half,
     * due to the need to access the AtomicInteger's backing
     * value on both the enqueue() and dequeue() sides of this
     * queue. (In a partially filled linked-list based queue, 
     * head and tail reside at different memory locations.)
	 * 
	 * 
	 * In a cleaner implementation, we would probably extend 
	 * ConcurrentLinkedQueue and wrap key operations in there, 
	 * however there are quite a few things to consider when 
	 * attempting to do this correctly and exhaustively.
	 * 
	 */
	private final AtomicInteger queueDepth = 
			new AtomicInteger(0);

	/**
	 * Each enqueued object is wrapped in a TimedEnvelope object,
	 * so that we can record and maintain the enqueue time for each 
	 * message.
	 */
	class TimedEnvelope {
		private final Envelope env;
		private final long createTimeMillis;
		
		TimedEnvelope(Envelope env) {
			this.env = env;
			this.createTimeMillis = System.currentTimeMillis();
		}
		
		/** 
		 * The actual payload - an Envelope object.
		 */
		Envelope getEnvelope() {
			return env;
		}
		
		/**
		 * Returns the time (in millis) since this envelope was created.
		 */
		long getDuration() {
			return System.currentTimeMillis() - createTimeMillis;
		}
	}
	
	/**
	 * Constructor - see {@link myakka.MonitoredUnboundedMailbox} to 
	 * see (and possibly alter) how 'prefix' is generated. Note that the
	 * more unique prefixes you use over the life of the object, the more
	 * distinct Monitors you will create in JAMon. So choose your 
	 * prefix calculation wisely!
	 */
	MonitoredQueue(String prefix) {
		this.prefix = prefix;
		this.log = LoggerFactory.getLogger("msgstats."+prefix);
	}
	
	/**
	 * Helper - write to JAMon and (if enabled) trace to log.
	 */
	private void logStat(String identifier, String units, long value) {
		MonitorFactory.add(identifier, units, value);
		if (log.isTraceEnabled()) {
			log.trace(identifier+": "+value+" "+units);
		}
	}
	
	/**
	 * Enqueue a message.
	 */
	public void enqueue(ActorRef receiver, Envelope env) {
		queue.offer(new TimedEnvelope(env));
		int depth = queueDepth.getAndIncrement();

		// log queue depth (excludes just-added message)
		logStat(prefix+".queueDepth", "count", depth);
	}

	/**
	 * Dequeue the next message. Returns immediately.
	 * 
	 * @return an Envelope, or null if the queue is empty.
	 */
	public Envelope dequeue() {
		TimedEnvelope timedEnv = queue.poll();
		if (timedEnv == null) {
			return null;
		}
		
		// we found a messages, so decrement the queue counter
		queueDepth.decrementAndGet();
		
		// record time spent in queue
		long queueMillis = timedEnv.getDuration();
		logStat(prefix+".queueLatency", "millis", queueMillis);

		return timedEnv.getEnvelope();
	}
	
	/**
	 * Returns the current queue depth.
	 */
	public int numberOfMessages() {
		return queueDepth.get();
	}

	/**
	 * Returns true if the queue is not empty.
	 */
	public boolean hasMessages() {
		return !queue.isEmpty();
	}
	
	/**
	 * Purge the contents of this Queue to deadLetter queue. Presumably
	 * this is called when an actor is terminated.
	 */
    public void cleanUp(ActorRef owner, MessageQueue deadLetters) {
    	// drain everything to the dead-letter queue 
    	// (no stats collected)
    	for (TimedEnvelope timedEnv: queue) {
          deadLetters.enqueue(owner, timedEnv.getEnvelope());
        }
    } 
    
    /**
     * For debugging/testing purposes.
     */
    public String getMonitorPrefix() {
        return prefix;
    }
}