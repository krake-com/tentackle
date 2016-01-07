/**
 * Tentackle - http://www.tentackle.org
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
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.Reader;
import java.util.HashMap;
import java.util.Map;
import java.util.StringTokenizer;
import javax.tools.FileObject;

/**
 * A manager for {@link FileObject} resources.
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

    private File getFile() throws IOException {
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
        writer = new PrintWriter(new BufferedWriter(new FileWriter(getFile())));
      }
      return writer;
    }

    private Reader getReader() throws IOException {
      if (reader == null) {
        reader = new BufferedReader(new FileReader(getFile()));
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
  }


  private final AbstractTentackleProcessor processor;   // annotation processor
  private final File location;                          // resource location
  private final Map<String,Resource> resourceMap;       // (generated) resources


  /**
   * Creates a resource manager.
   *
   * @param processor the annotation processor
   * @param location the resource location
   */
  public ResourceManager(AbstractTentackleProcessor processor, File location) {
    this.processor = processor;
    this.location = location;
    this.resourceMap = new HashMap<>();
  }



  public PrintWriter getWriter(String name) throws IOException {
    return getResource(name).getWriter();
  }


  public Reader getReader(String name) throws IOException {
    return getResource(name).getReader();
  }


  public void close() throws IOException {
    for (Resource resource: resourceMap.values()) {
      resource.close();
    }
  }


  private Resource getResource(String name) {
    Resource resource = resourceMap.get(name);
    if (resource == null) {
      resource = new Resource(name);
      resourceMap.put(name, resource);
    }
    return resource;
  }
}
