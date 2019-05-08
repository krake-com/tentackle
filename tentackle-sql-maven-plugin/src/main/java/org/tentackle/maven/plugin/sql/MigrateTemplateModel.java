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

import org.apache.maven.plugin.MojoExecutionException;

import org.tentackle.common.StringHelper;
import org.tentackle.sql.BackendInfo;

import java.util.HashMap;
import java.util.Map;

/**
 * Holds the template model for the migrate sql generator.
 * <p>
 * The model provides the following variables:
 * <ul>
 *   <li>url: the connection URL</li>
 *   <li>migrate: the names of all processed entities mapped to a boolean indicating whether migrated or not.</li>
 *   <li>all system-environment with the keys converted to camelCase.</li>
 *   <li>all maven-properties with the keys converted to camelCase.</li>
 * </ul>
 *
 * <pre>
 *   Example:
 *   &lt;#if migrate.User || migrate.UserGroup &gt;
 *   DROP VIEW xyzzy IF EXISTS;
 *   CREATE VIEW xyzzy ...
 * </pre>
 */
public class MigrateTemplateModel extends HashMap<String, Object> {

  private final MigrateSqlMojo mojo;

  /**
   * Creates the model.
   *
   * @param mojo the migrate mojo
   * @param backendInfo the backend info
   * @param migratedEntitiesMap set of migrated tables
   * @throws MojoExecutionException if module path could not generated
   */
  public MigrateTemplateModel(MigrateSqlMojo mojo, BackendInfo backendInfo, Map<String, Boolean> migratedEntitiesMap) throws MojoExecutionException {
    this.mojo = mojo;

    putValue("url", backendInfo.getUrl());
    put("migrate", migratedEntitiesMap);

    // all system properties in camelCase, e.g. "os.name" converts to "osName"
    for (Entry<Object, Object> entry : System.getProperties().entrySet()) {
      putValue(StringHelper.toCamelCase(entry.getKey().toString()), entry.getValue().toString());
    }

    // dto. for all maven properties
    for (Entry<Object, Object> entry : mojo.getMavenProject().getProperties().entrySet()) {
      putValue(StringHelper.toCamelCase(entry.getKey().toString()), entry.getValue().toString());
    }
  }

  private void putValue(String key, Object value) {
    String val = value == null ? "" : value.toString();
    put(key, val);
    mojo.getLog().debug("script variable " + key + " = \"" + val + "\"");
  }

}
