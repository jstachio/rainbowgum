package snippets;

import java.util.List;

import io.jstach.rainbowgum.LogResponse;
import io.jstach.rainbowgum.RainbowGum;

public class RollingExample {

	// @start region = "rollingExample"
	/*
	 * This method could be bound to an internal HTTP route say /log/rotate such that curl
	 * http://localhost:8080/log/rotate will block and wait till all the applicable
	 * outputs have reopened.
	 */
	@RequestMapping("/log/rotate")
	@ResponseBody
	public String someInternalHttpRequestHandler() {
		var gum = RainbowGum.getOrNull();
		if (gum != null) {
			List<LogResponse> response = gum.config() //
				.outputRegistry() //
				.reopen(); // Here is where we siginal to reopen outputs that support
							// reopening.
			return response.toString();
		}
		return "";
	}
	// @end

	public @interface RequestMapping {

		String value();

	}

	public @interface ResponseBody {

	}

}
