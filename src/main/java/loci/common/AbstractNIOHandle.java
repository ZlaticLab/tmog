/*
 * Copyright (c) 2011 Howard Hughes Medical Institute.
 * All rights reserved.
 * Use is subject to Janelia Farm Research Campus Software Copyright 1.1
 * license terms (http://license.janelia.org/license/jfrc_copyright_1_1.html).
 */

//
// AbstractNIOHandle.java
//

package loci.common;

import java.io.IOException;

/**
 * A wrapper for buffered NIO logic that implements the IRandomAccess interface.
 *
 * <dl><dt><b>Source code:</b></dt>
 * <dd><a href="http://dev.loci.wisc.edu/trac/java/browser/trunk/components/common/src/loci/common/AbstractNIOHandle.java">Trac</a>,
 * <a href="http://dev.loci.wisc.edu/svn/java/trunk/components/common/src/loci/common/AbstractNIOHandle.java">SVN</a></dd></dl>
 *
 * @see IRandomAccess
 * @see java.io.RandomAccessFile
 *
 * @author Chris Allan <callan at blackcat dot ca>
 */
public abstract class AbstractNIOHandle implements IRandomAccess {

  /** Error message to be used when instantiating an EOFException. */
  protected static final String EOF_ERROR_MSG =
    "Attempting to read beyond end of file.";

  //-- Constants --

  // -- Fields --

  // -- Constructors --

  // -- AbstractNIOHandle methods --

  /**
   * Ensures that the file mode is either "r" or "rw".
   * @param mode Mode to validate.
   * @throws IllegalArgumentException If an illegal mode is passed.
   */
  protected void validateMode(String mode) {
    if (!(mode.equals("r") || mode.equals("rw"))) {
      throw new IllegalArgumentException(
        String.format("%s mode not in supported modes ('r', 'rw')", mode));
    }
  }

  /**
   * Ensures that the handle has the correct length to be written to and
   * extends it as required.
   * @param writeLength Number of bytes to write.
   * @return <code>true</code> if the buffer has not required an extension.
   * <code>false</code> otherwise.
   * @throws IOException If there is an error changing the handle's length.
   */
  protected boolean validateLength(int writeLength) throws IOException {
    if (getFilePointer() + writeLength > length()) {
      setLength(getFilePointer() + writeLength);
      return false;
    }
    return true;
  }

  /**
   * Sets the new length of the handle.
   * @param length New length.
   * @throws IOException If there is an error changing the handle's length.
   */
  protected abstract void setLength(long length) throws IOException;
}
