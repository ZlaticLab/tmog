/*
 * Copyright (c) 2011 Howard Hughes Medical Institute.
 * All rights reserved.
 * Use is subject to Janelia Farm Research Campus Software Copyright 1.1
 * license terms (http://license.janelia.org/license/jfrc_copyright_1_1.html).
 */

//
// Location.java
//

package loci.common;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.HashMap;

// HACK: for scan-deps.pl: The following packages are not actually "optional":
// optional org.apache.log4j, optional org.slf4j.impl

/**
 * Pseudo-extension of java.io.File that supports reading over HTTP.
 * It is strongly recommended that you use this instead of java.io.File.
 *
 * <dl><dt><b>Source code:</b></dt>
 * <dd><a href="http://dev.loci.wisc.edu/trac/java/browser/trunk/components/common/src/loci/common/Location.java">Trac</a>,
 * <a href="http://dev.loci.wisc.edu/svn/java/trunk/components/common/src/loci/common/Location.java">SVN</a></dd></dl>
 */
public class Location {

  // -- Static fields --

  /** Map from given filenames to actual filenames. */
  private static HashMap<String, Object> idMap = new HashMap<String, Object>();

  // -- Fields --

  private boolean isURL = true;
  private URL url;
  private File file;

  // -- Constructors --

  public Location(String pathname) {
    try {
      url = new URL(getMappedId(pathname));
    }
    catch (MalformedURLException e) {
      isURL = false;
    }
    if (!isURL) file = new File(getMappedId(pathname));
  }

  public Location(File file) {
    isURL = false;
    this.file = file;
  }

  public Location(String parent, String child) {
    this(parent + File.separator + child);
  }

  public Location(Location parent, String child) {
    this(parent.getAbsolutePath(), child);
  }

  // -- Location API methods --

  /**
   * Maps the given id to an actual filename on disk. Typically actual
   * filenames are used for ids, making this step unnecessary, but in some
   * cases it is useful; e.g., if the file has been renamed to conform to a
   * standard naming scheme and the original file extension is lost, then
   * using the original filename as the id assists format handlers with type
   * identification and pattern matching, and the id can be mapped to the
   * actual filename for reading the file's contents.
   * @see #getMappedId(String)
   */
  public static void mapId(String id, String filename) {
    if (id == null) return;
    if (filename == null) idMap.remove(id);
    else idMap.put(id, filename);
  }

  /** Maps the given id to the given IRandomAccess object. */
  public static void mapFile(String id, IRandomAccess ira) {
    if (id == null) return;
    if (ira == null) idMap.remove(id);
    else idMap.put(id, ira);
  }

  /**
   * Gets the actual filename on disk for the given id. Typically the id itself
   * is the filename, but in some cases may not be; e.g., if OMEIS has renamed
   * a file from its original name to a standard location such as Files/101,
   * the original filename is useful for checking the file extension and doing
   * pattern matching, but the renamed filename is required to read its
   * contents.
   * @see #mapId(String, String)
   */
  public static String getMappedId(String id) {
    if (idMap == null) return id;
    String filename = null;
    if (id != null && (idMap.get(id) instanceof String)) {
      filename = (String) idMap.get(id);
    }
    return filename == null ? id : filename;
  }

  /** Gets the random access handle for the given id. */
  public static IRandomAccess getMappedFile(String id) {
    if (idMap == null) return null;
    IRandomAccess ira = null;
    if (id != null && (idMap.get(id) instanceof IRandomAccess)) {
      ira = (IRandomAccess) idMap.get(id);
    }
    return ira;
  }

  /** Return the id mapping. */
  public static HashMap<String, Object> getIdMap() { return idMap; }

  /**
   * Set the id mapping using the given HashMap.
   *
   * @throws IllegalArgumentException if the given HashMap is null.
   */
  public static void setIdMap(HashMap<String, Object> map) {
    if (map == null) throw new IllegalArgumentException("map cannot be null");
    idMap = map;
  }

  /**
   * Gets an IRandomAccess object that can read from the given file.
   * @see IRandomAccess
   */
  public static IRandomAccess getHandle(String id) throws IOException {
    return getHandle(id, false);
  }

  /**
   * Gets an IRandomAccess object that can read from or write to the given file.
   * @see IRandomAccess
   */
  public static IRandomAccess getHandle(String id, boolean writable)
    throws IOException
  {
    IRandomAccess handle = getMappedFile(id);
    if (handle == null) {
      String mapId = getMappedId(id);
      handle = new NIOFileHandle(mapId, writable ? "rw" : "r");
    }
    return handle;
  }

