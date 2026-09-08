package io.jstach.rainbowgum;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.jdt.annotation.Nullable;

import io.jstach.rainbowgum.LogProperties.FoundProperty;
import io.jstach.rainbowgum.LogProperty.Result;

public interface LogKeyed {

	public String key();
	public LogProperties properties();
	
	public Result<String> string();
	public Result<List<String>> list();
	public Result<Map<String,String>> keyValues();
	
	public static LogKeyed of(LogProperties properties, String key) {
		LogProperties.validateKeyParameters(key, Set.of());
		return new DefaultLogKeyed(key, properties);
	}
	
	public static LogKeyed of(LogProperties properties, String key, String nameParam) {
		return new DefaultLogKeyed(LogProperties.interpolateNamedKey(key, nameParam), properties);
	}
	
}
class DefaultLogKeyed implements LogKeyed {

	private final String key;
	private final LogProperties properties;
	
	DefaultLogKeyed(
			String key,
			LogProperties properties) {
		super();
		this.key = key;
		this.properties = properties;
	}

	@Override
	public String key() {
		return this.key;}

	@Override
	public LogProperties properties() {
		return this.properties;
	}

	@Override
	public Result<String> string() {
		var v = this.properties.stringPropertyOrNull(key);
		if (v == null) {
			return missingResult(this.properties, List.of(key));
		}
		return new Result.Success.PropertySuccess<>(v, v.value());
	}

	@Override
	public Result<List<String>> list() {
		FoundProperty.@Nullable ListProperty prop = properties.listPropertyOrNull(key);
		if (prop == null) {
			return missingResult(this.properties, List.of(key));
		}
		return new Result.Success.PropertySuccess<>(prop, prop.value());
	}


	@Override
	public Result<Map<String,String>> keyValues() {
		FoundProperty.@Nullable MapProperty prop = properties.mapPropertyOrNull(key);
		if (prop == null) {
			return missingResult(properties, List.of(key));
		}
		return new Result.Success.PropertySuccess<>(prop, prop.value());
	}
	
	<T> Result.Missing<T> missingResult(LogProperties props, List<String> keys) {
		List<String> resolvedKeys = describeKeys(props, keys);
		String message = "Property missing. keys: " + resolvedKeys;
		return new Result.Missing<>(keys, message);
	}

	List<String> describeKeys(LogProperties props, List<String> keys) {
		return keys.stream().<String>map(k -> {
			String fqk = fullyQualifiedKey(k);
			return "'" + fqk + "' from " + props.description(fullyQualifiedKey(k));
		}).toList();
	}
	
	String fullyQualifiedKey(String key) {
		return key;
	}
}
