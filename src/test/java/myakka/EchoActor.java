package myakka;

import akka.actor.UntypedActor;
import akka.event.Logging;
import akka.event.LoggingAdapter;

/**
 * EchoActor sends any received String messages back to the sender.
 */    
public class EchoActor extends UntypedActor {
    private LoggingAdapter log = Logging.getLogger(getContext().system(), this);

    @Override
    public void onReceive(Object msg) {
        //log.info("Received message: {}", msg);

        if (msg instanceof String) {
            onString((String) msg);
        } else {
            log.info("Unknown msg: "+msg);
        }
    }
    
    public void onString(String str) {
        getSender().tell(str, getSelf());
    }
}