package io.jstach.rainbowgum.slf4j;

import org.junit.jupiter.api.Test;

import io.jstach.rainbowgum.slf4j.CodeTemplates.ChangeLoggerModel;

class ChangeLoggerGenerateTest {

	@Test
	void test() {
		String actual = ChangeLoggerModelRenderer.of().execute(new ChangeLoggerModel());
		System.out.println(actual);
	}

}
