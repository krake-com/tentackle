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

import org.tentackle.maven.PackageInfo;

import java.util.Map;
import java.util.Objects;

/**
 * Base class for PDO and operation profiles.<br>
 * A profile describes a group of objects, such as master data or transaction data,
 * by the target packages of the generated files and the super types they extend.
 * Each package must map to exactly one module of the reactor.
 */
public class Profile {

  /**
   * The unique profile name.
   */
  @Parameter(required = true)
  private String name;

  /**
   * The super domain interface, null for the default.<br>
   * The fully qualified name lets the templates import it.
   */
  @Parameter
  private String domainInterface;

  /**
   * The super persistence interface, null for default.<br>
   * The fully qualified name lets the templates import it.
   */
  @Parameter
  private String persistenceInterface;

  /**
   * The super domain implementation, null for the default.<br>
   * The fully qualified name lets the templates import it.
   */
  @Parameter
  private String domainImplementation;

  /**
   * The super persistence implementation, null for the default.<br>
   * The fully qualified name lets the templates import it.
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


  /**
   * Gets the unique profile name.
   *
   * @return the name
   */
  public String getName() {
    return name;
  }

  /**
   * Gets the super domain interface.
   *
   * @return the interface name, null for the default
   */
  public String getDomainInterface() {
    return domainInterface;
  }

  /**
   * Gets the super persistence interface.
   *
   * @return the interface name, null for the default
   */
  public String getPersistenceInterface() {
    return persistenceInterface;
  }

  /**
   * Gets the super domain implementation.
   *
   * @return the class name, null for the default
   */
  public String getDomainImplementation() {
    return domainImplementation;
  }

  /**
   * Gets the super persistence implementation.
   *
   * @return the class name, null for the default
   */
  public String getPersistenceImplementation() {
    return persistenceImplementation;
  }

  /**
   * Gets the package of the domain interface.
   *
   * @return the package name
   */
  public String getDomainPackage() {
    return domainPackage;
  }

  /**
   * Gets the package of the persistence interface.
   *
   * @return the package name
   */
  public String getPersistencePackage() {
    return persistencePackage;
  }

  /**
   * Gets the package of the domain implementation.
   *
   * @return the package name
   */
  public String getDomainImplPackage() {
    return domainImplPackage;
  }

  /**
   * Gets the package of the persistence implementation.
   *
   * @return the package name
   */
  public String getPersistenceImplPackage() {
    return persistenceImplPackage;
  }

  /**
   * Gets the package info of the domain interface package.
   *
   * @return the package info, null if not validated yet
   */
  public PackageInfo getDomainPackageInfo() {
    return domainPackageInfo;
  }

  /**
   * Gets the package info of the persistence interface package.
   *
   * @return the package info, null if not validated yet
   */
  public PackageInfo getPersistencePackageInfo() {
    return persistencePackageInfo;
  }

  /**
   * Gets the package info of the domain implementation package.
   *
   * @return the package info, null if not validated yet
   */
  public PackageInfo getDomainImplPackageInfo() {
    return domainImplPackageInfo;
  }

  /**
   * Gets the package info of the persistence implementation package.
   *
   * @return the package info, null if not validated yet
   */
  public PackageInfo getPersistenceImplPackageInfo() {
    return persistenceImplPackageInfo;
  }

  /**
   * Validates the profile and determines the modules of its packages.
   *
   * @param packageInfoMap the package infos
   * @throws MojoExecutionException if a package is not assigned to exactly one module
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
