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

package org.tentackle.maven.sql;

import org.tentackle.common.ServiceFactory;

import java.io.File;


interface VersionFileFilterHolder {
  VersionFileFilter INSTANCE = ServiceFactory.createService(VersionFileFilter.class);
}

/**
 * Filters files according to their version.
 */
public interface VersionFileFilter {

  /**
   * The singleton.
   *
   * @return the singleton
   */
  public static VersionFileFilter getInstance() {
    return VersionFileFilterHolder.INSTANCE;
  }


  /**
   * Gets the name of the filter.
   *
   * @return the filter name
   */
  String getName();

  /**
   * Checks whether given migration hint file's version is logically greater or equal the given version.
   *
   * @param version the application's version
   * @param hintFile the migration hint file
   * @return true if hint applies to given version, false if too old
   */
  boolean isValid(String version, File hintFile);

}
