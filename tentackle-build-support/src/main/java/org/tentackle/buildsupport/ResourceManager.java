/*
 * Tentackle - https://tentackle.org
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
 */


package org.tentackle.buildsupport;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.Reader;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.StringTokenizer;
import org.tentackle.common.Settings;


/**
 * A manager for resources generated during annotation processing.
 * <p>
 * The resources are files relative to a base directory, named by slash-separated paths.
 * Writers and readers are created lazily on first access, use the encoding configured by
 * {@link Settings}, and are cached until {@link #close()}. Missing parent directories are created.
 * The names of all accessed resources are available via {@link #getResourceNames()}, even after {@link #close()}.
 *
 * @author harald
 */
public class ResourceManager {

  private class Resource {

    private final String name;
    private File file;
    private PrintWriter writer;
    private Reader reader;

    private Resource(String name) {
      this.name = name;
    }

    private File getFile() {
      if (file == null) {
        StringTokenizer stok = new StringTokenizer(name, "/");
        File dir = null;
        file = location;
        while (stok.hasMoreTokens()) {
          dir = file;
          file = new File(dir, stok.nextToken());
        }
        if (dir != null) {
          dir.mkdirs();
        }
      }
      return file;
    }

    private PrintWriter getWriter() throws IOException {
      if (writer == null) {
        writer = new PrintWriter(
                    new BufferedWriter(
                      new OutputStreamWriter(
                        new FileOutputStream(getFile()), Settings.getEncodingCharset())));
      }
      return writer;
    }

    private Reader getReader() throws IOException {
      if (reader == null) {
        reader = new BufferedReader(
                  new InputStreamReader(
                    new FileInputStream(getFile()), Settings.getEncodingCharset()));
      }
      return reader;
    }

    private void close() throws IOException {
      if (reader != null) {
        reader.close();
        reader = null;
      }
      if (writer != null) {
        writer.close();
        writer = null;
      }
    }

    private boolean exists() {
      return getFile().exists();
    }
  }


  private final File location;                          // resource location
  private final Map<String,Resource> resourceMap;       // (generated) resources


  /**
   * Creates a resource manager.
   *
   * @param location the resource location
   */
  public ResourceManager(File location) {
    this.location = location;
    this.resourceMap = new HashMap<>();
  }

  /**
   * Gets the names of all resources accessed so far.
   *
   * @return the names, never null
   */
  public Set<String> getResourceNames() {
    return new HashSet<>(resourceMap.keySet());
  }


  /**
   * Gets the resource location.
   *
   * @return the location
   */
  public File getLocation() {
    return location;
  }


  /**
   * Gets the writer for a resource.<br>
   * Creates or truncates the file on first access.
   *
   * @param name the resource name
   * @return the writer
   * @throws IOException if writer could not be created
   */
  public PrintWriter getWriter(String name) throws IOException {
    return getResource(name).getWriter();
  }


  /**
   * Gets the reader for a resource.
   *
   * @param name the resource name
   * @return the reader
   * @throws IOException if reader could not be created
   */
  public Reader getReader(String name) throws IOException {
    return getResource(name).getReader();
  }


  /**
   * Returns whether given resource file exists.
   *
   * @param name the resource name
   * @return true if exists
   */
  public boolean exists(String name) {
    return getResource(name).exists();
  }


  /**
   * Closes all open readers and writers.
   *
   * @throws IOException if closing a resource failed
   */
  public void close() throws IOException {
    for (Resource resource: resourceMap.values()) {
      resource.close();
    }
  }


  private Resource getResource(String name) {
    return resourceMap.computeIfAbsent(name, Resource::new);
  }
}
