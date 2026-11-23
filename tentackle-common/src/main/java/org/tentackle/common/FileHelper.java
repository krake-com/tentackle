/*
 * Tentackle - https://tentackle.org.
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 *
 */

package org.tentackle.common;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileFilter;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.nio.channels.FileChannel;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.HashSet;
import java.util.Properties;
import java.util.Set;
import java.util.function.Consumer;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Helper methods for file and resource handling.
 *
 * @author harald
 */
public final class FileHelper {

  /**
   * Default buffer size for unzipping/downloading.<br>
   * The size is not critical for Unix systems, but Windows works best with 1M on CIFS shares.
   */
  private static final int DEFAULT_BUFFER_SIZE = 1024 * 1024;

  /**
   * Default maximum number of entries (files and directories) extracted by {@link #unzip(File, File, Consumer)}.
   */
  public static final int DEFAULT_MAX_ZIP_ENTRIES = 100_000;

  /**
   * Default maximum ratio of the total uncompressed size to the size of the zip file.
   */
  public static final int DEFAULT_MAX_ZIP_RATIO = 100;

  /**
   * Lower bound of the default maximum uncompressed size: 64 MiB.<br>
   * Applies to small zip files with highly compressible contents.
   */
  public static final long MIN_MAX_ZIP_SIZE = 64L * 1024 * 1024;

  /**
   * Default maximum size of data downloaded into memory by {@link #download(URL)}: 256 MiB.
   */
  public static final long DEFAULT_MAX_DOWNLOAD_SIZE = 256L * 1024 * 1024;

  /**
   * The maximum size of a byte array.
   */
  private static final int MAX_ARRAY_SIZE = Integer.MAX_VALUE - 8;



