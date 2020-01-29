/*
 * Copyright (c) 2011 Howard Hughes Medical Institute.
 * All rights reserved.
 * Use is subject to Janelia Farm Research Campus Software Copyright 1.1
 * license terms (http://license.janelia.org/license/jfrc_copyright_1_1.html).
 */

//
// RandomAccessInputStream.java
//

package loci.common;

import java.io.DataInput;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 *
 * <dl><dt><b>Source code:</b></dt>
 * <dd><a href="http://dev.loci.wisc.edu/trac/java/browser/trunk/components/common/src/loci/common/RandomAccessInputStream.java">Trac</a>,
 * <a href="http://dev.loci.wisc.edu/svn/java/trunk/components/common/src/loci/common/RandomAccessInputStream.java">SVN</a></dd></dl>
 *
 * @author Melissa Linkert melissa at glencoesoftware.com
 * @author Curtis Rueden ctrueden at wisc.edu
 */
public class RandomAccessInputStream extends InputStream implements DataInput {

  // -- Constants --

  /** Maximum size of the buffer used by the DataInputStream. */
  protected static final int MAX_OVERHEAD = 1048576;

  /**
   * Block size to use when searching through the stream.
   */
  protected static final int DEFAULT_BLOCK_SIZE = 256 * 1024; // 256 KB

  /** Maximum number of bytes to search when searching through the stream. */
  protected static final int MAX_SEARCH_SIZE = 512 * 1024 * 1024; // 512 MB

  // -- Fields --

  protected IRandomAccess raf;

  /** The file name. */
  protected String file;

  // -- Constructors --

  /**
   * Constructs a hybrid RandomAccessFile/DataInputStream
   * around the given file.
   */
  public RandomAccessInputStream(String file) throws IOException {
    this(Location.getHandle(file));
    this.file = file;
  }

  /** Constructs a random access stream around the given handle. */
  public RandomAccessInputStream(IRandomAccess handle) throws IOException {
    raf = handle;
    raf.setOrder(ByteOrder.BIG_ENDIAN);
    seek(0);
  }

  /** Constructs a random access stream around the given byte array. */
  public RandomAccessInputStream(byte[] array) throws IOException {
    this(new ByteArrayHandle(array));
  }

  // -- RandomAccessInputStream API methods --

  /** Seeks to the given offset within the stream. */
  public void seek(long pos) throws IOException {
    raf.seek(pos);
  }

  /** Gets the number of bytes in the file. */
  public long length() throws IOException {
    return raf.length();
  }

  /** Gets the current (absolute) file pointer. */
  public long getFilePointer() throws IOException {
    return raf.getFilePointer();
  }

  /** Closes the streams. */
  public void close() throws IOException {
    if (Location.getMappedFile(file) != null) return;
    if (raf != null) raf.close();
    raf = null;
  }

  /** Sets the endianness of the stream. */
  public void order(boolean little) {
    if (raf != null) {
      raf.setOrder(little ? ByteOrder.LITTLE_ENDIAN : ByteOrder.BIG_ENDIAN);
    }
  }

  /** Gets the endianness of the stream. */
  public boolean isLittleEndian() {
    return raf.getOrder() == ByteOrder.LITTLE_ENDIAN;
  }

  /**
   * Reads a string ending with one of the characters in the given string.
   *
   * @see #findString(String...)
   */
  public String readString(String lastChars) throws IOException {
    if (lastChars.length() == 1) return findString(lastChars);
    String[] terminators = new String[lastChars.length()];
    for (int i=0; i<terminators.length; i++) {
      terminators[i] = lastChars.substring(i, i + 1);
    }
    return findString(terminators);
  }

  /**
   * Reads a string ending with one of the given terminating substrings.
   *
   * @param terminators The strings for which to search.
   *
   * @return The string from the initial position through the end of the
   *   terminating sequence, or through the end of the stream if no
   *   terminating sequence is found.
   */
  public String findString(String... terminators) throws IOException {
    return findString(true, DEFAULT_BLOCK_SIZE, terminators);
  }

