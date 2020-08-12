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
 * Helper methods for file handling.
 *
 * @author harald
 */
public class FileHelper {

  /**
   * Creates an input stream from a resource.
   *
   * @param caller the caller class, null to determine via Stackwalker
   * @param name the resource name
   * @return the input stream
   * @throws IOException if reading the resources failed
   * @throws FileNotFoundException if no such resources found
   */
  public static InputStream createInputStream(Class<?> caller, String name) throws IOException {
    if (caller == null) {
      caller = StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE).getCallerClass();
    }
    InputStream is = caller.getResourceAsStream(name);
    if (is == null) {
      // try other variant
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
   * Loads properties from a resource.
   *
   * @param name the properties name, null if return empty properties
   * @param asResource true if load from the classpath, false if from filesystem
   * @return the properties, empty if filename is null
   * @throws IOException if reading the property file failed
   * @throws FileNotFoundException if no properties found
   */
  public static Properties loadProperties(String name, boolean asResource) throws IOException {
    Properties dbProps = new Properties();
    if (name != null) {
      if (name.indexOf('.') < 0) {
        name += Constants.PROPS_EXTENSION;
      }
      if (!name.startsWith("/")) {
        name = "/" + name;
      }
      try (InputStream is = asResource ? createInputStream(FileHelper.class, name) : new FileInputStream(name)) {
        dbProps.load(is);
      }
    }
    return dbProps;
  }


  /**
   * Loads properties.<br>
   * Tries to load from filesystem first, then from classpath.
   *
   * @param name the properties name, null if return empty properties
   * @return the properties, empty if filename is null
   * @throws IOException if reading the property file failed
   * @throws FileNotFoundException if no properties found
   */
  public static Properties loadProperties(String name) throws IOException {
    try {
      return loadProperties(name, false);
    }
    catch (FileNotFoundException e1) {
      // try as resource
      return loadProperties(name, true);
    }
  }


  /**
   * Copies a file.
   *
   * @param source the source file
   * @param dest the destination file
   * @throws IOException if some I/O error
   */
  public static void copy(File source, File dest) throws IOException {
    try (FileChannel sourceChannel = new FileInputStream(source).getChannel()) {
      try (FileChannel destChannel = new FileOutputStream(dest).getChannel()) {
        destChannel.transferFrom(sourceChannel, 0, sourceChannel.size());
      }
    }
  }

  /**
   * Copies all files from one directory to another.
   *
   * @param srcDir the source directory
   * @param dstDir the destination directory
   * @param recursive true if copy also subdirectories
   * @param filter optional file filter, null if none
   *
   * @return number of files copied
   * @throws MojoExecutionException if copy failed
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
            count += copyDirectory(file, subDir, recursive, filter);
          }
        }
      }
    }
    return count;
  }


  /**
   * Downloads data from a URL into memory.
   *
   * @param url the URL
   * @return the data as bytes
   * @throws IOException if failed
   */
  public static byte[] download(URL url) throws IOException {
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    try (BufferedInputStream in = new BufferedInputStream(url.openStream())){
      byte dataBuffer[] = new byte[1024];
      int bytesRead;
      while ((bytesRead = in.read(dataBuffer, 0, 1024)) != -1) {
        outputStream.write(dataBuffer, 0, bytesRead);
      }
      return outputStream.toByteArray();
    }
  }


  /**
   * Removes a directory and all its contents recursively.
   *
   * @param name the directory name
   * @throws IOException if failed
   */
  public static void removeDirectory(String name) throws IOException {
    java.nio.file.Path path = Paths.get(name);
    Files.walkFileTree(path, new SimpleFileVisitor<java.nio.file.Path>() {

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
   * Unzips a zip file.
   *
   * @param zipFile the zip file
   * @param destDir the destination directory
   * @param fileConsumer optional consumer to receive the file being unzipped
   * @throws IOException if failed
   */
  public static void unzip(File zipFile, File destDir, Consumer<File> fileConsumer) throws IOException {
    byte[] buffer = new byte[1024];
    Set<File> createdDirs = new HashSet<>();

    try (ZipInputStream zipStream = new ZipInputStream(new FileInputStream(zipFile))) {
      ZipEntry zipEntry;
      while ((zipEntry = zipStream.getNextEntry()) != null) {
        File destFile = new File(destDir, zipEntry.getName());
        if (fileConsumer != null) {
          fileConsumer.accept(destFile);
        }
        if (destFile.getCanonicalPath().startsWith(destDir.getCanonicalPath())) {   // prevent zip slip!
          if (!zipEntry.isDirectory()) {
            File dir = destFile.getParentFile();
            if (!createdDirs.contains(dir)) {
              dir.mkdirs();
              createdDirs.add(dir);
            }
            try (FileOutputStream outputStream = new FileOutputStream(destFile)) {
              int len;
              while ((len = zipStream.read(buffer)) > 0) {
                outputStream.write(buffer, 0, len);
              }
            }
          }
        }
        else {
          throw new IOException("zip slip detected for " + zipEntry.getName());
        }
      }
    }
  }

  private FileHelper() {}

}