  /**
   * Return a list of all of the files in this directory.  If 'noHiddenFiles' is
   * set to true, then hidden files are omitted.
   *
   * @see java.io.File#list()
   */
  public String[] list(boolean noHiddenFiles) {
    ArrayList<String> files = new ArrayList<String>();
    if (isURL) {
      try {
        URLConnection c = url.openConnection();
        InputStream is = c.getInputStream();
        boolean foundEnd = false;

        while (!foundEnd) {
          byte[] b = new byte[is.available()];
          is.read(b);
          String s = new String(b);
          if (s.toLowerCase().indexOf("</html>") != -1) foundEnd = true;

          while (s.indexOf("a href") != -1) {
            int ndx = s.indexOf("a href") + 8;
            int idx = s.indexOf("\"", ndx);
            if (idx < 0) break;
            String f = s.substring(ndx, idx);
            if (files.size() > 0 && f.startsWith("/")) {
              return null;
            }
            s = s.substring(idx + 1);
            if (f.startsWith("?")) continue;
            Location check = new Location(getAbsolutePath(), f);
            if (check.exists() && (!noHiddenFiles || !check.isHidden())) {
              files.add(check.getName());
            }
          }
        }
      }
      catch (IOException e) {
        return null;
      }
    }
    else {
      if (file == null) return null;
      String[] f = file.list();
      if (f == null) return null;
      for (String name : f) {
        if (!noHiddenFiles ||
          !new Location(file.getAbsolutePath(), name).isHidden())
        {
          files.add(name);
        }
      }
    }
    return files.toArray(new String[files.size()]);
  }

  // -- File API methods --

  /**
   * If the underlying location is a URL, this method will return true if
   * the URL exists.
   * Otherwise, it will return true iff the file exists and is readable.
   *
   * @see java.io.File#canRead()
   */
  public boolean canRead() {
    return isURL ? (isDirectory() || isFile()) : file.canRead();
  }

  /**
   * If the underlying location is a URL, this method will always return false.
   * Otherwise, it will return true iff the file exists and is writable.
   *
   * @see java.io.File#canWrite()
   */
  public boolean canWrite() {
    return isURL ? false : file.canWrite();
  }

  /**
   * Creates a new empty file named by this Location's path name iff a file
   * with this name does not already exist.  Note that this operation is
   * only supported if the path name can be interpreted as a path to a file on
   * disk (i.e. is not a URL).
   *
   * @return true if the file was created successfully
   * @throws IOException if an I/O error occurred, or the
   *   abstract pathname is a URL
   * @see java.io.File#createNewFile()
   */
  public boolean createNewFile() throws IOException {
    if (isURL) throw new IOException("Unimplemented");
    return file.createNewFile();
  }

  /**
   * Deletes this file.  If {@link #isDirectory()} returns true, then the
   * directory must be empty in order to be deleted.  URLs cannot be deleted.
   *
   * @return true if the file was successfully deleted
   * @see java.io.File#delete()
   */
  public boolean delete() {
    return isURL ? false : file.delete();
  }

  /**
   * Request that this file be deleted when the JVM terminates.
   * This method will do nothing if the pathname represents a URL.
   *
   * @see java.io.File#deleteOnExit()
   */
  public void deleteOnExit() {
    if (!isURL) file.deleteOnExit();
  }

  /**
   * @see java.io.File#equals(Object)
   * @see java.net.URL#equals(Object)
   */
  public boolean equals(Object obj) {
    return isURL ? url.equals(obj) : file.equals(obj);
  }

  /**
   * Returns whether or not the pathname exists.
   * If the pathname is a URL, then existence is determined based on whether
   * or not we can successfully read content from the URL.
   *
   * @see java.io.File#exists()
   */
  public boolean exists() {
    if (isURL) {
      try {
        url.getContent();
        return true;
      }
      catch (IOException e) {
        return false;
      }
    }
    if (file.exists()) return true;
    if (getMappedFile(file.getPath()) != null) return true;

    String mappedId = getMappedId(file.getPath());
    return mappedId != null && new File(mappedId).exists();
  }

  /* @see java.io.File#getAbsoluteFile() */
  public Location getAbsoluteFile() {
    return new Location(getAbsolutePath());
  }

