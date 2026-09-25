package rainbowgum.helidon.example;

import java.util.logging.Level;
import java.util.logging.Logger;

import io.helidon.logging.common.LogConfig;
import io.helidon.webserver.WebServer;
import io.helidon.webserver.http.HttpRouting;

public class Main {

    private static final Logger LOGGER = Logger.getLogger(Main.class.getName());

    public static void main(String[] args) {
        LogConfig.configureRuntime();
        // no explicit RainbowGum.of() call here - see README.md's rainbowgum-jdk finding
        WebServer server = WebServer.builder()
                .routing(Main::routing)
                .port(8080)
                .build()
                .start();
        LOGGER.info("Server started at http://localhost:" + server.port());
    }

    static void routing(HttpRouting.Builder routing) {
        routing.get("/hello", (req, res) -> {
            LOGGER.info("handling /hello");
            LOGGER.fine("this is a fine (debug-ish) message, should be filtered by default level");
            try {
                throw new RuntimeException("boom");
            } catch (RuntimeException e) {
                LOGGER.log(Level.SEVERE, "something went wrong", e);
            }
            res.send("Hello, Rainbow Gum!");
        });
    }

}
