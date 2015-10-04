package myakka;

import static org.junit.Assert.*;

import java.text.NumberFormat;

import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import akka.dispatch.Envelope;
import akka.dispatch.MessageQueue;
import akka.dispatch.UnboundedMailbox;

/**
 * This test iterates through large numbers of inserts and removals of the
 * MonitoredQueue object. It also tests to make sure that 
 * 
 * We want to be able to compare the performance of monitored and unmonitored 
 * queues, so in this test, we use JUnit asserts instead of Hamcrest asserts 
 * because they are a bit faster.
 */
public class MonitoredQueueLoadTest {
	final Logger log = LoggerFactory.getLogger(MonitoredQueueLoadTest.class);
	
	final static String PREFIX = "testPrefix";

	@Test
	public void testMonitoredSingleThread() {
		MonitoredQueue queue = new MonitoredQueue(PREFIX);
		testSingleThread(queue, 10000);
		testSingleThread(queue, 100000);
		testSingleThread(queue, 1000000);
	}

	@Test
	public void testUnmonitoredSingleThread() {
		MessageQueue queue = new UnboundedMailbox.MessageQueue();
		testSingleThread(queue, 10000);
		testSingleThread(queue, 100000);
		testSingleThread(queue, 1000000);
	}

	void testSingleThread(MessageQueue queue, int numMsgs) {
		Envelope env = new Envelope("Test", null);
		
		log.info("*** testing "+queue.getClass().getSimpleName()+
				" with "+f(numMsgs)+" msgs ***");
		// enqueue
		{
			long start = System.nanoTime();
			for (int i = 0; i < numMsgs; i++) {
				queue.enqueue(null, env);
			}
			long end = System.nanoTime();

			long delta = end - start;
			log.info(f(numMsgs)+" enqueued, took "+f(delta)+" nanos");
			log.info("avg nanos/msg: "+delta/numMsgs);
		}

		assertEquals(numMsgs, queue.numberOfMessages());

		// dequeue
		{
			long start = System.nanoTime();
			for (int i = 0; i < numMsgs; i++) {
				// JUnit assert is a little faster than Hamcrest
				assertNotNull(queue.dequeue()); 
			}
			long end = System.nanoTime();

			long delta = end - start;
			log.info(f(numMsgs)+" dequeued, took "+f(delta)+" nanos");
			log.info("avg nanos/msg: "+delta/numMsgs);
		}

		assertEquals(0, queue.numberOfMessages());
		assertNull(queue.dequeue());
		
		// combined enqueue/dequeue (keeps queue shallow)
		{
			long start = System.nanoTime();
			for (int i = 0; i < numMsgs; i++) {
				queue.enqueue(null, env);
				assertNotNull(queue.dequeue());
			}
			long end = System.nanoTime();

			long delta = end - start;
			log.info(f(numMsgs)+" combined, took "+f(delta)+" nanos");
			log.info("avg nanos/msg: "+delta/numMsgs);
		}
		
		assertEquals(0, queue.numberOfMessages());
		assertNull(queue.dequeue());
	}
	
	// pretty-print helper - we are dealing with some large numbers
	private String f(long num) {
		return NumberFormat.getIntegerInstance().format(num);
	}
	
}
