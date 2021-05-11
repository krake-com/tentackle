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
 * Profile for PDOs.
 */
public class PdoProfile extends Profile {

  /**
   * The super pdo interface, default is PersistentDomainObject.
   */
  @Parameter
  private String pdoInterface;

  /**
   * The pdo interface package.
   */
  @Parameter(required = true)
  private String pdoPackage;
  private PackageInfo pdoPackageInfo;

  /**
   * First class ID to use.
   */
  @Parameter(required = true)
  private int minClassId;

  /**
   * Last class ID to use (optional).
   */
  @Parameter
  private int maxClassId;

  /**
   * Optional table prefix.
   */
  @Parameter
  private String tablePrefix;


  public String getPdoInterface() {
    return pdoInterface;
  }

  public String getPdoPackage() {
    return pdoPackage;
  }

  public int getMinClassId() {
    return minClassId;
  }

  public int getMaxClassId() {
    return maxClassId;
  }

  public PackageInfo getPdoPackageInfo() {
    return pdoPackageInfo;
  }

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
   * Updates the classid according to the model.
   *
   * @param minClassId the next class ID to use
   */
  public void setMinClassId(int minClassId) {
    this.minClassId = minClassId;
  }

  /**
   * Updates the max. classid according to the profiles.
   *
   * @param maxClassId the max class id
   */
  public void setMaxClassId(int maxClassId) {
    this.maxClassId = maxClassId;
  }

}
