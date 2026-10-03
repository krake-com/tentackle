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
 * Profile for PDOs.<br>
 * Adds the PDO package and super interface, the range of class IDs and an optional table prefix.
 */
public class PdoProfile extends Profile {

  /**
   * The super PDO interface, null for the default {@code PersistentDomainObject}.
   */
  @Parameter
  private String pdoInterface;

  /**
   * The package of the PDO interface.
   */
  @Parameter(required = true)
  private String pdoPackage;
  private PackageInfo pdoPackageInfo;

  /**
   * The first class ID of this profile.<br>
   * Must be at least 100, since lower IDs are reserved for Tentackle.
   * The ranges of all profiles must not overlap.
   */
  @Parameter(required = true)
  private int minClassId;

  /**
   * The last class ID of this profile (optional).<br>
   * If missing, the range ends right below the {@code minClassId} of the next higher profile.
   * The profile with the highest {@code minClassId} is unbounded if no {@code maxClassId} is given.
   */
  @Parameter
  private int maxClassId;

  /**
   * Optional table prefix, typically a schema name such as {@code md.}.<br>
   * Prepended to the table name entered in the interactive wizard.
   */
  @Parameter
  private String tablePrefix;


  /**
   * Gets the super PDO interface.
   *
   * @return the interface name, null for the default
   */
  public String getPdoInterface() {
    return pdoInterface;
  }

  /**
   * Gets the package of the PDO interface.
   *
   * @return the package name
   */
  public String getPdoPackage() {
    return pdoPackage;
  }

  /**
   * Gets the minimum class ID.<br>
   * Once the goal has determined the class IDs in use, this is the next free class ID.
   *
   * @return the minimum class ID
   */
  public int getMinClassId() {
    return minClassId;
  }

  /**
   * Gets the maximum class ID.<br>
   * Once the goal has determined the class ID ranges, this is the effective upper bound
   * ({@link Integer#MAX_VALUE} if unbounded).
   *
   * @return the maximum class ID, 0 if not configured and not determined yet
   */
  public int getMaxClassId() {
    return maxClassId;
  }

  /**
   * Gets the package info of the PDO interface package.
   *
   * @return the package info, null if not validated yet
   */
  public PackageInfo getPdoPackageInfo() {
    return pdoPackageInfo;
  }

  /**
   * Gets the table prefix.
   *
   * @return the prefix, null if none
   */
  public String getTablePrefix() {
    return tablePrefix;
  }

  @Override
  public void validate(Map<String, PackageInfo> packageInfoMap) throws MojoExecutionException {
    super.validate(packageInfoMap);
    pdoPackageInfo = getPackageInfo(packageInfoMap, pdoPackage);
    if (minClassId < 100) {
      throw new MojoExecutionException("class IDs < 100 are reserved for tentackle");
    }
  }

  /**
   * Sets the minimum class ID.<br>
   * Used to update the profile with the next free class ID.
   *
   * @param minClassId the next class ID to use
   */
  public void setMinClassId(int minClassId) {
    this.minClassId = minClassId;
  }

  /**
   * Sets the maximum class ID.<br>
   * Used to update the profile with the effective upper bound.
   *
   * @param maxClassId the max class ID
   */
  public void setMaxClassId(int maxClassId) {
    this.maxClassId = maxClassId;
  }

}
