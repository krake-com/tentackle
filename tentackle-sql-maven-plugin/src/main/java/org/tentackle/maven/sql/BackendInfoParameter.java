/*
 * Tentackle - http://www.tentackle.org.
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

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.shared.model.fileset.FileSet;

import org.tentackle.common.StringHelper;
import org.tentackle.sql.BackendInfo;

import java.lang.reflect.InvocationTargetException;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.StringTokenizer;

/**
 * Maven parameter to create a {@link BackendInfo}.
 *
 * <pre>
 * &lt;backends&gt;
 *   &lt;backend&gt;
 *     &lt;url&gt;jdbc:mysql://localhost/muz&lt;/url&gt;
 *     &lt;user&gt;muz&lt;/user&gt;
 *     &lt;password&gt;muz&lt;/password&gt;
 *     &lt;schemaNames&gt;muz, tx&lt;/schemaNames&gt;
 *   &lt;/backend&gt;
 * &lt;/backends&gt;
 * </pre>
 *
 * is equivalent to:
 *
 * <pre>
 * &lt;backends&gt;
 *   &lt;backend&gt;
 *     &lt;url&gt;jdbc:mysql://localhost/muz&lt;/url&gt;
 *     &lt;user&gt;muz&lt;/user&gt;
 *     &lt;password&gt;muz&lt;/password&gt;
 *     &lt;schemas&gt;
 *       &lt;schema&gt;muz&lt;/schema&gt;
 *       &lt;schema&gt;tx&lt;/schema&gt;
 *     &lt;/schemas&gt;
 *   &lt;/backend&gt;
 * &lt;/backends&gt;
 * </pre>
 *
 * @author harald
 */
public class BackendInfoParameter {

  /** the backend url. */
  public String url;

  /** the username. */
  public String user;

  /** the password. */
  public String password;

  /** allowed schemas. */
  public List<String> schemas;

  /** alternate way to set schemas as a comma-separated string. */
  public String schemaNames;

  /** optional migration hints. */
  public List<FileSet> migrationHints;

  /**
   * Optional minimum version number.<br>
   * This is usually the version of the database model.
   * <p>
   * There are 3 kinds of filters:
   * <ol>
   * <li>Strings holding a fixed version number, e.g. 3.1.0</li>
   * <li>String starting with 'SELECT' which is an SQL-select returning a version number (the current database version)</li>
   * <li>FQCN of a class with a no-args constructor implementing {@link DatabaseVersionLoader}. Notice that this class must
   * be added to the classpath via plugin dependencies.</li>
   * </ol>
   * If given, the version number will be applied to the <em>directories</em> of the hint-files.
   */
  public String minVersion;


  /**
   * Creates a backend info from this parameter.
   *
   * @param logger the maven logger
   * @return the backend info
   */
  public BackendInfo createBackendInfo(Log logger) {
    if (schemaNames != null) {
      schemas = new ArrayList<>();
      StringTokenizer stok = new StringTokenizer(schemaNames, ",");
      while (stok.hasMoreTokens()) {
        schemas.add(stok.nextToken().trim());
      }
    }
    char[] passwd = password == null ? null : password.toCharArray();
    String[] schemaArray = null;
    if (schemas != null && !schemas.isEmpty()) {
      schemaArray = schemas.toArray(new String[schemas.size()]);
    }
    return new BackendInfo(url, user, passwd, schemaArray);
  }


  /**
   * Load the migration hints.
   *
   * @param backendInfo the backend info
   * @param mojo the sql mojo
   * @param verbose true if verbose
   * @param resourceDirs resource dirs, null if none
   * @return the hints the migration hints
   * @throws MojoExecutionException if failed
   */
  public MigrationHints loadMigrationHints(BackendInfo backendInfo, AbstractSqlMojo mojo, boolean verbose, List<String> resourceDirs) throws MojoExecutionException {
    MigrationHints hints = new MigrationHints();

    if (minVersion != null) {
      if (minVersion.toUpperCase().startsWith("SELECT ")) {
        String effectiveFilter = null;
        try (Connection con = backendInfo.connect()) {
          try (Statement stmt = con.createStatement()) {
            ResultSet rs = stmt.executeQuery(minVersion);
            if (rs.next()) {
              effectiveFilter = rs.getString(1);
            }
          }
          if (effectiveFilter == null) {
            throw new MojoExecutionException(minVersion + " returned no results!");
          }
          minVersion = effectiveFilter;
        }
        catch (SQLException sx) {
          throw new MojoExecutionException("cannot retrieve filter from database", sx);
        }
      }
      else if (StringHelper.isFQCN(minVersion)) {
        try {
          Class<?> clazz = Class.forName(minVersion);
          if (DatabaseVersionLoader.class.isAssignableFrom(clazz)) {
            DatabaseVersionLoader versionLoader = (DatabaseVersionLoader) clazz.getConstructor().newInstance();
            minVersion = versionLoader.getDatabaseVersion(mojo, backendInfo);
          }
          else {
            throw new MojoExecutionException(clazz + " is not a DatabaseVersionLoader");
          }
        }
        catch (ClassNotFoundException | NoSuchMethodException | InstantiationException | IllegalAccessException | InvocationTargetException e) {
          throw new MojoExecutionException("cannot create DatabaseVersionLoader", e);
        }
      }
    }

    if (minVersion != null) {
      mojo.getLog().debug("applying filter '" + minVersion + "'");
    }
    else {
      mojo.getLog().debug("no version filter");
    }

    hints.load(migrationHints, minVersion, mojo.getLog(), verbose, resourceDirs);

    return hints;
  }

}
