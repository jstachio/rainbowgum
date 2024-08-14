package io.jstach.rainbowgum.rabbitmq;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Function;

import org.eclipse.jdt.annotation.Nullable;

import com.rabbitmq.client.AMQP.BasicProperties;
import com.rabbitmq.client.AlreadyClosedException;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;

import io.jstach.rainbowgum.LogConfig;
import io.jstach.rainbowgum.LogEncoder.BufferHints;
import io.jstach.rainbowgum.LogEvent;
import io.jstach.rainbowgum.LogOutput;
import io.jstach.rainbowgum.LogProperties;
import io.jstach.rainbowgum.MetaLog;
import io.jstach.rainbowgum.annotation.GeneratedByATrustedSource;
import io.jstach.rainbowgum.annotation.LogConfigurable;

/**
 * RabbitMQ Output that will write publish messages to a given exchange with a given
 * routing key.
 */
public final class RabbitMQOutput implements LogOutput {

	private final URI uri;

	private final ConnectionFactory connectionFactory;

	private Connection connection;

	private volatile Channel channel;

	private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

	private final @Nullable String appId;

	private final String exchange;

	private final Function<LogEvent, String> routingKeyFunction;

	private final String connectionName;

	private final boolean declareExchange;

	private final String exchangeType;

	/**
	 * The rabbitmq URI scheme for configuration.
	 */
	public final static String URI_SCHEME = "amqp";

	/**
	 * Default exchange.
	 */
	public final static String DEFAULT_EXCHANGE = "logging";

	/**
	 * Default exchange type for declaration.
	 */
	public final static String DEFAULT_EXCHANGE_TYPE = "topic";

	RabbitMQOutput(URI uri, ConnectionFactory connectionFactory, @Nullable String appId, String exchange,
			Function<LogEvent, String> routingKeyFunction, String connectionName, boolean declareExchange,
			String exchangeType) {
		super();
		this.uri = uri;
		this.connectionFactory = connectionFactory;
		this.appId = appId;
		this.exchange = exchange;
		this.routingKeyFunction = routingKeyFunction;
		this.connectionName = connectionName;
		this.declareExchange = declareExchange;
		this.exchangeType = exchangeType;
	}

	/**
	 * Creates a RabbitMQOutput.
	 * @param name used to resolve config and give the output a name.
	 * @param uri passed to the rabbitmq connection factory.
	 * @param exchange exchange to send messages to.
	 * @param routingKey the logging event level will be used by default.
	 * @param declareExchange declare exchange on start. Default is false.
	 * @param host host.
	 * @param username set user name if not null outside of URI.
	 * @param password set password if not null outside of URI.
	 * @param port set port if not null.
	 * @param appId sets the message appId if not null.
	 * @param connectionName connection name if not null.
	 * @param exchangeType exchange type like "topic" covered in rabbitmq doc.
	 * @param virtualHost sets virtualhost if not null.
	 * @return output.
	 */
	@LogConfigurable(prefix = LogProperties.OUTPUT_PREFIX)
	static RabbitMQOutput of( //
			@LogConfigurable.KeyParameter String name, //
			@Nullable URI uri, //
			@LogConfigurable.DefaultParameter("DEFAULT_EXCHANGE") String exchange, //
			@LogConfigurable.ConvertParameter("toRoutingKeyFunction") @Nullable Function<LogEvent, String> routingKey, //
			@Nullable Boolean declareExchange, //
			@Nullable String host, //
			@Nullable String username, //
			@Nullable String password, //
			@Nullable Integer port, //
			@Nullable String appId, //
			@Nullable String connectionName, //
			@LogConfigurable.DefaultParameter("DEFAULT_EXCHANGE_TYPE") @Nullable String exchangeType, //
			@Nullable String virtualHost) {
		connectionName = connectionName == null ? "rainbowgumOutput" : connectionName;
		declareExchange = declareExchange == null ? false : declareExchange;
		exchangeType = exchangeType == null ? DEFAULT_EXCHANGE_TYPE : exchangeType;
		ConnectionFactory factory = new ConnectionFactory();
		if (uri != null) {
			try {
				factory.setUri(uri);
			}
			catch (Exception e) {
				throw new RuntimeException(e);
			}
		}
		if (username != null) {
			factory.setUsername(username);
		}
		if (password != null) {
			factory.setPassword(password);
		}
		if (port != null) {
			factory.setPort(port);
		}
		if (host != null) {
			factory.setHost(host);
		}
		if (virtualHost != null) {
			factory.setVirtualHost(virtualHost);
		}
		Function<LogEvent, String> routingKeyFunction;
		if (routingKey != null) {
			routingKeyFunction = routingKey;
		}
		else {
			routingKeyFunction = e -> e.level().name();
		}
		return new RabbitMQOutput(uri, factory, appId, exchange, routingKeyFunction, connectionName, declareExchange,
				exchangeType);
	}

