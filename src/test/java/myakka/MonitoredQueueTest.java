package myakka;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.sameInstance;

import org.junit.Test;

import akka.actor.ActorRef;
import akka.dispatch.Envelope;

import com.jamonapi.Monitor;
import com.jamonapi.MonitorFactory;

/**
 * Validates the correctness of the MonitoredQueue class.
 */
public class MonitoredQueueTest {

    final static String PREFIX = "testPrefix";
    
    // it's ok that these are null for now
    ActorRef SENDER = null; //mock(ActorRef.class);
    ActorRef RECEIVER = null; //mock(ActorRef.class); 
    
    @Test
    public void testEmptyQueue() {
        MonitoredQueue queue = new MonitoredQueue(PREFIX);

        assertThat(queue.numberOfMessages(), is(0));
        assertThat(queue.hasMessages(), is(false));
        assertThat(queue.dequeue(), is(nullValue()));
    }
    
    @Test
    public void testOneMessage() {
        MonitoredQueue queue = new MonitoredQueue(PREFIX);
        
        // add a message
        Envelope in = new Envelope("Test", SENDER);
        queue.enqueue(RECEIVER, in);
        assertThat(queue.hasMessages(), is(true));
        assertThat(queue.numberOfMessages(), is(1));
        
        // remove the message, check that it's the right one
        Envelope out = queue.dequeue();        
        assertThat(out, sameInstance(in));

        // check empty queue
        assertThat(queue.numberOfMessages(), is(0));
        assertThat(queue.hasMessages(), is(false));
        assertThat(queue.dequeue(), is(nullValue()));
    }
    
    @Test
    public void testStatsCollection() throws InterruptedException {
    	MonitoredQueue queue = new MonitoredQueue(PREFIX);

        Monitor queueDepth = MonitorFactory.getMonitor(PREFIX+".queueDepth", "count");
        Monitor queueLatency = MonitorFactory.getMonitor(PREFIX+".queueLatency", "millis");
        assertThat(queueDepth, not(nullValue()));
        assertThat(queueLatency, not(nullValue()));

        queueDepth.reset();
        queueLatency.reset();
        
    	// add a message
        Envelope in = new Envelope("Test", SENDER);
        queue.enqueue(RECEIVER, in);
        assertThat(queue.hasMessages(), is(true));
        assertThat(queue.numberOfMessages(), is(1));
    	
        // check queueDepth monitor
        assertThat(queueDepth.getHits(), is(1.0));
        assertThat(queueDepth.getLastValue(), is(0.0));

        // sleep at least 1 millisecond, then de-queue message
        Thread.sleep(1);
        assertThat(queue.dequeue(), sameInstance(in));
        
        // check queueLatency monitor
        assertThat(queueLatency.getHits(), is(1.0));
        assertThat(queueLatency.getLastValue(), greaterThan(0.0));
    }
    
}
