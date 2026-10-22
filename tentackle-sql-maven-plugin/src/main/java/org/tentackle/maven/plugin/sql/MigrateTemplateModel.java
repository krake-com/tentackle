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

import org.tentackle.buildsupport.codegen.TemplateModel;
import org.tentackle.sql.BackendInfo;

import java.io.Serial;
import java.util.Map;

/**
 * Holds the template model for the migration sql generator.
 * <p>
 * The model provides the following variables:
 * <ul>
 *   <li>url: the connection URL</li>
 *   <li>migrate: the names of all processed entities mapped to a boolean indicating whether the entity's table
 *   needs migration or not.</li>
 *   <li>all system properties with the keys converted to camelCase.</li>
 *   <li>all maven properties with the keys converted to camelCase.</li>
 * </ul>
 *
 * <pre>
 *   Example:
 *   &lt;#if migrate.User || migrate.UserGroup &gt;
 *   DROP VIEW xyzzy IF EXISTS;
 *   CREATE VIEW xyzzy ...
 * </pre>
 */
public class MigrateTemplateModel extends TemplateModel {

  @Serial
  private static final long serialVersionUID = 1L;

  /**
   * Creates the model.
   *
   * @param mojo the migrate mojo
   * @param backendInfo the backend info
   * @param migratedEntitiesMap the processed entity names mapped to whether they need migration
   * @throws MojoExecutionException if the model could not be created
   */
  public MigrateTemplateModel(MigrateSqlMojo mojo, BackendInfo backendInfo, Map<String, Boolean> migratedEntitiesMap) throws MojoExecutionException {

    putValue("url", backendInfo.getUrl());
    put("migrate", migratedEntitiesMap);

    // all system properties in camelCase, e.g. "os.name" converts to "osName"
    addProperties(System.getProperties());

    // dto. for all maven properties
    addProperties(mojo.getMavenProject().getProperties());
  }

}
