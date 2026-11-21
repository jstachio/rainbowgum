package rainbowgum.weblog.example;

import java.lang.System.Logger.Level;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

import io.jstach.rainbowgum.LogConfig;

/**
 * The most recent log entries, which anything can follow without touching an output.
 * {@link WebLogConfigurator} puts one in the {@link io.jstach.rainbowgum.ServiceRegistry}
 * and {@link WebLogOutput} pushes to it.
 * <p>
 * Logging only pushes: an application thread cannot be asked to slow down without
 * blocking it. So the feed never waits for a subscriber and never runs subscriber code
 * on the logging thread. Each subscription has a bounded queue, and a subscriber that
 * falls behind misses entries.
 */
public final class LogFeed implements AutoCloseable {

	/**
	 * Property for how many entries the feed keeps for new subscribers.
	 */
	public static final String SIZE_PROPERTY = "logging.feed.size";

	private static final int DEFAULT_SIZE = 500;

	/**
	 * One log event as encoded text. A copy: the event and the encoder's buffer are reused
	 * once the write returns.
	 * @param sequence increases by one per entry, so a subscriber can skip entries it has.
	 * @param level event level.
	 * @param text the encoder's output without the trailing line break.
	 */
	public record Entry(long sequence, Level level, String text) {
	}

	private final int size;

	private final ArrayDeque<Entry> recent;

	private final List<Subscription> subscriptions = new ArrayList<>();

	private final ReentrantLock lock = new ReentrantLock();

	private long sequence;

	private boolean closed;

	LogFeed(int size) {
		if (size <= 0) {
			throw new IllegalArgumentException(SIZE_PROPERTY + " must be positive: " + size);
		}
		this.size = size;
		this.recent = new ArrayDeque<>(size);
	}

	static LogFeed of(LogConfig config) {
		int size = config.properties().forKey(SIZE_PROPERTY).ofInt().or(DEFAULT_SIZE).validateNow(LogFeed.class);
		return new LogFeed(size);
	}

	/**
	 * Finds the feed registered with this configuration.
	 * @param config Rainbow Gum's configuration, for example
	 * <code>RainbowGum.of().config()</code>.
	 * @return the feed.
	 * @throws IllegalStateException if none is registered.
	 */
	public static LogFeed find(LogConfig config) {
		var feed = config.serviceRegistry().findOrNull(LogFeed.class);
		if (feed == null) {
			throw new IllegalStateException(
					"No LogFeed is registered. WebLogConfigurator registers one through the ServiceLoader.");
		}
		return feed;
	}

	void publish(Level level, String text) {
		lock.lock();
		try {
			if (closed) {
				return;
			}
			var entry = new Entry(++sequence, level, text);
			if (recent.size() == size) {
				recent.removeFirst();
			}
			recent.addLast(entry);
			for (var subscription : subscriptions) {
				subscription.queue.offer(entry);
			}
		}
		finally {
			lock.unlock();
		}
	}

	/**
	 * Starts following the feed. The entries kept so far and every later one reach the
	 * subscription exactly once, apart from those it misses by falling behind.
	 * @return subscription, to close when done.
	 */
	public Subscription subscribe() {
		lock.lock();
		try {
			var subscription = new Subscription(this, List.copyOf(recent), new ArrayBlockingQueue<>(size));
			if (closed) {
				subscription.closed = true;
			}
			else {
				subscriptions.add(subscription);
			}
			return subscription;
		}
		finally {
			lock.unlock();
		}
	}

	private void unsubscribe(Subscription subscription) {
		lock.lock();
		try {
			subscriptions.remove(subscription);
		}
		finally {
			lock.unlock();
		}
	}

	/**
	 * Ends every subscription. Called when Rainbow Gum closes its service registry.
	 */
	@Override
	public void close() {
		lock.lock();
		try {
			closed = true;
			for (var subscription : subscriptions) {
				subscription.closed = true;
			}
			subscriptions.clear();
		}
		finally {
			lock.unlock();
		}
	}

	/**
	 * One follower of the feed. Used by one thread at a time.
	 */
	public static final class Subscription implements AutoCloseable {

		private final LogFeed feed;

		private final List<Entry> backlog;

		private final BlockingQueue<Entry> queue;

		private volatile boolean closed;

		private Subscription(LogFeed feed, List<Entry> backlog, BlockingQueue<Entry> queue) {
			this.feed = feed;
			this.backlog = backlog;
			this.queue = queue;
		}

		/**
		 * The entries the feed held when this subscription started, oldest first.
		 * @return entries before the first one {@link #poll(Duration)} returns.
		 */
		public List<Entry> backlog() {
			return backlog;
		}

		/**
		 * Waits for the next entry.
		 * @param timeout how long to wait.
		 * @return the entry, or <code>null</code> if none arrived in time.
		 * @throws InterruptedException if interrupted while waiting.
		 */
		public Entry poll(Duration timeout) throws InterruptedException {
			return queue.poll(timeout.toNanos(), TimeUnit.NANOSECONDS);
		}

		/**
		 * Whether this subscription or its feed was closed.
		 * @return <code>true</code> once no more entries will arrive.
		 */
		public boolean isClosed() {
			return closed;
		}

		@Override
		public void close() {
			closed = true;
			feed.unsubscribe(this);
		}

	}

}
