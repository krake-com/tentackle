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

package org.tentackle.sql.maven;

import java.util.ArrayList;
import java.util.List;
import java.util.StringTokenizer;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.shared.model.fileset.FileSet;
import org.tentackle.sql.BackendInfo;

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

  /** the jdbc url. */
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
   * Creates a backend info from this parameter.
   *
   * @return the backend info
   */
  public BackendInfo createBackendInfo() {
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
   * @param logger the maven logger
   * @param verbose true if verbose
   * @param resourceDirs resource dirs, null if none
   * @return the hints the migration hints
   * @throws MojoExecutionException if failed
   */
  public MigrationHints loadMigrationHints(Log logger, boolean verbose, List<String> resourceDirs) throws MojoExecutionException {
    MigrationHints hints = new MigrationHints();
    hints.load(migrationHints, logger, verbose, resourceDirs);
    return hints;
  }

}
