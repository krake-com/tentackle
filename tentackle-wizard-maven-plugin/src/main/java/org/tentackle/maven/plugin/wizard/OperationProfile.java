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

/**
 * Profile for operations.
 */
public class OperationProfile extends Profile {

  /**
   * The super operation interface, null for the default {@code Operation}.<br>
   * The fully qualified name lets the templates import it.
   */
  @Parameter
  private String operationInterface;

  /**
   * The package of the operation interface.
   */
  @Parameter(required = true)
  private String operationPackage;
  private PackageInfo operationPackageInfo;

  /**
   * Gets the super operation interface.
   *
   * @return the interface name, null for the default
   */
  public String getOperationInterface() {
    return operationInterface;
  }

  /**
   * Gets the package of the operation interface.
   *
   * @return the package name
   */
  public String getOperationPackage() {
    return operationPackage;
  }

  /**
   * Gets the package info of the operation interface package.
   *
   * @return the package info, null if not validated yet
   */
  public PackageInfo getOperationPackageInfo() {
    return operationPackageInfo;
  }

  @Override
  public void validate(Map<String, PackageInfo> packageInfoMap) throws MojoExecutionException {
    super.validate(packageInfoMap);
    operationPackageInfo = getPackageInfo(packageInfoMap, operationPackage);
  }

}
