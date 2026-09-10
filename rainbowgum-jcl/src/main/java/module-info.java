/**
 * Rainbow Gum Apache Commons Logging (JCL) implementation - a native
 * {@code org.apache.commons.logging.LogFactory}/{@code Log}, not a bridge through
 * {@code jcl-over-slf4j}/{@code spring-jcl}.
 * <p>
 * Commons Logging's {@code Log} interface is method-for-method identical to
 * {@code org.apache.juli.logging.Log} (Tomcat's own logging facade, confirmed by
 * decompiling both), so this module mirrors {@code rainbowgum-tomcat}'s implementation
 * shape directly: a non-changeable path with a per-level specialized {@code Log}
 * (avoiding a level check on every call, same rationale as
 * {@code io.jstach.rainbowgum.slf4j}'s {@code LevelLogger}, just without needing to be
 * mustache-generated given how much smaller this facade's surface is), and a
 * changeable path that re-resolves the level and re-fetches the sink fresh on every
 * call instead - deliberately not the more complex mutable-state-plus-subscription
 * approach {@code io.jstach.rainbowgum.slf4j}'s {@code ReplaceableLogger} uses, since
 * that complexity is only earned by SLF4J's much hotter, more heavily wrapped call
 * path.
 *
 * @provides org.apache.commons.logging.LogFactory
 */
module io.jstach.rainbowgum.jcl {

	requires io.jstach.rainbowgum;
	requires static org.apache.commons.logging;
	requires static org.eclipse.jdt.annotation;
	requires static io.jstach.svc;

	provides org.apache.commons.logging.LogFactory with io.jstach.rainbowgum.jcl.RainbowGumLogFactory;

}