  /**
   * Creates an input stream from a resource.
   * <p>
   * The resource is looked up in the following order:
   * <ol>
   * <li>via {@link Class#getResourceAsStream(String)} of the caller, i.e., relative names are resolved
   * against the caller's package</li>
   * <li>via the context classloader (the name is taken as an absolute name without a leading slash)</li>
   * <li>via the unnamed module of the caller's classloader</li>
   * </ol>
   *
   * @param caller the caller class, null to determine via {@link StackWalker}
   * @param name the resource name
   * @return the buffered input stream
   * @throws IOException if reading the resource failed
   * @throws FileNotFoundException if no such resource found
   */
  public static InputStream createInputStream(Class<?> caller, String name) throws IOException {
    if (caller == null) {
      caller = StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE).getCallerClass();
    }
    InputStream is = caller.getResourceAsStream(name);
    if (is == null) {
      // try another variant
      is = Thread.currentThread().getContextClassLoader().getResourceAsStream(name);
      if (is == null) {
        Module module = caller.getClassLoader().getUnnamedModule();
        if (module != null) {
          is = module.getResourceAsStream(name);
        }
        if (is == null) {
          throw new FileNotFoundException("no such resource: " + name);
        }
      }
    }
    is = new BufferedInputStream(is);
    return is;
  }

  /**
   * Loads properties from a file or resource.<br>
   * If the name contains no dot, the extension {@code .properties} is appended.
   * Resource names are always taken as absolute.
   *
   * @param name the {@code Properties} name
   * @param asResource true to load from the classpath, false if from filesystem
   * @param properties the properties to load into
   * @throws IOException if reading the property file failed
   * @throws FileNotFoundException if no properties found
   */
  public static void loadProperties(String name, boolean asResource, Properties properties) throws IOException {
    if (name.indexOf('.') < 0) {
      name += Constants.PROPS_EXTENSION;
    }
    if (asResource && !name.startsWith("/")) {
      name = "/" + name;
    }
    try (InputStream is = asResource ? createInputStream(FileHelper.class, name) : new FileInputStream(name)) {
      properties.load(is);
    }
  }

  /**
   * Loads properties.<br>
   * Tries to load from filesystem first, then from the class path
   * (see {@link #loadProperties(String, boolean, Properties)}).
   * The name of the returned properties is set to the given name (see {@link EncryptedProperties#setName(String)}).
   *
   * @param name the {@code Properties} name, null if return empty properties
   * @return the properties, empty if the file name is null
   * @throws IOException if reading the property file failed
   * @throws FileNotFoundException if no properties found
   */
  public static EncryptedProperties loadProperties(String name) throws IOException {
    EncryptedProperties properties = new EncryptedProperties();
    if (name != null) {
      properties.setName(name);
      try {
        loadProperties(name, false, properties);
      }
      catch (FileNotFoundException e1) {
        // try as resource
        loadProperties(name, true, properties);
      }
    }
    return properties;
  }

  /**
   * Copies a file.
   *
   * @param source the source file
   * @param dest the destination file
   * @throws IOException if an I/O error occurred
   */
  public static void copy(File source, File dest) throws IOException {
    try (FileInputStream is = new FileInputStream(source)) {
      try (FileChannel sourceChannel = is.getChannel()) {
        try (FileOutputStream os = new FileOutputStream(dest)) {
          try (FileChannel destChannel = os.getChannel()) {
            destChannel.transferFrom(sourceChannel, 0, sourceChannel.size());
          }
        }
      }
    }
  }

  /**
   * Copies all files from one directory to another.<br>
   * The destination directory is created if necessary.
   *
   * @param srcDir the source directory
   * @param dstDir the destination directory
   * @param recursive true to copy subdirectories as well
   * @param filter optional file filter, null if none. Applies to files and directories.
   *
   * @return number of files copied
   * @throws IOException if copy failed
   */
  public static int copyDirectory(File srcDir, File dstDir, boolean recursive, FileFilter filter) throws IOException {
    int count = 0;
    File[] files = srcDir.listFiles();
    if (files != null) {
      boolean dstExists = dstDir.isDirectory();
      for (File file : files) {
        if (filter == null || filter.accept(file)) {
          if (file.isFile()) {
            if (!dstExists) {
              dstExists = dstDir.mkdirs();
            }
            File dest = new File(dstDir, file.getName());
            FileHelper.copy(file, dest);
            ++count;
          }
          else if (recursive && file.isDirectory()) {
            File subDir = new File(dstDir, file.getName());
            subDir.mkdir();
            count += copyDirectory(file, subDir, true, filter);
          }
        }
      }
    }
    return count;
  }

  /**
   * Downloads data from a URL into memory using a default buffer size and the default size limit.
   *
   * @param url the URL
   * @return the data as bytes
   * @throws IOException if failed or the data exceeds {@link #DEFAULT_MAX_DOWNLOAD_SIZE}
   * @see #download(URL, int, long)
   */
  public static byte[] download(URL url) throws IOException {
    return download(url, DEFAULT_BUFFER_SIZE);
  }

  /**
   * Downloads data from a URL into memory using the default size limit.
   *
   * @param url the URL
   * @param bufferSize the buffer size
   * @return the data as bytes
   * @throws IOException if failed or the data exceeds {@link #DEFAULT_MAX_DOWNLOAD_SIZE}
   * @see #download(URL, int, long)
   */
  public static byte[] download(URL url, int bufferSize) throws IOException {
    return download(url, bufferSize, DEFAULT_MAX_DOWNLOAD_SIZE);
  }

  /**
   * Downloads data from a URL into memory.
   * <p>
   * Since the data is held in memory, its size is limited. If the connection announces a content length
   * exceeding the limit, the download fails before any data is read. Otherwise, the bytes actually read are
   * counted, so a missing or wrong content length cannot bypass the limit.
   *
   * @param url the URL
   * @param bufferSize the buffer size
   * @param maxSize the maximum size in bytes, &le; 0 for the maximum size of a byte array
   * @return the data as bytes
   * @throws IOException if failed or the data exceeds the maximum size
   */
  public static byte[] download(URL url, int bufferSize, long maxSize) throws IOException {
    long limit = maxSize <= 0 ? MAX_ARRAY_SIZE : Math.min(maxSize, MAX_ARRAY_SIZE);
    URLConnection connection = url.openConnection();
    long contentLength = connection.getContentLengthLong();
    if (contentLength > limit) {
      throw new IOException("download of " + url + " exceeds max. size (" + contentLength + " > " + limit + " bytes)");
    }
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream(contentLength > 0 ? (int) contentLength : 32);
    try (BufferedInputStream in = new BufferedInputStream(connection.getInputStream())) {
      byte[] dataBuffer = new byte[bufferSize];
      long total = 0;
      int bytesRead;
      while ((bytesRead = in.read(dataBuffer, 0, bufferSize)) != -1) {
        total += bytesRead;
        if (total > limit) {
          throw new IOException("download of " + url + " exceeds max. size (" + limit + " bytes)");
        }
        outputStream.write(dataBuffer, 0, bytesRead);
      }
      return outputStream.toByteArray();
    }
  }

  /**
   * Opens an input stream for a URL without using the JVM-wide URL cache.<br>
   * For {@code jar:} URLs, the JDK shares a cached {@link java.util.jar.JarFile} among all classloaders.
   * If one of them is closed, for example, a Maven class realm disposed by a concurrent build thread,
   * the cached file is closed as well, and streams opened via {@link URL#openStream()} by others fail.
   * This is the same approach as in {@link java.util.ServiceLoader}.
   *
   * @param url the URL
   * @return the input stream
   * @throws IOException if failed
   */
  public static InputStream openStreamUncached(URL url) throws IOException {
    URLConnection connection = url.openConnection();
    connection.setUseCaches(false);
    return connection.getInputStream();
  }

  /**
   * Removes a directory and all its contents recursively.
   *
   * @param name the directory name
   * @throws IOException if failed
   */
  public static void removeDirectory(String name) throws IOException {
    java.nio.file.Path path = Paths.get(name);
    Files.walkFileTree(path, new SimpleFileVisitor<>() {

      @Override
      public FileVisitResult visitFile(java.nio.file.Path file, BasicFileAttributes attrs) throws IOException {
        Files.delete(file);
        return FileVisitResult.CONTINUE;
      }

      @Override
      public FileVisitResult postVisitDirectory(java.nio.file.Path dir, IOException exc) throws IOException {
        Files.delete(dir);
        return FileVisitResult.CONTINUE;
      }
    });
  }

  /**
   * Unzips a zip file using a default buffer size and the default limits.
   *
   * @param zipFile the zip file
   * @param destDir the destination directory
   * @param fileConsumer optional consumer to receive the file being unzipped
   * @throws IOException if failed
   * @see #unzip(File, File, Consumer, int, int, long)
   */
  public static void unzip(File zipFile, File destDir, Consumer<File> fileConsumer) throws IOException {
    unzip(zipFile, destDir, fileConsumer, DEFAULT_BUFFER_SIZE);
  }

  /**
   * Unzips a zip file using the default limits.<br>
   * At most {@link #DEFAULT_MAX_ZIP_ENTRIES} entries are extracted, and the total uncompressed size
   * is limited to {@link #DEFAULT_MAX_ZIP_RATIO} times the size of the zip file, but at least
   * {@link #MIN_MAX_ZIP_SIZE}.
   *
   * @param zipFile the zip file
   * @param destDir the destination directory
   * @param fileConsumer optional consumer to receive the file being unzipped
   * @param bufferSize the buffer size
   * @throws IOException if failed, an entry would be extracted outside the destination directory, or a limit was exceeded
   * @see #unzip(File, File, Consumer, int, int, long)
   */
  public static void unzip(File zipFile, File destDir, Consumer<File> fileConsumer, int bufferSize) throws IOException {
    unzip(zipFile, destDir, fileConsumer, bufferSize, DEFAULT_MAX_ZIP_ENTRIES, defaultMaxZipSize(zipFile.length()));
  }

  /**
   * Unzips a zip file.
   * <p>
   * Each entry is verified to resolve to a location inside the destination directory
   * before anything is written ("zip slip" protection). The consumer, if given, is invoked
   * for every verified entry, directories included, before the entry is extracted.
   * <p>
   * The number of entries and the total uncompressed size are limited ("zip bomb" protection).
   * The size counts the bytes actually decompressed, not the sizes declared in the entry headers.
   * Files extracted before a limit is exceeded are left in place.
   * <p>
   * The modification time of each extracted file is restored from its entry, if the entry carries one.
   * This matters for files whose timestamp is validated, such as the jars recorded in an AOT cache.
   *
   * @param zipFile the zip file
   * @param destDir the destination directory
   * @param fileConsumer optional consumer to receive the file being unzipped
   * @param bufferSize the buffer size
   * @param maxEntries the maximum number of entries, files and directories, &le; 0 if unlimited
   * @param maxSize the maximum total uncompressed size in bytes, &le; 0 if unlimited
   * @throws IOException if failed, an entry would be extracted outside the destination directory, or a limit was exceeded
   */
  public static void unzip(File zipFile, File destDir, Consumer<File> fileConsumer, int bufferSize,
                           int maxEntries, long maxSize) throws IOException {
    byte[] buffer = new byte[bufferSize];
    Set<File> createdDirs = new HashSet<>();
    String destPath = destDir.getCanonicalPath();
    String destPrefix = destPath.endsWith(File.separator) ? destPath : destPath + File.separator;
    int entryCount = 0;
    long totalSize = 0;

    try (ZipInputStream zipStream = new ZipInputStream(new FileInputStream(zipFile))) {
      ZipEntry zipEntry;
      while ((zipEntry = zipStream.getNextEntry()) != null) {
        if (maxEntries > 0 && ++entryCount > maxEntries) {
          throw new IOException("zip exceeds max. number of entries (" + maxEntries + ")");
        }
        File destFile = new File(destDir, zipEntry.getName());
        String path = destFile.getCanonicalPath();
        if (!path.equals(destPath) && !path.startsWith(destPrefix)) {   // prevent zip slip!
          throw new IOException("zip slip detected for " + zipEntry.getName());
        }
        if (fileConsumer != null) {
          fileConsumer.accept(destFile);
        }
        File dir = zipEntry.isDirectory() ? destFile : destFile.getParentFile();
        if (createdDirs.add(dir)) {
          dir.mkdirs();
        }
        if (!zipEntry.isDirectory()) {
          try (FileOutputStream outputStream = new FileOutputStream(destFile)) {
            int len;
            while ((len = zipStream.read(buffer)) > 0) {
              totalSize += len;
              if (maxSize > 0 && totalSize > maxSize) {
                throw new IOException("zip exceeds max. uncompressed size (" + maxSize + " bytes)");
              }
              outputStream.write(buffer, 0, len);
            }
          }
          long time = zipEntry.getTime();
          if (time != -1) {
            destFile.setLastModified(time);
          }
        }
      }
    }
  }


  /**
   * Computes the default maximum uncompressed size for a zip file.
   *
   * @param zipLength the size of the zip file in bytes
   * @return {@link #DEFAULT_MAX_ZIP_RATIO} times the size, at least {@link #MIN_MAX_ZIP_SIZE}, saturated at {@link Long#MAX_VALUE}
   */
  static long defaultMaxZipSize(long zipLength) {
    long size;
    try {
      size = Math.multiplyExact(zipLength, (long) DEFAULT_MAX_ZIP_RATIO);
    }
    catch (ArithmeticException ax) {
      size = Long.MAX_VALUE;
    }
    return Math.max(MIN_MAX_ZIP_SIZE, size);
  }


  private FileHelper() {}

}
