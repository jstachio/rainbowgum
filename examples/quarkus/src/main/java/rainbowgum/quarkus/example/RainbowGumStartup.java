package rainbowgum.quarkus.example;

import io.quarkus.runtime.StartupEvent;
import io.jstach.rainbowgum.RainbowGum;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;

/**
 * Bootstraps Rainbow Gum so its rainbowgum-jul bridge Handler actually gets installed
 * (see examples/helidon's README: rainbowgum-jul does nothing until something
 * bootstraps Rainbow Gum - same gap, same fix, applies here too).
 */
@ApplicationScoped
public class RainbowGumStartup {

    void onStart(@Observes StartupEvent ev) {
        RainbowGum.of();
    }

}
