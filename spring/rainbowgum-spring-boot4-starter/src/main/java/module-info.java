/**
 * Spring Boot 4 Rainbow Gum Integration.
 * Please refer to the overview for installation.
 * <p>
 * See {@code io.jstach.rainbowgum.spring.boot4}'s module-info for why the module name is
 * suffixed with the Spring Boot major version while the packages are not.
 */
@io.jstach.rainbowgum.annotation.MavenModule(groupId = "io.jstach.rainbowgum", artifactId = "rainbowgum-spring-boot4-starter")
module io.jstach.rainbowgum.spring.boot4.starter {
	requires static io.jstach.rainbowgum.annotation;
	requires static org.jspecify;
}
