package io.jstach.rainbowgum.rabbitmq;

import java.net.URI;

import com.rabbitmq.client.MessageProperties;

class RabbitMQBuilder {

	/**
	 * Name of the exchange to publish log events to.
	 */
	private String exchangeName = "logs"; // done

	/**
	 * Type of the exchange to publish log events to.
	 */
	private String exchangeType = "topic";

	/**
	 * Configuration arbitrary application ID.
	 */
	private String applicationId = null; // done

	/**
	 * A name for the connection (appears on the RabbitMQ Admin UI).
	 */
	private String connectionName; // done

	/**
	 * Additional client connection properties added to the rabbit connection, with the
	 * form {@code key:value[,key:value]...}.
	 */
	private String clientConnectionProperties;

	/**
	 * A comma-delimited list of broker addresses: host:port[,host:port]*
	 *
	 * @since 1.5.6
	 */
	private String addresses;

	/**
	 * RabbitMQ host to connect to.
	 */
	private URI uri;

	/**
	 * RabbitMQ host to connect to.
	 */
	private String host;

	/**
	 * RabbitMQ virtual host to connect to.
	 */
	private String virtualHost;

	/**
	 * RabbitMQ port to connect to.
	 */
	private Integer port;

	/**
	 * RabbitMQ user to connect as.
	 */
	private String username;

	/**
	 * RabbitMQ password for this user.
	 */
	private String password;

	/**
	 * Use an SSL connection.
	 */
	private boolean useSsl;

	/**
	 * The SSL algorithm to use.
	 */
	private String sslAlgorithm;

	/**
	 * Location of resource containing keystore and truststore information.
	 */
	private String sslPropertiesLocation;

	/**
	 * Keystore location.
	 */
	private String keyStore;

	/**
	 * Keystore passphrase.
	 */
	private String keyStorePassphrase;

	/**
	 * Keystore type.
	 */
	private String keyStoreType = "JKS";

	/**
	 * Truststore location.
	 */
	private String trustStore;

	/**
	 * Truststore passphrase.
	 */
	private String trustStorePassphrase;

	/**
	 * Truststore type.
	 */
	private String trustStoreType = "JKS";

	/**
	 * SaslConfig.
	 * @see RabbitUtils#stringToSaslConfig(String, ConnectionFactory)
	 */
	private String saslConfig;

	private boolean verifyHostname = true;

	/**
	 * Default content-type of log messages.
	 */
	private String contentType = "text/plain";

	/**
	 * Default content-encoding of log messages.
	 */
	private String contentEncoding = null;

	/**
	 * Whether or not to try and declare the configured exchange when this appender
	 * starts.
	 */
	private boolean declareExchange = false;

	/**
	 * charset to use when converting String to byte[], default null (system default
	 * charset used). If the charset is unsupported on the current platform, we fall back
	 * to using the system charset.
	 */
	private String charset;

	/**
	 * Whether or not add MDC properties into message headers. true by default for
	 * backward compatibility
	 */
	private boolean addMdcAsHeaders = true;

	private boolean durable = true;

	// private MessageDeliveryMode deliveryMode = MessageDeliveryMode.PERSISTENT;

	private boolean autoDelete = false;

	/**
	 * Used to determine whether {@link MessageProperties#setMessageId(String)} is set.
	 */
	private boolean generateId = false;

	private boolean includeCallerData;

}