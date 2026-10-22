/*
 * Tentackle - https://tentackle.org.
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
import org.apache.maven.settings.Server;
import org.apache.maven.settings.building.SettingsProblem;
import org.apache.maven.settings.crypto.DefaultSettingsDecryptionRequest;
import org.apache.maven.settings.crypto.SettingsDecryptionResult;
import org.apache.maven.shared.model.fileset.FileSet;

import org.tentackle.common.StringHelper;
import org.tentackle.maven.AbstractTentackleMojo;
import org.tentackle.sql.BackendInfo;
import org.tentackle.sql.BackendInfoFactory;

import java.lang.reflect.InvocationTargetException;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
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

  /** server ID from settings.xml. */
  public String serverId;

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
   * <li>a string holding a fixed version number, e.g. 3.1.0</li>
   * <li>a string starting with {@code SELECT} (case-insensitive), which is an SQL select returning
   * the current database version in the first column of the first row</li>
   * <li>the FQCN of a class with a no-args constructor implementing {@link DatabaseVersionLoader}. Notice that this class must
   * be added to the classpath via plugin dependencies.</li>
   * </ol>
   * The version is applied to the <em>directories</em> of the hint files via the {@link VersionFileFilter}.
   * If missing, all hint files are loaded.
   */
  public String minVersion;


  /**
   * Creates a backend info from this parameter.
   *
   * @param mojo the mojo
   * @return the backend info
   * @throws MojoExecutionException if credentials could not be determined from server ID
   */
  public BackendInfo createBackendInfo(AbstractTentackleMojo mojo) throws MojoExecutionException {
    if (schemaNames != null) {
      schemas = new ArrayList<>();
      StringTokenizer stok = new StringTokenizer(schemaNames, ",");
      while (stok.hasMoreTokens()) {
        schemas.add(stok.nextToken().trim());
      }
    }
    String[] schemaArray = null;
    if (schemas != null && !schemas.isEmpty()) {
      schemaArray = schemas.toArray(new String[0]);
    }

    if (serverId != null && !serverId.isEmpty()) {
      Server server = mojo.getSettings().getServer(serverId);
      if (server != null) {
        if (user != null || password != null) {
          mojo.getLogger().warn("replacing user/password from pom.xml by settings.xml for server id '{}'", serverId);
        }
        user = server.getUsername();
        SettingsDecryptionResult result = mojo.getSettingsDecrypter().decrypt(new DefaultSettingsDecryptionRequest(server));
        for (SettingsProblem problem : result.getProblems()) {
          StringBuilder msg = new StringBuilder();
          msg.append(problem.getMessage());
          if (!problem.getSource().isEmpty()) {
            msg.append(" at ").append(problem.getSource());
            if (problem.getLineNumber() > 0) {
              msg.append(':').append(problem.getLineNumber());
            }
          }
          if (problem.getSeverity() == SettingsProblem.Severity.ERROR || problem.getSeverity() == SettingsProblem.Severity.FATAL) {
            throw new MojoExecutionException("unable to decrypt password: " + msg);
          }
          else {
            mojo.getLogger().warn(msg.toString());
          }
        }
        password = result.getServer().getPassword();
      }
      else {
        throw new MojoExecutionException("no such server id in settings.xml: " + serverId);
      }
    }

    char[] passwd = password == null ? null : password.toCharArray();

    return BackendInfoFactory.getInstance().create(url, user, passwd, schemaArray);
  }


  /**
   * Loads the migration hints.<br>
   * Resolves the {@link #minVersion} first, if given.
   *
   * @param backendInfo the backend info
   * @param mojo the sql mojo
   * @param verbose true if verbose
   * @param resourceDirs resource dirs, null if none
   * @return the migration hints
   * @throws MojoExecutionException if failed
   */
  public MigrationHints loadMigrationHints(BackendInfo backendInfo, AbstractSqlMojo mojo, boolean verbose, List<String> resourceDirs) throws MojoExecutionException {
    MigrationHints hints = new MigrationHints();

    if (minVersion != null) {
      if (minVersion.toUpperCase(Locale.ROOT).startsWith("SELECT ")) {
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
      mojo.getLogger().debug("applying filter '{}'", minVersion);
    }
    else {
      mojo.getLogger().debug("no version filter");
    }

    hints.load(mojo, verbose, migrationHints, minVersion, resourceDirs);

    return hints;
  }

}
