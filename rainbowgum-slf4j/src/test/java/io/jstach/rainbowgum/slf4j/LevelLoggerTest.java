package io.jstach.rainbowgum.slf4j;

import org.junit.jupiter.api.Test;

import io.jstach.rainbowgum.slf4j.CodeTemplates.ClassModel;

class LevelLoggerTest {

	@Test
	void test() {
		String actual = ClassModelRenderer.of().execute(new ClassModel());
		System.out.println(actual);
	}

}
