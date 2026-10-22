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

package org.tentackle.maven.plugin.sql;

import org.tentackle.common.ServiceFactory;

import java.io.File;


interface VersionFileFilterHolder {
  VersionFileFilter INSTANCE = ServiceFactory.createService(
    VersionFileFilter.class, SemanticVersionFileFilter.class, false);
}


/**
 * Filters migration hint files according to their version.<br>
 * The default implementation is {@link SemanticVersionFileFilter}. It can be replaced by
 * an application-specific {@code @Service(VersionFileFilter.class)} on the plugin's classpath.
 */
public interface VersionFileFilter {

  /**
   * The singleton.
   *
   * @return the singleton
   */
  static VersionFileFilter getInstance() {
    return VersionFileFilterHolder.INSTANCE;
  }


  /**
   * Gets the name of the filter.
   *
   * @return the filter name
   */
  String getName();

  /**
   * Checks whether the given migration hint file applies to the given database version.<br>
   * Usually, a hint file applies if its version is greater than the database version.
   *
   * @param version the current database version, null if unknown (no filtering)
   * @param hintFile the migration hint file
   * @return true if the hint applies, false if too old
   */
  boolean isValid(String version, File hintFile);

}
