package io.jstach.rainbowgum.pattern.format;

class ANSIConstants {

	public final static String ESC_START = "\033[";

	public final static String ESC_END = "m";

	public final static String BOLD = "1;";

	public final static String RESET = "0;";

	public final static String BLACK_FG = "30";

	public final static String RED_FG = "31";

	public final static String GREEN_FG = "32";

	public final static String YELLOW_FG = "33";

	public final static String BLUE_FG = "34";

	public final static String MAGENTA_FG = "35";

	public final static String CYAN_FG = "36";

	public final static String WHITE_FG = "37";

	public final static String DEFAULT_FG = "39";
	
	public final static String SET_DEFAULT_COLOR = ESC_START + RESET + DEFAULT_FG + ESC_END;

}