  /**
   * Reads or skips a string ending with
   * one of the given terminating substrings.
   *
   * @param saveString Whether to collect the string from the current file
   *   pointer to the terminating bytes, and return it. If false, returns null.
   * @param terminators The strings for which to search.
   *
   * @throws IOException If saveString flag is set
   *   and the maximum search length (512 MB) is exceeded.
   *
   * @return The string from the initial position through the end of the
   *   terminating sequence, or through the end of the stream if no
   *   terminating sequence is found, or null if saveString flag is unset.
   */
  public String findString(boolean saveString, String... terminators)
    throws IOException
  {
    return findString(saveString, DEFAULT_BLOCK_SIZE, terminators);
  }

  /**
   * Reads a string ending with one of the given terminating
   * substrings, using the specified block size for buffering.
   *
   * @param blockSize The block size to use when reading bytes in chunks.
   * @param terminators The strings for which to search.
   *
   * @return The string from the initial position through the end of the
   *   terminating sequence, or through the end of the stream if no
   *   terminating sequence is found.
   */
  public String findString(int blockSize, String... terminators)
    throws IOException
  {
    return findString(true, blockSize, terminators);
  }

  /**
   * Reads or skips a string ending with one of the given terminating
   * substrings, using the specified block size for buffering.
   *
   * @param saveString Whether to collect the string from the current file
   *   pointer to the terminating bytes, and return it. If false, returns null.
   * @param blockSize The block size to use when reading bytes in chunks.
   * @param terminators The strings for which to search.
   *
   * @throws IOException If saveString flag is set
   *   and the maximum search length (512 MB) is exceeded.
   *
   * @return The string from the initial position through the end of the
   *   terminating sequence, or through the end of the stream if no
   *   terminating sequence is found, or null if saveString flag is unset.
   */
  public String findString(boolean saveString, int blockSize,
    String... terminators) throws IOException
  {
    StringBuilder out = new StringBuilder();
    long startPos = getFilePointer();
    long bytesDropped = 0;
    long inputLen = length();
    long maxLen = inputLen - startPos;
    boolean tooLong = saveString && maxLen > MAX_SEARCH_SIZE;
    if (tooLong) maxLen = MAX_SEARCH_SIZE;
    boolean match = false;
    int maxTermLen = 0;
    for (String term : terminators) {
      int len = term.length();
      if (len > maxTermLen) maxTermLen = len;
    }

    InputStreamReader in = new InputStreamReader(this);
    char[] buf = new char[blockSize];
    long loc = 0;
    while (loc < maxLen && getFilePointer() < length() - 1) {
      // if we're not saving the string, drop any old, unnecessary output
      if (!saveString) {
        int outLen = out.length();
        if (outLen >= maxTermLen) {
          int dropIndex = outLen - maxTermLen + 1;
          String last = out.substring(dropIndex, outLen);
          out.setLength(0);
          out.append(last);
          bytesDropped += dropIndex;
        }
      }

      // read block from stream
      int r = in.read(buf, 0, blockSize);
      if (r <= 0) throw new IOException("Cannot read from stream: " + r);

      // append block to output
      out.append(buf, 0, r);

      // check output, returning smallest possible string
      int min = Integer.MAX_VALUE, tagLen = 0;
      for (String t : terminators) {
        int len = t.length();
        int start = (int) (loc - bytesDropped - len);
        int value = out.indexOf(t, start < 0 ? 0 : start);
        if (value >= 0 && value < min) {
          match = true;
          min = value;
          tagLen = len;
        }
      }

      if (match) {
        // reset stream to proper location
        seek(startPos + bytesDropped + min + tagLen);

        // trim output string
        if (saveString) {
          out.setLength(min + tagLen);
          return out.toString();
        }
        return null;
      }

      loc += r;
    }

    // no match
    if (tooLong) throw new IOException("Maximum search length reached.");
    return saveString ? out.toString() : null;
  }

