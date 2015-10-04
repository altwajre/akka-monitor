# MonitoredQueues in Akka

For performance reasons, Akka's default unbounded queue implementation doesn't include any metrics collection. 

Sometimes it's nice to trade a little performance for more information. So, in this package I put together a simple implementation of an Akka mailbox/message queue that logs a few statistics to an in-memory statistics aggregation tool named JAMon. It can also write stats directly to log, when active tracing is necessary/helpful.

To globally enable monitored (unbounded) queues, in application.conf we specify:

```
akka.actor.default-mailbox.mailbox-type = "myakka.MonitoredUnboundedMailbox"
```

The implementation of the monitoring queue is pretty straightforward - it utilizes a ConcurrentLinkedQueue (the default in Akka) plus an AtomicInteger to enable tracking/logging queue depth as an O(1) operation. Additionally, objects inserted into the queue are wrapped with a timestamp-carrying object so that the time spent in queue can also be logged/collected.

This implementation (with all the extra data collection and logging) is several times slower than the default queue, which is to be expected. But importantly, it retains O(1) performance relative to queue depth.

I also included a hastily written utility named StatsLogger (and StatsLoggerScheduler) to purge JAMon stats to logfile.

TODOs if you wanted to include this in production:

* Save precious RAM by using a different technique for mapping actor instances to their corresponding JAMon Monitor labels. It may be ideal to collate stats for all actors of the same type log under the same Monitor (see MonitoredUnboundedMailbox implementation).
* See if there is already a hardened UnboundedQueue implementation with an O(1) size() calculation that can be swapped in for the ConcurrentLinkedQueue + AtomicInteger implementation.
* Write a different StatsLogger - one that logs to an external data store that is equiped to deal with time series data (e.g. maybe to a scalable relational/olap db, or some sort of map-reduce cluster, etc.)
* Modify/replace StatsLoggerScheduler to be more robust (e.g. support start/stop) and also to be more cron-like.
* Maybe only enable this Mailbox on specific actors you wish to track (i.e. not globally.) For example, it's probably not ideal that this mailbox is also enabled for the system's logger actor.

Another nice-to-have would be to log instances of each arriving message type inside MonitoredQueue. This can be done by adding a monitor per message type, like so:

```java
  String msg = env.message();
  String msgType = msg != null ? msg.getClass().getSimpleName() : "null";
  logStat(prefix+"."+msgType, "count", 1);
```

I leave that as an easy exercise for the reader.

Enjoy!
