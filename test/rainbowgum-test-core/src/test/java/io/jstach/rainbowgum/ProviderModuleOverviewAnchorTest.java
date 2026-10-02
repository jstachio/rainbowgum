package io.jstach.rainbowgum;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;

/*
 * Overview section ids get renamed and moved as the doc is reorganized; this catches a
 * ProviderModule doc link pointing at a section that no longer exists.
 */
class ProviderModuleOverviewAnchorTest {

	@Test
	void everyOverviewAnchorExistsInOverview() throws IOException {
		String overview = Files.readString(Path.of("../../doc/overview.html"));
		List<String> missing = new java.util.ArrayList<>();
		for (var m : ProviderModule.values()) {
			String url = m.docUrl();
			int hash = url.indexOf('#');
			if (hash < 0) {
				continue;
			}
			String anchor = url.substring(hash + 1);
			if (!overview.contains("id=\"" + anchor + "\"")) {
				missing.add(m + "#" + anchor);
			}
		}
		assertEquals(List.of(), missing);
	}

}
