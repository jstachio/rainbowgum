package io.jstach.rainbowgum;

import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

/*
 * Exploratory spike only - not a golden-string test, prints candidate formats to stdout
 * for side-by-side comparison, nothing here is asserted or shipping. Reuses
 * ConfigFailureTest's encoderMalformedIntProperty scenario (FakeEncoderConfigurator/
 * FakeEncoderBuilder) purely because it is already a clean 4-layer nested failure with a
 * golden string pinned elsewhere, so the "current" rendering below is directly
 * comparable to that pinned text.
 *
 * The real Throwable#getCause() chain is fully intact end to end (LogProvider#wrap's
 * ProvisionException, LogProperty's PropertyConvertException/ValidationException all call
 * super(message, cause)) even though each layer's getMessage() already has the next
 * layer's full text baked in via string concatenation at construction time. That baked-in
 * concatenation is exactly what unbake() below undoes: for any layer whose message ends
 * with its cause's message verbatim, the "own" contribution is just that suffix
 * subtracted off. The one layer where that pattern breaks - a ValidationException whose
 * message is LogProperty's own synthesized description of the root property failure, not
 * a mechanical concatenation of its raw cause's getMessage() - is exactly where "root
 * cause" should stop for display purposes anyway, since that synthesized text is already
 * the most specific human-readable description available.
 */
class RootCauseFirstErrorFormatDemo {

	@Test
	void printCandidateFormats() {
		var properties = LogProperties.builder().fromProperties("""
				logging.appenders=myapp
				logging.appender.myapp.output=list:///
				logging.appender.myapp.encoder=fake:///
				logging.encoder.myapp.host=h
				logging.encoder.myapp.port=notanumber
				""").build();
		var config = LogConfig.builder().properties(properties).configurator(new FakeEncoderConfigurator()).build();
		var e = assertThrows(RuntimeException.class, () -> RainbowGum.builder(config).build().start());

		/*
		 * layers[0] is the root/innermost text block; layers[last] is the outermost
		 * wrapping context - i.e. already reversed relative to getCause() walk order.
		 */
		List<String> layers = unbake(e);

		System.out.println("========== CURRENT (top-to-bottom) ==========");
		System.out.println(e.getMessage());

		System.out.println();
		System.out.println("========== CANDIDATE A: numbered, root cause first ==========");
		System.out.println(candidateA(layers));

		System.out.println();
		System.out.println("========== CANDIDATE B: paragraph \"...while:\" trail ==========");
		System.out.println(candidateB(layers));

		System.out.println();
		System.out.println("========== CANDIDATE C: compact arrow trail ==========");
		System.out.println(candidateC(layers));
	}

	private static List<String> unbake(Throwable e) {
		List<String> layers = new ArrayList<>();
		Throwable cur = e;
		while (true) {
			Throwable cause = cur.getCause();
			String curMsg = cur.getMessage();
			if (cause != null && cause.getMessage() != null && curMsg != null && curMsg.endsWith(cause.getMessage())) {
				String own = curMsg.substring(0, curMsg.length() - cause.getMessage().length());
				own = own.stripTrailing();
				if (own.endsWith("cause:")) {
					own = own.substring(0, own.length() - "cause:".length()).stripTrailing();
				}
				layers.add(0, own);
				cur = cause;
			}
			else {
				layers.add(0, curMsg);
				break;
			}
		}
		return layers;
	}

	private static String candidateA(List<String> layers) {
		StringBuilder sb = new StringBuilder();
		sb.append("Root cause:\n");
		sb.append(indent(layers.get(0)));
		if (layers.size() > 1) {
			sb.append("\n\nWrapped while:\n");
			for (int i = 1; i < layers.size(); i++) {
				sb.append("  ").append(i).append(". ").append(layers.get(i)).append("\n");
			}
		}
		return sb.toString().stripTrailing();
	}

	private static String candidateB(List<String> layers) {
		StringBuilder sb = new StringBuilder();
		sb.append(layers.get(0));
		for (int i = 1; i < layers.size(); i++) {
			sb.append("\n\n...while:\n").append(layers.get(i));
		}
		return sb.toString();
	}

	private static String candidateC(List<String> layers) {
		StringBuilder sb = new StringBuilder();
		sb.append(layers.get(0));
		for (int i = 1; i < layers.size(); i++) {
			sb.append("\n  ↳ ").append(layers.get(i));
		}
		return sb.toString();
	}

	private static String indent(String text) {
		return text.lines().map(l -> "  " + l).reduce((a, b) -> a + "\n" + b).orElse("");
	}

}
