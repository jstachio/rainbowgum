package rainbowgum.micronaut.example;

import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

@Controller("/hello")
public class HelloController {

    private static final Logger log = LoggerFactory.getLogger(HelloController.class);

    @Get
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
            return "Hello, Rainbow Gum!";
        } finally {
            MDC.clear();
        }
    }

}