	static Function<LogEvent, String> toRoutingKeyFunction(String routingKey) {
		return e -> routingKey;
	}

	@Override
	public void start(LogConfig config) {
		lock.writeLock().lock();
		try {
			this.connection = connectionFactory.newConnection(connectionName);
			if (declareExchange) {
				var channel = this.connection.createChannel();
				channel.exchangeDeclare(exchange, exchangeType);
			}
		}
		catch (IOException e) {
			throw new UncheckedIOException(e);
		}
		catch (TimeoutException e) {
			throw new RuntimeException(e);
		}
		finally {
			lock.writeLock().unlock();
		}
	}

	@Override
	public URI uri() {
		return this.uri;
	}

	@Override
	public void write(LogEvent event, byte[] bytes, int off, int len, ContentType contentType) {
		// https://github.com/rabbitmq/rabbitmq-java-client/issues/422
		byte[] copy = new byte[len];
		System.arraycopy(bytes, off, copy, 0, len);
		write(event, bytes, contentType);
	}

	@Override
	public void write(LogEvent event, byte[] bytes, ContentType contentType) {
		if (checkReentry(event)) {
			return;
		}
		BasicProperties props = properties(event, contentType);
		byte[] body = bytes;
		try {
			var c = channel();
			c.basicPublish(exchange, routingKeyFunction.apply(event), props, body);
		}
		catch (IOException e) {
			MetaLog.error(RabbitMQOutput.class, e);
			lock.writeLock().lock();
			try {
				this.channel = null;
			}
			finally {
				lock.writeLock().unlock();
			}
		}
	}

	// This is to exclude this code from code coverage as it is not possible with current
	// RabbitMQ client.
	@GeneratedByATrustedSource
	// TODO make this generic and add to MetaLog.
	private static boolean checkReentry(LogEvent event) {
		if (event.loggerName().startsWith("com.rabbitmq.client")) {
			StringBuilder sb = new StringBuilder();
			event.formattedMessage(sb);
			String docUrl = MetaLog.documentBaseUrl() + "/#appender_reentry";
			MetaLog.error(RabbitMQOutput.class, "RabbitMQ attempted to recursively log. File a bug. See: " + docUrl,
					new Exception(sb.toString()));
			return true;
		}
		return false;
	}

	private BasicProperties properties(LogEvent event, ContentType contentType) {
		var builder = new BasicProperties.Builder().contentType(contentType.contentType()).appId(appId);
		var kvs = event.keyValues();
		if (!kvs.isEmpty()) {
			Map<String, Object> headers = new LinkedHashMap<>(kvs.size());
			kvs.forEach(headers::put);
			builder.headers(headers);
		}
		if (appId != null) {
			builder.appId(appId);
		}
		return builder.build();
	}

	Channel channel() throws IOException {
		var c = this.channel;
		if (c == null) {
			lock.writeLock().lock();
			try {
				c = this.channel = connection.createChannel();
				if (c == null) {
					throw new IOException("channel is unavailable");
				}
				return c;
			}
			finally {
				lock.writeLock().unlock();
			}
		}
		return c;
	}

	@Override
	public void flush() {

	}

	@Override
	public BufferHints bufferHints() {
		return WriteMethod.BYTES;
	}

	@Override
	public OutputType type() {
		return OutputType.NETWORK;
	}

	@Override
	public void close() {
		lock.writeLock().lock();
		try {
			var c = this.channel;
			var conn = this.connection;
			if (c != null) {
				try {
					c.close();
				}
				catch (AlreadyClosedException ae) {
					// do nothing.
				}
				catch (IOException | TimeoutException e) {
					MetaLog.error(getClass(), e);
				}
			}
			if (conn != null) {
				try {
					conn.close();
				}
				catch (AlreadyClosedException ae) {
					// do nothing.
				}
				catch (IOException e) {
					MetaLog.error(getClass(), e);
				}
			}
			this.channel = null;
			this.connection = null;
		}
		finally {
			lock.writeLock().unlock();
		}

	}

}
