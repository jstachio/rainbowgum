package io.jstach.rainbowgum.otlp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.HexFormat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import com.google.protobuf.ByteString;
import com.google.protobuf.UnknownFieldSet;

import io.opentelemetry.proto.collector.logs.v1.ExportLogsPartialSuccess;
import io.opentelemetry.proto.collector.logs.v1.ExportLogsServiceResponse;

class OtlpResponseTest {

	@Test
	void unknownFieldsBeforeKnownFieldsAreSkipped() throws Exception {
		var unknown = UnknownFieldSet.newBuilder()
			.addField(3,
					UnknownFieldSet.Field.newBuilder()
						.addVarint(123)
						.addFixed32(456)
						.addFixed64(789)
						.addLengthDelimited(ByteString.copyFromUtf8("future"))
						.build())
			.build()
			.toByteString();
		var partial = ExportLogsPartialSuccess.newBuilder().setRejectedLogRecords(1).setErrorMessage("bad").build();
		var inner = unknown.concat(partial.toByteString());
		var response = UnknownFieldSet.newBuilder()
			.addField(1, UnknownFieldSet.Field.newBuilder().addLengthDelimited(inner).build())
			.build()
			.toByteString();
		var bytes = unknown.concat(response).toByteArray();
		assertEquals(1, ExportLogsServiceResponse.parseFrom(bytes).getPartialSuccess().getRejectedLogRecords());
		assertEquals(new OtlpOutput.PartialSuccess(1, "bad"), OtlpOutput.PartialSuccess.decode(bytes));
	}

	enum Malformed {

		TRUNCATED_MESSAGE("0a0508", """
				Invalid protobuf field length"""), TRUNCATED_STRING("0a021205", """
				Invalid protobuf field length"""), TRUNCATED_UNKNOWN("120566", """
				Invalid protobuf field length"""), TRUNCATED_FIXED64("19ff", """
				Truncated protobuf field"""), TRUNCATED_FIXED32("1dff", """
				Truncated protobuf field"""), TRUNCATED_VARINT("0a020880", """
				Truncated protobuf varint"""), OVERFLOW_LENGTH("0affffffffffffffffff01", """
				Invalid protobuf field length"""), OVERFLOW_VARINT("0a0b08ffffffffffffffffff02", """
				Malformed protobuf varint""");

		final String hex;

		final String message;

		Malformed(String hex, String message) {
			this.hex = hex;
			this.message = message;
		}

	}

	@ParameterizedTest
	@EnumSource(Malformed.class)
	void malformedResponsesHaveBoundedReads(Malformed input) {
		var e = assertThrows(IllegalArgumentException.class,
				() -> OtlpOutput.PartialSuccess.decode(HexFormat.of().parseHex(input.hex)));
		assertEquals(input.message, e.getMessage());
	}

	@Test
	void backoffSpreadsRetriesWithinIncreasingWindows() {
		assertEquals(500, OtlpOutput.backoff(0, 0));
		assertEquals(750, OtlpOutput.backoff(0, 0.5));
		assertEquals(999, OtlpOutput.backoff(0, Math.nextDown(1.0)));
		assertEquals(1000, OtlpOutput.backoff(1, 0));
		assertEquals(1500, OtlpOutput.backoff(1, 0.5));
		assertEquals(2000, OtlpOutput.backoff(2, 0));
		assertEquals(3000, OtlpOutput.backoff(2, 0.5));
	}

}
