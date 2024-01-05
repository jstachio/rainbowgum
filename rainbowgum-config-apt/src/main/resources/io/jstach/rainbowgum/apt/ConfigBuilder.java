{{=$$ $$=}}
package $$packageName$$;

import io.jstach.rainbowgum.LogProperties;
import io.jstach.rainbowgum.LogProperties.Property;

/**
 * Builder to create $$targetType$$.
 * $$description$$
 * <table class="table">
 * <caption>Properties retrieved from LogProperties</caption>
 * <tr>
 * <th>Property Pattern</th>
 * <th>Type</th>
 * <th>Required</th>
 * <th>Default</th>
 * <th>Description</th>
 * </tr>
 $$#properties$$
 $$#normal$$
 * <tr>
 * <td><code>{@value $$propertyLiteral$$ }</code></td>
 * <td><code>$$type$$</code></td>
 * <td><code>$$required$$</code></td>
 * <td><code>$$defaultValue$$</code></td>
 * <td>$$javadoc$$</td>
 * </tr>
 $$/normal$$ 
 $$/properties$$
 * </table>
 */
public final class $$builderName$$ {

	/**
	 * The properties to be retrieved from config will have
	 * this prefix.
	 */
	static final String PROPERTY_PREFIX = "$$propertyPrefix$$";
	
	$$#properties$$
	$$#normal$$
	/**
	 * <code>{@value #$$propertyLiteral$$ } = $$type$$</code> $$javadoc$$
	 */
	static final String $$propertyLiteral$$ = PROPERTY_PREFIX + "$$name$$";
	$$/normal$$
	$$/properties$$
		
	$$#properties$$
	$$#normal$$
	final Property<$$type$$> $$propertyVar$$;
	$$/normal$$
	$$/properties$$
	
	$$#properties$$
	$$#normal$$
	private $$typeWithAnnotation$$ $$name$$ = $$defaultValue$$;
	$$/normal$$
	$$#prefixParameter$$
	private final $$typeWithAnnotation$$ $$name$$;
	$$/prefixParameter$$
	$$/properties$$
	
	/**
	 * Create a builder for $$targetType$$.
	 $$#prefixParameters$$
	 * @param $$name$$ will fill <code>$$LB$$$$name$$$$RB$$</code> in <code>$$propertyPrefix$$</code>. $$javadoc$$
	 $$/prefixParameters$$
	 */
	public $$builderName$$(
			$$#prefixParameters$$
			$$^-first$$, $$/-first$$$$type$$ $$name$$
			$$/prefixParameters$$
			) {
		java.util.Map<String,String> prefixParameters = java.util.Map.of(
				$$#prefixParameters$$
				$$^-first$$, $$/-first$$"$$name$$", $$name$$
				$$/prefixParameters$$
			);
		$$#properties$$
		$$#normal$$
		$$propertyVar$$ = Property.builder()
			$$convertMethod$$.build(LogProperties.interpolateKey($$propertyLiteral$$, prefixParameters));
		$$/normal$$
		$$#prefixParameter$$
		this.$$name$$ = $$name$$;
		$$/prefixParameter$$
		$$/properties$$
	}

	$$#properties$$
	$$#normal$$
	
	/**
	 * Sets $$#required$$<strong>required</strong> $$/required$$$$name$$.
	 * @param $$name$$ <code>{@value #$$propertyLiteral$$ } = $$type$$</code> $$javadoc$$
	 * @return this builder.
	 */
	public $$builderName$$ $$name$$($$typeWithAnnotation$$ $$name$$) {
		this.$$name$$ = $$name$$;
		return this;
	}
	$$/normal$$
	$$/properties$$
	
	/**
	 * Creates $$targetType$$ from this builder.
	 * @return $$targetType$$.
	 */
	public $$targetType$$ build() {
		return $$factoryMethod$$(
				$$#properties$$
				$$^-first$$, $$/-first$$this.$$name$$
				$$/properties$$
				);
	}
	
	/**
	 * Fills the builder from properties.
	 * @param properties not null.
	 * @return this updated builder.
	 */
	public $$builderName$$ fromProperties(LogProperties properties) {
		$$#properties$$
		$$#normal$$
		this.$$name$$ = $$propertyVar$$.get(properties).$$valueMethod$$(this.$$name$$);
		$$/normal$$
		$$/properties$$
		return this;
	}
}