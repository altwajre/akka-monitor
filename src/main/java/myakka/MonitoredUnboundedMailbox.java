package myakka;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.ActorSystem.Settings;
import akka.dispatch.MailboxType;
import akka.dispatch.ProducesMessageQueue;

import com.typesafe.config.Config;

import scala.Option;

/**
 * This implementation of an Akka Mailbox closely follows the implementation
 * of the default UnboundedMailbox, but adds on additional statistics 
 * collection capabilities.
 * 
 * <p>To use this Mailbox implementation globally, set the following ActorSystem
 * Props setting:
 * 
 * <pre><code>akka.actor.default-mailbox.mailbox-type = myakka.MonitoredUnboundedMailbox</code></pre>
 * 
 * <p>See {@link myakka.MonitoredQueue} for the actual queue implementation.
 */
public class MonitoredUnboundedMailbox implements MailboxType,
	ProducesMessageQueue<MonitoredQueue> {
	
    /* constructors (required) */
	public MonitoredUnboundedMailbox(Settings settings, Config config) {}
	public MonitoredUnboundedMailbox() {}
	
	/**
	 * Creates and returns a monitored queue object. This is the main factory 
	 * method. See {@link myakka.MonitoredQueue}.
	 */
	public MonitoredQueue create(Option<ActorRef> owner, 
	        Option<ActorSystem> system) {
		return new MonitoredQueue(refToPrefix(owner.get()));
	}

	/**
	 * This helper method returns a dot-separated variant of the actor path.
	 * 
	 * <p>In reality, it would be much better to prune the instanceId and 
	 * instead just capture the SimpleName of the underlying actor class. 
	 * 
	 * <p>Why? Because JAMon (used for stats collection) creates a record in 
	 * memory for each unique label. Akka generates actors freely and easily, 
	 * so memory usage can get out of hand quickly.
	 * 
	 * @param ref The ActorRef to process into a usable prefix for stats 
	 *            collection.
	 * @return A dot-separated actor path, or 'unknown' if ref is null.
	 */
	private String refToPrefix(ActorRef ref) {
		if (ref == null) 
			return "unknown";
		String prefix = ref.path().toStringWithoutAddress();
		// '.' is more log4j-friendly than '/'
		prefix = prefix.replace("/", "."); 
		// strip leading '.', if present
		return prefix.startsWith(".") ? prefix.substring(1) :  prefix; 
	}
}
