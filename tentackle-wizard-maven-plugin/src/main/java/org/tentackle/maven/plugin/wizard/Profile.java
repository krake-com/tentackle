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

package org.tentackle.maven.plugin.wizard;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Parameter;

import java.util.Map;
import java.util.Objects;

/**
 * Base class for pdo- and operation profiles.
 */
public class Profile {

  /**
   * The unique profile name.
   */
  @Parameter(required = true)
  private String name;

  /**
   * The super domain interface, null if default.
   */
  @Parameter
  private String domainInterface;

  /**
   * The super persistence interface, null if default.
   */
  @Parameter
  private String persistenceInterface;

  /**
   * The super domain implementation, null if default.
   */
  @Parameter
  private String domainImplementation;

  /**
   * The super pseristence implemenation, null if default.
   */
  @Parameter
  private String persistenceImplementation;

  /**
   * The domain interface package.
   */
  @Parameter(required = true)
  private String domainPackage;
  private PackageInfo domainPackageInfo;

  /**
   * The persistence interface package.
   */
  @Parameter(required = true)
  private String persistencePackage;
  private PackageInfo persistencePackageInfo;

  /**
   * The domain implementation package.
   */
  @Parameter(required = true)
  private String domainImplPackage;
  private PackageInfo domainImplPackageInfo;

  /**
   * The persistence implementation package.
   */
  @Parameter(required = true)
  private String persistenceImplPackage;
  private PackageInfo persistenceImplPackageInfo;


  public String getName() {
    return name;
  }

  public String getDomainInterface() {
    return domainInterface;
  }

  public String getPersistenceInterface() {
    return persistenceInterface;
  }

  public String getDomainImplementation() {
    return domainImplementation;
  }

  public String getPersistenceImplementation() {
    return persistenceImplementation;
  }

  public String getDomainPackage() {
    return domainPackage;
  }

  public String getPersistencePackage() {
    return persistencePackage;
  }

  public String getDomainImplPackage() {
    return domainImplPackage;
  }

  public String getPersistenceImplPackage() {
    return persistenceImplPackage;
  }

  public PackageInfo getDomainPackageInfo() {
    return domainPackageInfo;
  }

  public PackageInfo getPersistencePackageInfo() {
    return persistencePackageInfo;
  }

  public PackageInfo getDomainImplPackageInfo() {
    return domainImplPackageInfo;
  }

  public PackageInfo getPersistenceImplPackageInfo() {
    return persistenceImplPackageInfo;
  }

  /**
   * Validates the profile.
   *
   * @param packageInfoMap the package infos
   * @throws MojoExecutionException if some package not assigned to a module
   */
  public void validate(Map<String, PackageInfo> packageInfoMap) throws MojoExecutionException {
    domainPackageInfo = getPackageInfo(packageInfoMap, domainPackage);
    persistencePackageInfo = getPackageInfo(packageInfoMap, persistencePackage);
    domainImplPackageInfo = getPackageInfo(packageInfoMap, domainImplPackage);
    persistenceImplPackageInfo = getPackageInfo(packageInfoMap, persistenceImplPackage);
  }

  /**
   * Determines the package info for a given package name.
   *
   * @param packageInfoMap the package info map
   * @param packageName the package name
   * @return the package info
   * @throws MojoExecutionException if no such mapping or more than one empty package in different modules
   */
  protected PackageInfo getPackageInfo(Map<String, PackageInfo> packageInfoMap, String packageName) throws MojoExecutionException {
    PackageInfo info = packageInfoMap.get(packageName);
    if (info == null) {
      throw new MojoExecutionException("cannot determine module for package " + packageName);
    }
    if (info.getEmptyDuplicates() != null) {    // !empty if != null
      StringBuilder buf = new StringBuilder();
      for (PackageInfo other: info.getEmptyDuplicates()) {
        buf.append("\nempty split package detected: ").append(info.getName()).
            append(" in ").append(info.getProject().getName()).
            append(" and ").append(other.getProject().getName());
      }
      buf.deleteCharAt(0);    // remove leading newline
      throw new MojoExecutionException(buf.toString());
    }
    return info;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    Profile profile = (Profile) o;
    return Objects.equals(name, profile.name);
  }

  @Override
  public int hashCode() {
    return Objects.hash(name);
  }

  @Override
  public String toString() {
    return name;
  }
}
