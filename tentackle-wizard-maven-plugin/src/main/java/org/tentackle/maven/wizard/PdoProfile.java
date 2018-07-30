/*
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

package org.tentackle.maven.wizard;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Parameter;

import org.tentackle.maven.wizard.Profile;

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
  private int classId;


  public String getPdoInterface() {
    return pdoInterface;
  }

  public String getPdoPackage() {
    return pdoPackage;
  }

  public int getClassId() {
    return classId;
  }

  public PackageInfo getPdoPackageInfo() {
    return pdoPackageInfo;
  }

  @Override
  public void validate(Map<String, PackageInfo> packageInfoMap) throws MojoExecutionException {
    super.validate(packageInfoMap);
    pdoPackageInfo = getPackageInfo(packageInfoMap, pdoPackage);
  }

  @Override
  public String toString() {
    return "PdoProfile{" + super.toString() +
           ", pdoInterface='" + pdoInterface + '\'' +
           ", pdoPackage='" + pdoPackage + '\'' +
           ", classId=" + classId +
           '}';
  }
}
