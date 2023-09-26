package io.jstach.rainbowgum;

import java.lang.System.Logger.Level;

import org.eclipse.jdt.annotation.Nullable;

public interface LevelAware {

	@Nullable Level levelOrNull(String name);

}
