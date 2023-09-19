package io.jstach.rainbowgum.jansi;

import org.fusesource.jansi.Ansi.Attribute;
import org.fusesource.jansi.Ansi.Color;

@SuppressWarnings("exports")
/*
 * TODO
 */
public interface AnsiWriter {

	public void append(StringBuilder buf, String s);

	public void fg(StringBuilder buf, Color color);

	public void bg(StringBuilder buf, Color color);

	public void fgBright(StringBuilder buf, Color color);

	public void bgBright(StringBuilder buf, Color color);

	public void fg(StringBuilder buf, int color);

	public void fgRgb(StringBuilder buf, int r, int g, int b);

	public void bg(StringBuilder buf, int color);

	public void bgRgb(StringBuilder buf, int r, int g, int b);

	public void a(StringBuilder buf, Attribute attribute);

	public void reset(StringBuilder b);

}
