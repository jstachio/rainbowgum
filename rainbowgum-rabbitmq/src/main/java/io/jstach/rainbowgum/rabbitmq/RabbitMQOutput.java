package io.jstach.rainbowgum.rabbitmq;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import org.eclipse.jdt.annotation.Nullable;

import com.rabbitmq.client.AMQP.BasicProperties;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;

import io.jstach.rainbowgum.ConfigObject;
import io.jstach.rainbowgum.LogConfig;
import io.jstach.rainbowgum.LogEvent;
import io.jstach.rainbowgum.LogOutput;
import io.jstach.rainbowgum.LogProperties;
import io.jstach.rainbowgum.MetaLog;

class RabbitMQOutput implements LogOutput {

	private final URI uri;

	private final ConnectionFactory connectionFactory;

	private Connection connection;

	private volatile Channel channel;

	private ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

	private final @Nullable String appId;

	private final String exchange;

	private final String routingKey;

	private final String connectionName;

	private final boolean declareExchange;

	private final String exchangeType;

	private static final String PREFIX = LogProperties.ROOT_PREFIX + "rabbitmq.";

	public static final String EXCHANGE_PROPERTY = PREFIX + "exchange";

	static final String ROUTING_KEY_PROPERTY = PREFIX + "routingKey";

	static final String CONNECTION_NAME_PROPERTY = PREFIX + "connectionName";

	static final String DECLARE_EXCHANGE_PROPERTY = PREFIX + "declareExchange";

	static final String EXCHANGE_TYPE_PROPERTY = PREFIX + "exchangeType";

	static final String USERNAME_PROPERTY = PREFIX + "username";

	static final String PASSWORD_PROPERTY = PREFIX + "password";

	static final String PORT_PROPERTY = PREFIX + "port";

	static final String HOST_PROPERTY = PREFIX + "host";

	static final String VIRTUAL_HOST_PROPERTY = PREFIX + "virtualHost";

	static final String APP_ID_PROPERTY = PREFIX + "appId";

	// public static RabbitMQOutput of(URI uri, LogProperties properties) {
	// LogProperties combined = LogProperties.of(List.of(LogProperties.of(uri)),
	// properties);
	// Property.builder().build(ROUTING_KEY_PROPERTY);
	//
	// }

	public RabbitMQOutput(URI uri, ConnectionFactory connectionFactory, @Nullable String appId, String exchange,
			String routingKey, String connectionName, boolean declareExchange, String exchangeType) {
		super();
		this.uri = uri;
		this.connectionFactory = connectionFactory;
		this.appId = appId;
		this.exchange = exchange;
		this.routingKey = routingKey;
		this.connectionName = connectionName;
		this.declareExchange = declareExchange;
		this.exchangeType = exchangeType;
	}

	@ConfigObject(prefix = LogProperties.OUTPUT_PREFIX, name = "RabbitMQOutputBuilder")
	public static RabbitMQOutput of(@ConfigObject.PrefixParameter String name, //
			@Nullable URI uri, //
			String exchange, //
			String routingKey, //
			@Nullable Boolean declareExchange, //
			@Nullable String host, //
			@Nullable String username, @Nullable String password, //
			@Nullable Integer port, //
			@Nullable String appId, //
			@Nullable String connectionName, //
			@Nullable String exchangeType, //
			@Nullable String virtualHost) throws KeyManagementException, NoSuchAlgorithmException, URISyntaxException {
		connectionName = connectionName == null ? "rainbowgumOutput" : connectionName;
		declareExchange = declareExchange == null ? false : declareExchange;
		exchangeType = exchangeType == null ? "topic" : exchangeType;
		ConnectionFactory factory = new ConnectionFactory();
		factory.setUri(uri);
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
		return new RabbitMQOutput(uri, factory, appId, exchange, routingKey, connectionName, false, exchangeType);
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
		BasicProperties props = properties(event, contentType);
		byte[] body = bytes;
		try {
			var c = channel();
			c.basicPublish(exchange, routingKey, props, body);
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
	public WriteMethod writeMethod() {
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
				catch (IOException | TimeoutException e) {
					MetaLog.error(getClass(), e);
				}
			}
			if (conn != null) {
				try {
					c.close();
				}
				catch (IOException | TimeoutException e) {
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
