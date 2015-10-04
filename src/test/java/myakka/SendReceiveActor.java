package myakka;

import akka.actor.*;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import akka.japi.Creator;

/**
 * This actor generates 'numMessages' String-based messages, and sends
 * them to the provided actorRef. It also counts up the number of 
 * String-based responses. Stats are collected and returned.
 */
public class SendReceiveActor extends UntypedActor {
    private LoggingAdapter log = Logging.getLogger(getContext().system(), this);

    final ActorRef target;
    final int numMessages;
    
    long startTimeMillis; // time the first message was sent 
    long endTimeMillis;   // time the last message was received
    int numReceived;      // number of messages sent

    /* the entity watching this test */
    ActorRef supervisor;
    
    final static String MSG_PREFIX = "This is message ";
            
    public SendReceiveActor(ActorRef target, int numMessages) {
        this.target = target;
        this.numMessages = numMessages;
    }
        
    @Override
    public void onReceive(Object msg) {
        if (msg instanceof String) {
            onString((String) msg);
        } else {
            log.info("Unknown msg: "+msg);
        }
    }
    
    public void onString(String str) {
        if (str.startsWith(MSG_PREFIX)) {
            numReceived++;
            if (numReceived == numMessages) {
                endTimeMillis = System.currentTimeMillis();
                done();
            }
        } else if (str.equals("Start")) {
            supervisor = getSender();
            numReceived = 0;
            startTimeMillis = System.currentTimeMillis();
            
            // shoot off numMessages to target
            for (int i = 0; i < numMessages; i++) {
                target.tell(MSG_PREFIX+i, getSelf());
            }            
        }
    }
    
    private void done() {
        long deltaTime = endTimeMillis - startTimeMillis;
        log.info("Sent and received "+numMessages+" messages in "+
                deltaTime+" ms.");
        supervisor.tell("Done", getSelf());
        supervisor.tell(Long.valueOf(deltaTime), getSelf());
    }
    
    /**
     * This method (and supporting class) is used to generate instances of 
     * SendReceiveActor.
     */
    public static Props props(final ActorRef target, final int numMessages) {            
        return Props.create(new SRACreator(target, numMessages));
    }
    
    static class SRACreator implements Creator<SendReceiveActor> {
        private static final long serialVersionUID = 1L;
        private final ActorRef target;
        private final int numMessages;
        
        SRACreator(ActorRef target, int numMessages) {
            this.target = target;
            this.numMessages = numMessages;
        }
        public SendReceiveActor create() throws Exception {
            return new SendReceiveActor(target, numMessages);
        }        
    }
}