  /* @see java.io.File#getAbsolutePath() */
  public String getAbsolutePath() {
    return isURL ? url.toExternalForm() : file.getAbsolutePath();
  }

  /* @see java.io.File#getCanonicalFile() */
  public Location getCanonicalFile() throws IOException {
    return isURL ? getAbsoluteFile() : new Location(file.getCanonicalFile());
  }

  /**
   * Returns the canonical path to this file.
   * If the file is a URL, then the canonical path is equivalent to the
   * absolute path ({@link #getAbsolutePath()}).  Otherwise, this method
   * will delegate to {@link java.io.File#getCanonicalPath()}.
   */
  public String getCanonicalPath() throws IOException {
    return isURL ? getAbsolutePath() : file.getCanonicalPath();
  }

  /**
   * Returns the name of this file, i.e. the last name in the path name
   * sequence.
   *
   * @see java.io.File#getName()
   */
  public String getName() {
    if (isURL) {
      String name = url.getFile();
      name = name.substring(name.lastIndexOf("/") + 1);
      return name;
    }
    return file.getName();
  }

  /**
   * Returns the name of this file's parent directory, i.e. the path name prefix
   * and every name in the path name sequence except for the last.
   * If this file does not have a parent directory, then null is returned.
   *
   * @see java.io.File#getParent()
   */
  public String getParent() {
    if (isURL) {
      String absPath = getAbsolutePath();
      absPath = absPath.substring(0, absPath.lastIndexOf("/"));
      return absPath;
    }
    return file.getParent();
  }

  /* @see java.io.File#getParentFile() */
  public Location getParentFile() {
    return new Location(getParent());
  }

  /* @see java.io.File#getPath() */
  public String getPath() {
    return isURL ? url.getHost() + url.getPath() : file.getPath();
  }

  /**
   * Tests whether or not this path name is absolute.
   * If the path name is a URL, this method will always return true.
   *
   * @see java.io.File#isAbsolute()
   */
  public boolean isAbsolute() {
    return isURL ? true : file.isAbsolute();
  }

  /**
   * Returns true if this pathname exists and represents a directory.
   *
   * @see java.io.File#isDirectory()
   */
  public boolean isDirectory() {
    if (isURL) {
      String[] list = list();
      return list != null;
    }
    return file.isDirectory();
  }

  /**
   * Returns true if this pathname exists and represents a regular file.
   *
   * @see java.io.File#exists()
   */
  public boolean isFile() {
    return isURL ? (!isDirectory() && exists()) : file.isFile();
  }

  /**
   * Returns true if the pathname is 'hidden'.  This method will always
   * return false if the pathname corresponds to a URL.
   *
   * @see java.io.File#isHidden()
   */
  public boolean isHidden() {
    return isURL ? false : file.isHidden();
  }

  /**
   * Return the last modification time of this file, in milliseconds since
   * the UNIX epoch.
   * If the file does not exist, 0 is returned.
   *
   * @see java.io.File#lastModified()
   * @see java.net.URLConnection#getLastModified()
   */
  public long lastModified() {
    if (isURL) {
      try {
        return url.openConnection().getLastModified();
      }
      catch (IOException e) {
        return 0;
      }
    }
    return file.lastModified();
  }

  /**
   * @see java.io.File#length()
   * @see java.net.URLConnection#getContentLength()
   */
  public long length() {
    if (isURL) {
      try {
        return url.openConnection().getContentLength();
      }
      catch (IOException e) {
        return 0;
      }
    }
    return file.length();
  }

  /**
   * Return a list of file names in this directory.  Hidden files will be
   * included in the list.
   * If this is not a directory, return null.
   */
  public String[] list() {
    return list(false);
  }

  /**
   * Return a list of absolute files in this directory.  Hidden files will
   * be included in the list.
   * If this is not a directory, return null.
   */
  public Location[] listFiles() {
    String[] s = list();
    if (s == null) return null;
    Location[] f = new Location[s.length];
    for (int i=0; i<f.length; i++) {
      f[i] = new Location(getAbsolutePath(), s[i]);
      f[i] = f[i].getAbsoluteFile();
    }
    return f;
  }

  /**
   * Return the URL corresponding to this pathname.
   *
   * @see java.io.File#toURL()
   */
  public URL toURL() throws MalformedURLException {
    return isURL ? url : file.toURI().toURL();
  }

  /**
   * @see java.io.File#toString()
   * @see java.net.URL#toString()
   */
  public String toString() {
    return isURL ? url.toString() : file.toString();
  }

}
