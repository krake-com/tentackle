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

import org.apache.maven.project.MavenProject;

import java.io.File;
import java.util.List;

/**
 * Contains info about a java package.<br>
 * Created by {@link AbstractTentackleMojo#createPackageMap(boolean)}.
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
   * Determines whether a file is hidden.<br>
   * Unlike {@link File#isHidden()}, a name starting with a dot counts as hidden on all platforms,
   * not just on unix-like ones. Otherwise, files such as {@code .gitignore} would be treated
   * differently on Windows.
   *
   * @param file the file to check
   * @return true if hidden
   */
  public static boolean isHidden(File file) {
    return file.getName().startsWith(".") || file.isHidden();
  }

  /**
   * Determines whether this package contains any files.<br>
   * The method is used to detect split packages which are forbidden in JPMS.
   * <p>
   * Only the files directly within the package directory count, and hidden files are ignored,
   * just as when the package was collected (a directory holding nothing but a {@code .gitignore}
   * is an empty package).
   *
   * @return true if not empty, false if no files found
   */
  public boolean isContainingFiles() {
    // notice: the first argument of a FilenameFilter is the directory, not the file!
    String[] files = path.list((dir, nam) -> {
      File file = new File(dir, nam);
      return file.isFile() && !isHidden(file);
    });
    return files != null && files.length > 0;
  }


  /**
   * Gets the empty duplicates for this package info.<br>
   * If an empty package is found more than once in different modules, it cannot be used in the
   * wizard's profiles, because the generator cannot decide which one to use.
   *
   * @return the duplicates, null if none
   */
  public List<PackageInfo> getEmptyDuplicates() {
    return emptyDuplicates;
  }

  /**
   * Sets the empty duplicates.
   *
   * @param emptyDuplicates the duplicates, null if none
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
