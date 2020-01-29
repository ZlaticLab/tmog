/*
 * Copyright (c) 2011 Howard Hughes Medical Institute.
 * All rights reserved.
 * Use is subject to Janelia Farm Research Campus Software Copyright 1.1
 * license terms (http://license.janelia.org/license/jfrc_copyright_1_1.html).
 */

//
// Region.java
//

package loci.common;

/**
 * A class for representing a rectangular region.
 * This class is very similar to {@link java.awt.Rectangle};
 * it mainly exists to avoid problems with AWT, JNI and headless operation.
 *
 * <dl><dt><b>Source code:</b></dt>
 * <dd><a href="http://dev.loci.wisc.edu/trac/java/browser/trunk/components/common/src/loci/common/Region.java">Trac</a>,
 * <a href="http://dev.loci.wisc.edu/svn/java/trunk/components/common/src/loci/common/Region.java">SVN</a></dd></dl>
 */
public class Region {

  // -- Fields --

  public int x;
  public int y;
  public int width;
  public int height;

  // -- Constructor --

  public Region() { }

  public Region(int x, int y, int width, int height) {
    this.x = x;
    this.y = y;
    this.width = width;
    this.height = height;
  }

  // -- Region API methods --

  /** Returns true if this region intersects the given region. */
  public boolean intersects(Region r) {
    int tw = this.width;
    int th = this.height;
    int rw = r.width;
    int rh = r.height;
    if (rw <= 0 || rh <= 0 || tw <= 0 || th <= 0) {
      return false;
    }
    int tx = this.x;
    int ty = this.y;
    int rx = r.x;
    int ry = r.y;
    rw += rx;
    rh += ry;
    tw += tx;
    th += ty;
    boolean rtn = ((rw < rx || rw > tx) && (rh < ry || rh > ty) &&
      (tw < tx || tw > rx) && (th < ty || th > ry));
    return rtn;
  }

  /**
   * Returns a Region representing the intersection of this Region with the
   * given Region.  If the two Regions do not intersect, the result is an
   * empty Region.
   */
  public Region intersection(Region r) {
    int x = Math.max(this.x, r.x);
    int y = Math.max(this.y, r.y);
    int w = Math.min(this.x + this.width, r.x + r.width) - x;
    int h = Math.min(this.y + this.height, r.y + r.height) - y;

    if (w < 0) w = 0;
    if (h < 0) h = 0;

    return new Region(x, y, w, h);
  }

  public String toString() {
    return "x=" + x + ", y=" + y + ", w=" + width + ", h=" + height;
  }

}
