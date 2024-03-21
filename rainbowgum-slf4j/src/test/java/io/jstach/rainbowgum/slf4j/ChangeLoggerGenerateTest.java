package io.jstach.rainbowgum.slf4j;

import org.junit.jupiter.api.Test;

import io.jstach.rainbowgum.slf4j.CodeTemplates.ChangeLoggerModel;
import io.jstach.rainbowgum.slf4j.CodeTemplates.ForwardLoggerModel;

/*
 * Run the tests to generate code
 * and copy and paste.
 */
class ChangeLoggerGenerateTest {

	@Test
	void test() {
		String actual = ChangeLoggerModelRenderer.of().execute(new ChangeLoggerModel());
		System.out.println(actual);
	}

	@Test
	void generateForwardLogger() throws Exception {
		String actual = ForwardLoggerModelRenderer.of().execute(new ForwardLoggerModel());
		System.out.println(actual);
	}

}
