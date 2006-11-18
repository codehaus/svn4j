package de.regnis.q.sequence.line;

import java.io.*;
import java.util.*;

import de.regnis.q.sequence.*;
import de.regnis.q.sequence.core.*;
import de.regnis.q.sequence.media.*;

/**
 * @author Marc Strapetz
 */
public final class QSequenceLineMedia implements QSequenceCachableMedia, QSequenceMediaComparer {

	// Constants ==============================================================

	public static final int FILE_SEGMENT_SIZE = 16384;
	public static final int MEMORY_THRESHOLD = 1048576;
	public static final int SEGMENT_ENTRY_SIZE = 16;

	// Static =================================================================

	public static QSequenceLineCache readLines(QSequenceLineRAData data, byte[] customEolBytes) throws IOException {
		if (data.length() <= MEMORY_THRESHOLD) {
			final InputStream stream = data.read(0, data.length());
			try {
				return QSequenceLineMemoryCache.read(stream, customEolBytes);
			}
			finally {
				stream.close();
			}
		}

		final File tempDirectory = File.createTempFile("q.sequence.line.", ".temp");
		tempDirectory.delete();
		return QSequenceLineFileSystemCache.create(data, tempDirectory, customEolBytes, MEMORY_THRESHOLD, FILE_SEGMENT_SIZE);
	}

	public static QSequenceLineResult createBlocks(QSequenceLineRAData leftData, QSequenceLineRAData rightData, byte[] customEolBytes) throws IOException, QSequenceException {
		final File tempDirectory = File.createTempFile("q.sequence.line.", ".temp");
		tempDirectory.delete();
		return createBlocks(leftData, rightData, customEolBytes, MEMORY_THRESHOLD, FILE_SEGMENT_SIZE, 1.0, tempDirectory);
	}

	public static QSequenceLineResult createBlocks(QSequenceLineRAData leftData, QSequenceLineRAData rightData, byte[] customEolBytes, int memoryThreshold, int fileSegmentSize, double searchDepthExponent, File tempDirectory) throws IOException, QSequenceException {
		if (leftData.length() <= memoryThreshold && rightData.length() <= memoryThreshold) {
			final InputStream leftStream = leftData.read(0, leftData.length());
			final InputStream rightStream = rightData.read(0, rightData.length());
			try {
				return createBlocksInMemory(leftStream, rightStream, customEolBytes, searchDepthExponent);
			}
			finally {
				leftStream.close();
				rightStream.close();
			}
		}

		return createBlocksInFilesystem(leftData, rightData, tempDirectory, customEolBytes, searchDepthExponent, memoryThreshold, fileSegmentSize);
	}

	static QSequenceLineResult createBlocksInMemory(InputStream leftStream, InputStream rightStream, byte[] customEolBytes, double searchDepthExponent) throws IOException, QSequenceException {
		final QSequenceLineMemoryCache leftCache = QSequenceLineMemoryCache.read(leftStream, customEolBytes);
		final QSequenceLineMemoryCache rightCache = QSequenceLineMemoryCache.read(rightStream, customEolBytes);
		final QSequenceLineMedia lineMedia = new QSequenceLineMedia(leftCache, rightCache);
		final QSequenceCachingMedia cachingMedia = new QSequenceCachingMedia(lineMedia, new QSequenceDummyCanceller());
		final QSequenceDiscardingMedia discardingMedia = new QSequenceDiscardingMedia(cachingMedia, new QSequenceDiscardingMediaNoConfusionDectector(true), new QSequenceDummyCanceller());
		final List blocks = new QSequenceDifference(discardingMedia, discardingMedia, getSearchDepth(lineMedia, searchDepthExponent)).getBlocks();
		new QSequenceDifferenceBlockShifter(cachingMedia, cachingMedia).shiftBlocks(blocks);
		return new QSequenceLineResult(blocks, leftCache, rightCache);
	}

	static QSequenceLineResult createBlocksInFilesystem(QSequenceLineRAData leftData, QSequenceLineRAData rightData, File tempDirectory, byte[] customEolBytes, double searchDepthExponent, int memoryThreshold, int fileSegmentSize) throws IOException, QSequenceException {
		final QSequenceLineFileSystemCache leftCache = QSequenceLineFileSystemCache.create(leftData, tempDirectory, customEolBytes, memoryThreshold, fileSegmentSize);
		final QSequenceLineFileSystemCache rightCache = QSequenceLineFileSystemCache.create(rightData, tempDirectory, customEolBytes, memoryThreshold, fileSegmentSize);
		final QSequenceLineMedia lineMedia = new QSequenceLineMedia(leftCache, rightCache);
		final List blocks = new QSequenceDifference(lineMedia, new QSequenceMediaDummyIndexTransformer(lineMedia), getSearchDepth(lineMedia, searchDepthExponent)).getBlocks();
		new QSequenceDifferenceBlockShifter(lineMedia, lineMedia).shiftBlocks(blocks);
		return new QSequenceLineResult(blocks, leftCache, rightCache);
	}

	// Fields =================================================================

	private final QSequenceLineCache leftCache;
	private final QSequenceLineCache rightCache;

	// Setup ==================================================================

	public QSequenceLineMedia(QSequenceLineCache leftCache, QSequenceLineCache rightCache) {
		this.leftCache = leftCache;
		this.rightCache = rightCache;
	}

	// Implemented ============================================================

	public int getLeftLength() {
		return leftCache.getLineCount();
	}

	public int getRightLength() {
		return rightCache.getLineCount();
	}

	public Object getMediaLeftObject(int index) throws QSequenceException {
		try {
			return leftCache.getLine(index);
		}
		catch (IOException ex) {
			throw new QSequenceException(ex);
		}
	}

	public Object getMediaRightObject(int index) throws QSequenceException {
		try {
			return rightCache.getLine(index);
		}
		catch (IOException ex) {
			throw new QSequenceException(ex);
		}
	}

	public boolean equals(int leftIndex, int rightIndex) throws QSequenceException {
		try {
			final int leftHash = leftCache.getLineHash(leftIndex);
			final int rightHash = rightCache.getLineHash(rightIndex);
			if (leftHash != 0 && rightHash != 0 && leftHash != rightHash) {
				return false;
			}

			return leftCache.getLine(leftIndex).equals(rightCache.getLine(rightIndex));
		}
		catch (IOException ex) {
			throw new QSequenceException(ex);
		}
	}

	public boolean equalsLeft(int left1, int left2) throws QSequenceException {
		try {
			return leftCache.getLine(left1).equals(leftCache.getLine(left2));
		}
		catch (IOException ex) {
			throw new QSequenceException(ex);
		}
	}

	public boolean equalsRight(int right1, int right2) throws QSequenceException {
		try {
			return rightCache.getLine(right1).equals(rightCache.getLine(right2));
		}
		catch (IOException ex) {
			throw new QSequenceException(ex);
		}
	}

	// Utils ==================================================================

	private static int getSearchDepth(QSequenceLineMedia lineMedia, double searchDepthExponent) {
		QSequenceAssert.assertTrue(searchDepthExponent >= 0.0 && searchDepthExponent <= 1.0);

		if (searchDepthExponent == 1.0) {
			return Integer.MAX_VALUE;
		}

		return Math.max(256, (int)Math.pow(lineMedia.getLeftLength() + lineMedia.getRightLength(), searchDepthExponent));
	}
}