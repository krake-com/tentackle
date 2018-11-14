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

package org.tentackle.maven.wizard;

import org.apache.maven.project.MavenProject;

import java.io.File;
import java.util.List;

/**
 * Contains info about a java package.
 */
public class PackageInfo {

  private final String name;
  private final MavenProject project;
  private final File path;

  private List<PackageInfo> emptyDuplicates;

  /**
   * Creates a package info.
   *
   * @param name the java package name
   * @param project the maven project containing the package
   * @param path the directory path of the package
   */
  public PackageInfo(String name, MavenProject project, File path) {
    this.name = name;
    this.project = project;
    this.path = path;
  }

  /**
   * Gets the package name.
   *
   * @return the java package name
   */
  public String getName() {
    return name;
  }

  /**
   * Gets the maven project containing the package.
   *
   * @return the maven project
   */
  public MavenProject getProject() {
    return project;
  }

  /**
   * Gets the directory path of the package.
   *
   * @return the path
   */
  public File getPath() {
    return path;
  }

  /**
   * Determines whether this package contains any java files.<br>
   * The method is used to detect split packages which are forbidden in JPMS.
   *
   * @return true if not empty, false if no java files found
   */
  public boolean isContainingJavaFiles() {
    return path.list((dir, nam) -> nam.endsWith(Constants.JAVA_EXT)).length > 0;
  }

  /**
   * Gets the empty duplicates for this package info.<br>
   * If an empty package is found more than once in different modules, it cannot be used in profiles
   * because the generator cannot decide which one to use.
   *
   * @return the duplicates, null if none
   */
  public List<PackageInfo> getEmptyDuplicates() {
    return emptyDuplicates;
  }

  /**
   * Sets the duplicates.
   *
   * @param emptyDuplicates the duplicates
   */
  public void setEmptyDuplicates(List<PackageInfo> emptyDuplicates) {
    this.emptyDuplicates = emptyDuplicates;
  }

  @Override
  public String toString() {
    return "PackageInfo{" +
           "name='" + name + '\'' +
           ", project=" + project.getName() +
           ", path=" + path +
           '}';
  }
}
