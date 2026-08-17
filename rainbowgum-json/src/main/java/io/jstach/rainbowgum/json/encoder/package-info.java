/**
 * Common JSON encoders like GELF, ECS, and Logstash's format.
 * <p>
 * The Service Loaded configurators add:
 * <ul>
 * <li><a href="https://go2docs.graylog.org/5-2/getting_in_log_data/gelf.html">GELF
 * JSON</a> Encoder to encoder registry with {@value GelfEncoder#GELF_SCHEME} URI
 * scheme.</li>
 * <li><a href=
 * "https://www.elastic.co/guide/en/ecs-logging/java/current/index.html">Elastic Common
 * Schema (ECS)</a> Encoder to encoder registry with {@value EcsEncoder#ECS_SCHEME} URI
 * scheme.</li>
 * <li><a href=
 * "https://github.com/logfellow/logstash-logback-encoder">logstash-logback-encoder</a>
 * style Encoder to encoder registry with {@value LogstashEncoder#LOGSTASH_SCHEME} URI
 * scheme.</li>
 * </ul>
 */
@org.eclipse.jdt.annotation.NonNullByDefault
package io.jstach.rainbowgum.json.encoder;