  // -- DataInput API methods --

  /** Read an input byte and return true if the byte is nonzero. */
  public boolean readBoolean() throws IOException {
    return raf.readBoolean();
  }

  /** Read one byte and return it. */
  public byte readByte() throws IOException {
    return raf.readByte();
  }

  /** Read an input char. */
  public char readChar() throws IOException {
    return raf.readChar();
  }

  /** Read eight bytes and return a double value. */
  public double readDouble() throws IOException {
    return raf.readDouble();
  }

  /** Read four bytes and return a float value. */
  public float readFloat() throws IOException {
    return raf.readFloat();
  }

  /** Read four input bytes and return an int value. */
  public int readInt() throws IOException {
    return raf.readInt();
  }

  /** Read the next line of text from the input stream. */
  public String readLine() throws IOException {
    String line = findString("\n");
    return line.length() == 0 ? null : line;
  }

  /** Read a string of arbitrary length, terminated by a null char. */
  public String readCString() throws IOException {
    String line = findString("\0");
    return line.length() == 0 ? null : line;
  }

  /** Read a string of up to length n. */
  public String readString(int n) throws IOException {
    int avail = available();
    if (n > avail) n = avail;
    byte[] b = new byte[n];
    readFully(b);
    return new String(b);
  }

  /** Read eight input bytes and return a long value. */
  public long readLong() throws IOException {
    return raf.readLong();
  }

  /** Read two input bytes and return a short value. */
  public short readShort() throws IOException {
    return raf.readShort();
  }

  /** Read an input byte and zero extend it appropriately. */
  public int readUnsignedByte() throws IOException {
    return raf.readUnsignedByte();
  }

  /** Read two bytes and return an int in the range 0 through 65535. */
  public int readUnsignedShort() throws IOException {
    return raf.readUnsignedShort();
  }

  /** Read a string that has been encoded using a modified UTF-8 format. */
  public String readUTF() throws IOException {
    return raf.readUTF();
  }

  /** Skip n bytes within the stream. */
  public int skipBytes(int n) throws IOException {
    return raf.skipBytes(n);
  }

  /** Read bytes from the stream into the given array. */
  public int read(byte[] array) throws IOException {
    int rtn = raf.read(array);
    if (rtn == 0 && raf.getFilePointer() >= raf.length() - 1) rtn = -1;
    return rtn;
  }

  /**
   * Read n bytes from the stream into the given array at the specified offset.
   */
  public int read(byte[] array, int offset, int n) throws IOException {
    int rtn = raf.read(array, offset, n);
    if (rtn == 0 && raf.getFilePointer() >= raf.length() - 1) rtn = -1;
    return rtn;
  }

  /** Read bytes from the stream into the given buffer. */
  public int read(ByteBuffer buf) throws IOException {
    return raf.read(buf);
  }

  /**
   * Read n bytes from the stream into the given buffer at the specified offset.
   */
  public int read(ByteBuffer buf, int offset, int n) throws IOException {
    return raf.read(buf, offset, n);
  }

  /** Read bytes from the stream into the given array. */
  public void readFully(byte[] array) throws IOException {
    raf.readFully(array);
  }

  /**
   * Read n bytes from the stream into the given array at the specified offset.
   */
  public void readFully(byte[] array, int offset, int n) throws IOException {
    raf.readFully(array, offset, n);
  }

  // -- InputStream API methods --

  public int read() throws IOException {
    int b = readByte();
    if (b == -1 && (getFilePointer() >= length())) return 0;
    return b;
  }

  public int available() throws IOException {
    long remain = length() - getFilePointer();
    if (remain > Integer.MAX_VALUE) remain = Integer.MAX_VALUE;
    return (int) remain;
  }

  public void mark(int readLimit) { }

  public boolean markSupported() { return false; }

  public void reset() throws IOException { }

}
