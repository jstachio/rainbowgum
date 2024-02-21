package io.jstach.rainbowgum.pattern.format;

interface Abbreviator {

	public static final char DOT = '.';

	public String abbreviate(
			String in);
	
	public static Abbreviator of(int length) {
		if (length <= 0) {
			return StandardAbbreviator.CLASS_NAME_ONLY;
		}
		return new TargetLengthBasedClassNameAbbreviator(length);
	}

	enum StandardAbbreviator implements Abbreviator {
		CLASS_NAME_ONLY {

			public String abbreviate(
					String fqClassName) {
				// we ignore the fact that the separator character can also be a
				// dollar
				// If the inner class is org.good.AClass#Inner, returning
				// AClass#Inner seems most appropriate
				int lastIndex = fqClassName.lastIndexOf(DOT);
				if (lastIndex != -1) {
					return fqClassName.substring(lastIndex + 1, fqClassName.length());
				} else {
					return fqClassName;
				}
			}
		};
	}

	class TargetLengthBasedClassNameAbbreviator implements Abbreviator {

		final int targetLength;

		public TargetLengthBasedClassNameAbbreviator(
				int targetLength) {
			this.targetLength = targetLength;
		}

		public String abbreviate(
				String fqClassName) {
			if (fqClassName == null) {
				throw new IllegalArgumentException("Class name may not be null");
			}

			int inLen = fqClassName.length();
			if (inLen < targetLength) {
				return fqClassName;
			}

			StringBuilder buf = new StringBuilder(inLen);

			int rightMostDotIndex = fqClassName.lastIndexOf(DOT);

			if (rightMostDotIndex == -1)
				return fqClassName;

			// length of last segment including the dot
			int lastSegmentLength = inLen - rightMostDotIndex;

			int leftSegments_TargetLen = targetLength - lastSegmentLength;
			if (leftSegments_TargetLen < 0)
				leftSegments_TargetLen = 0;

			int leftSegmentsLen = inLen - lastSegmentLength;

			// maxPossibleTrim denotes the maximum number of characters we aim
			// to trim
			// the actual number of character trimmed may be higher since
			// segments, when
			// reduced, are reduced to just one character
			int maxPossibleTrim = leftSegmentsLen - leftSegments_TargetLen;

			int trimmed = 0;
			boolean inDotState = true;

			int i = 0;
			for (; i < rightMostDotIndex; i++) {
				char c = fqClassName.charAt(i);
				if (c == DOT) {
					// if trimmed too many characters, let us stop
					if (trimmed >= maxPossibleTrim)
						break;
					buf.append(c);
					inDotState = true;
				} else {
					if (inDotState) {
						buf.append(c);
						inDotState = false;
					} else {
						trimmed++;
					}
				}
			}
			// append from the position of i which may include the last seen DOT
			buf.append(fqClassName.substring(i));
			return buf.toString();
		}
	}
}
