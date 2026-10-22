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

package org.tentackle.maven;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.List;

/**
 * Classloader that adds the project's classpath elements to the plugin's classloader.<br>
 * Delegates to the parent first, i.e. classes of the plugin take precedence over the project's ones.
 *
 * @author harald
 */
public class ProjectClassLoader extends URLClassLoader {

  /**
   * Creates a project classloader.
   *
   * @param classLoader the parent classloader, usually the plugin's classloader
   * @param classpathElements the classpath elements from maven, null if none
   * @throws MalformedURLException if malformed classpath element
   */
  public ProjectClassLoader(URLClassLoader classLoader, List<String> classpathElements) throws MalformedURLException {
    super(new URL[0], classLoader);
    if (classpathElements != null) {
      for (String classpathElement : classpathElements) {
        File classPathFile = new File(classpathElement);
        URL url = classPathFile.toURI().toURL();
        addURL(url);
      }
    }
  }

  /**
   * Adds a URL to the search path.<br>
   * Overridden to make it public.
   *
   * @param url the URL of a directory or jar file
   */
  @Override
  public void addURL(URL url) {
    super.addURL(url);
  }

}
