{{=$$ $$=}}
package $$packageName$$;

import io.jstach.rainbowgum.LogProperties;
import io.jstach.rainbowgum.LogProperties.Property;

public class $$builderName$$ {
		
	static final String PROPERTY_PREFIX = "$$propertyPrefix$$";
	
	$$#properties$$
	static final String $$propertyLiteral$$ = PROPERTY_PREFIX + "$$name$$";
	$$/properties$$
	
	$$#properties$$
	static final Property<$$type$$> $$propertyVar$$ = Property.builder()$$convertMethod$$.build($$propertyLiteral$$);
	$$/properties$$
	
	$$#properties$$
	private $$typeWithAnnotation$$ $$name$$ = $$defaultValue$$;
	$$/properties$$

	$$#properties$$
	public $$builderName$$ $$name$$($$typeWithAnnotation$$ $$name$$) {
		this.$$name$$ = $$name$$;
		return this;
	}
	$$/properties$$
	
	public $$targetType$$ build() {
		return $$factoryMethod$$(
				$$#properties$$
				$$^-first$$, $$/-first$$this.$$name$$
				$$/properties$$
				);
	}
	
	public $$builderName$$ fromProperties(LogProperties properties) {
		$$#properties$$
		this.$$name$$ = $$propertyVar$$.get(properties).$$valueMethod$$(this.$$name$$);
		$$/properties$$
		return this;
	}
}