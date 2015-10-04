package myakka;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.lessThan;

import org.junit.After;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import scala.concurrent.duration.FiniteDuration;
import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;
import akka.testkit.JavaTestKit;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

/**
 * This test spins up two akka actors, and asks them to send large batches
 * of messages between each other. This test makes sure that the operations
 * complete successfully with both a monitored and unmonitored queue.
 */
public class ActorLoadTest {
    private final Logger log = LoggerFactory.getLogger(ActorLoadTest.class);
    
    /* Setup and Cleanup */
    
    ActorSystem system;
        
    @After
    public void tearDown() {
        if (system != null) {
            system.shutdown();
        }
    }

    /* Test(s) */
    
    @Test
    public void testMonitored() throws InterruptedException {
        Config config = ConfigFactory.load();

        // the 'monitored' section of application.conf contains this tweak: 
        // akka.actor.default-mailbox.mailbox-type = "myakka.MonitoredUnboundedMailbox"
        config = config.getConfig("monitored").withFallback(config);

        // let the JVM get into a groove
        for (int i = 0; i < 10; i++) {
            system = ActorSystem.create("system", config);
            testMessages(100000, "30 seconds");
            system.shutdown();
        }
    
    }

    @Test
    public void testUnonitored() throws InterruptedException {
        Config config = ConfigFactory.load();
        
        // let the JVM get into a groove
        for (int i = 0; i < 10; i++) {
            system = ActorSystem.create("system", config);
            testMessages(100000, "30 seconds");
            system.shutdown();
        }    
    }
    
    @Test
    public void testDifference() {
        final int numMsgs = 100000;
        final String duration = "30 seconds";
        final int numTests = 10;

        // We will capture and use the best result from 'numTests' runs
        long bestMonitored = Long.MAX_VALUE;
        long bestUnmonitored = Long.MAX_VALUE;
        
        // test without monitoring
        Config config = ConfigFactory.load();

        for (int i = 0; i < numTests; i++) {
            system = ActorSystem.create("system", config);
            long time = testMessages(numMsgs, duration);
            bestUnmonitored = Math.min(time, bestUnmonitored);
            system.shutdown();
        }

        // now test with monitoring enabled
        config = config.getConfig("monitored").withFallback(config);
        
        for (int i = 0; i < numTests; i++) {
            system = ActorSystem.create("system", config);
            long time = testMessages(numMsgs, duration);
            bestMonitored = Math.min(time, bestMonitored);
            system.shutdown();
        }

        log.info("Best unmonitored: "+bestUnmonitored);
        log.info("Best monitored: "+bestMonitored);
        log.info("Performance ratio: " + 1.0 * bestMonitored / bestUnmonitored);
    }

    public long testMessages(final int numMsgs, final String durationStr) {

        // just a quick trick to extract a numeric result 
    	// from the JavaTestKit instance below
        final DurationCallback callback = new DurationCallback();
        
        new JavaTestKit(system) {{
            final FiniteDuration MAX_DURATION = duration(durationStr);

            // 1. Create an 'echo' actor that acts as a bent pipe.
            // 2. Create a tester actor that will hammer the echo actor
            //    and collect results.
            // 3. Evaluate results.
            
            ActorRef echo = system.actorOf(Props.create(EchoActor.class), "echo");
            final Props testerProps = SendReceiveActor.props(echo, numMsgs);
            final ActorRef tester = system.actorOf(testerProps, "tester");
            
            tester.tell("Start", getRef());
            expectMsgEquals(MAX_DURATION, "Done");
            Long duration = expectMsgClass(Long.class);

            if (callback != null) {
                callback.onDuration(duration);
            }
            
            StatsLogger.logAndClearStats();
            log.info("*** Echoing "+numMsgs+" messages took "+duration+" ms. ***");
            
            assertThat("Expected to take less than "+MAX_DURATION.toMillis()+
                    " but took "+duration+" ms.",
                    duration, lessThan(MAX_DURATION.toMillis()));
        }};
        
        return callback.getDuration();        
    }
    
    class DurationCallback {
        volatile long duration;
        public void onDuration(long duration) {
            this.duration = duration;
        }
        public long getDuration() {
            return duration;
        }
    }
}
