package rainbowgum.quarkus.example;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import org.jboss.logging.Logger;
import org.jboss.logging.MDC;

@Path("/hello")
public class GreetingResource {

    private static final Logger log = Logger.getLogger(GreetingResource.class);

    @GET
    @Produces(MediaType.TEXT_PLAIN)
    public String hello() {
        MDC.put("requestId", "abc-123");
        try {
            log.info("handling /hello");
            log.debug("this is a debug message, should be filtered by default level");
            try {
                throw new RuntimeException("boom");
            } catch (RuntimeException e) {
                log.error("something went wrong", e);
            }
            return "Hello from Quarkus REST";
        } finally {
            MDC.clear();
        }
    }
}
