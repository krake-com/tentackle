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

import org.apache.maven.plugins.annotations.Parameter;

/**
 * Base class for pdo- and operation profiles.
 */
public class Profile {

  /**
   * The profile's name.
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

  /**
   * The persistence interface package.
   */
  @Parameter(required = true)
  private String persistencePackage;

  /**
   * The domain implementation package.
   */
  @Parameter(required = true)
  private String domainImplPackage;

  /**
   * The persistence implementation package.
   */
  @Parameter(required = true)
  private String persistenceImplPackage;


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

  @Override
  public String toString() {
    return "name='" + name + '\'' +
           ", domainInterface='" + domainInterface + '\'' +
           ", persistenceInterface='" + persistenceInterface + '\'' +
           ", domainImplementation='" + domainImplementation + '\'' +
           ", persistenceImplementation='" + persistenceImplementation + '\'' +
           ", domainPackage='" + domainPackage + '\'' +
           ", persistencePackage='" + persistencePackage + '\'' +
           ", domainImplPackage='" + domainImplPackage + '\'' +
           ", persistenceImplPackage='" + persistenceImplPackage + '\'';
  }
}
