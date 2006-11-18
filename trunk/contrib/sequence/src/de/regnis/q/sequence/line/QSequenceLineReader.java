package de.regnis.q.sequence.line;

import java.io.*;

/**
 * @author Marc Strapetz
 */
final class QSequenceLineReader {

	// Fields =================================================================

	private final byte[] myCustomEolBytes;
	private byte[] buffer;

	// Setup ==================================================================

	public QSequenceLineReader(byte[] customEolBytes) {
		this(8192, customEolBytes);
	}

	public QSequenceLineReader(int initialBufferSize, byte[] customEolBytes) {
		myCustomEolBytes = customEolBytes;
		buffer = new byte[initialBufferSize];
	}

	// Static =================================================================

	public void read(InputStream rawStream, QSequenceLineCache cache) throws IOException {
		final BufferedInputStream stream = new BufferedInputStream(rawStream);
		try {
			int pushBack = -1;
			int from = 0;
			int length = 0;
			int eolLength = 0;
			int lastLength = 0;
			for (; ;) {
				int ch = pushBack;
				if (ch != -1) {
					pushBack = -1;
				}
				else {
					ch = stream.read();
				}

				if (ch != -1) {
					append(length, (byte)(ch & 0xff));
					length++;
				}

				switch (ch) {
				case '\r':
					pushBack = stream.read();
					if (pushBack == '\n') {
						append(length, (byte)(pushBack & 0xff));
						length++;
						eolLength++;
						pushBack = -1;
					}
				case '\n':
					eolLength++;
				case -1:
					if (length > 0) {
						final byte[] bytes;
						if (myCustomEolBytes != null && eolLength > 0) {
							bytes = new byte[length - eolLength + myCustomEolBytes.length];
							System.arraycopy(buffer, 0, bytes, 0, length - eolLength);
							System.arraycopy(myCustomEolBytes, 0, bytes, length - eolLength, myCustomEolBytes.length);
						}
						else {
							bytes = new byte[length];
							System.arraycopy(buffer, 0, bytes, 0, length);
						}
						cache.addLine(new QSequenceLine(from, bytes));
						lastLength = length;
					}
					from = from + length;
					length = 0;
					eolLength = 0;
				}

				if (ch == -1) {
					lastLength--;
					if (myCustomEolBytes != null && lastLength < buffer.length && lastLength >= 0) {
						if (buffer[lastLength] == '\r' || buffer[lastLength] == '\n') {
							cache.addLine(new QSequenceLine(from, new byte[0]));
						}
					}
					break;
				}
			}
		}
		finally {
			stream.close();
		}
	}

	// Utils ==================================================================

	private void append(int position, byte ch) {
		if (position >= buffer.length) {
			final byte[] newArray = new byte[buffer.length * 2];
			System.arraycopy(buffer, 0, newArray, 0, buffer.length);
			buffer = newArray;
		}
		buffer[position] = ch;
	}
}