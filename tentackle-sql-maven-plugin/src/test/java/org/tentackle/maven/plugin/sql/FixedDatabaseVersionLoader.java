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

import org.tentackle.sql.BackendInfo;

/**
 * Test-only {@link DatabaseVersionLoader} loaded by FQCN from
 * {@link BackendInfoParameterTest}. Must be a top-level class with a public no-arg
 * constructor: {@code BackendInfoParameter#loadMigrationHints} loads it via
 * {@code Class.forName(minVersion).getConstructor().newInstance()}, and
 * {@code StringHelper#isValidJavaClassName} rejects nested-class binary names (the "$").
 */
public class FixedDatabaseVersionLoader implements DatabaseVersionLoader {

  /** the version returned by every instance. */
  public static final String VERSION = "3.1.4";

  @Override
  public String getDatabaseVersion(AbstractSqlMojo mojo, BackendInfo backendInfo) {
    return VERSION;
  }

}
