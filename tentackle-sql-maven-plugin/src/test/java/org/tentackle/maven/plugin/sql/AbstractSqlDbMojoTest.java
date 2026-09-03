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

import org.testng.annotations.AfterClass;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;

import org.tentackle.maven.plugin.sql.SqlTestSupport.RecordingLog;
import org.tentackle.sql.Backend;
import org.tentackle.sql.BackendFactory;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Base class for the goal tests that need a real database: {@code migrate}, {@code validate} and
 * {@code run}. Unlike {@code create}, these connect via {@code BackendInfo#connect()} - so, unlike
 * {@code tentackle-i18n-maven-plugin}'s {@code AbstractI18nMojoTest}, no PDO session is needed
 * here, just a plain JDBC connection kept open for the lifetime of the test class
 * ({@code DB_CLOSE_DELAY=-1}), matching {@code tentackle-sql}'s own {@code H2Test}.
 */
@SuppressWarnings({ "exports", "missing-explicit-ctor" })
public abstract class AbstractSqlDbMojoTest {

  /** the backend URL shared by every mojo run in a test method. */
  protected static final String URL = "jdbc:h2:mem:sqlplugin;DB_CLOSE_DELAY=-1";

  private static Connection connection;

  private File basedir;
  private File modelDir;
  private File sqlDir;
  private RecordingLog log;


  @BeforeClass(alwaysRun = true)
  public void openDatabase() throws SQLException {
    connection = DriverManager.getConnection(URL);
  }

  @AfterClass(alwaysRun = true)
  public void closeDatabase() throws SQLException {
    if (connection != null) {
      connection.close();
    }
  }

  @BeforeMethod(alwaysRun = true)
  public void setUpProject() throws SQLException {
    try (Statement stmt = connection.createStatement()) {
      stmt.execute("DROP ALL OBJECTS");
    }
    basedir = SqlTestSupport.createTempDir("sql-db-mojo");
    modelDir = SqlTestSupport.createDir(basedir, "model");
    sqlDir = SqlTestSupport.createDir(basedir, "sql");
    log = new RecordingLog();
  }

  @AfterMethod(alwaysRun = true)
  public void tearDownProject() {
    SqlTestSupport.deleteRecursively(basedir);
    basedir = null;
  }


  protected File getModelDir() {
    return modelDir;
  }

  protected File getSqlDir() {
    return sqlDir;
  }

  protected RecordingLog getLog() {
    return log;
  }

  /**
   * Executes a plain SQL statement against the shared database - used to establish the "existing"
   * schema a test migrates away from.
   *
   * @param sql the statement
   */
  protected void execute(String sql) {
    try (Statement stmt = connection.createStatement()) {
      stmt.execute(sql);
    }
    catch (SQLException ex) {
      throw new IllegalStateException("cannot execute " + sql, ex);
    }
  }

  /**
   * The container never boots, so no {@code @Parameter(defaultValue = ...)} is ever applied -
   * every default a test relies on has to be set explicitly, matching the mojo's own defaults.
   *
   * @param mojo the mojo
   * @param modelName the model name - unique per test so the shared {@code ModelManager}
   *        singleton starts with a fresh, empty model
   * @param <T> the mojo type
   * @return the mojo
   */
  protected <T extends AbstractSqlMojo> T configure(T mojo, String modelName) {
    mojo.setLog(log);
    SqlTestSupport.setField(mojo, "mavenProject", SqlTestSupport.createProject(basedir));
    SqlTestSupport.setField(mojo, "modelDir", modelDir);
    SqlTestSupport.setField(mojo, "sqlDir", sqlDir);
    SqlTestSupport.setField(mojo, "modelName", modelName);
    SqlTestSupport.setField(mojo, "checkReservedTables", true);
    SqlTestSupport.setField(mojo, "checkBackendSpecificNames", true);
    SqlTestSupport.setField(mojo, "generateHeaderComment", false);

    BackendInfoParameter backend = new BackendInfoParameter();
    backend.url = URL;
    SqlTestSupport.setField(mojo, "backends", java.util.List.of(backend));
    return mojo;
  }

  /**
   * Runs a mojo the way {@code execute()} would, minus the global logging handler (see
   * {@code AbstractTentackleMojo#installJavaLoggingHandler}, which would reset the whole test
   * JVM's logging config).
   *
   * @param mojo the mojo
   * @throws Exception whatever the goal threw
   */
  protected void run(AbstractSqlMojo mojo) throws Exception {
    mojo.prepareExecute();
    if (SqlTestSupport.callValidate(mojo)) {
      mojo.executeImpl();
      mojo.finishExecute();
    }
  }

  /**
   * Bootstraps the database schema from the model already written to {@link #getModelDir()},
   * so it matches the model byte-for-byte (columns, types, comments) - by running the real
   * {@code create} goal and executing its output through the backend's own
   * {@code ScriptRunner}, the same way {@code run} would. Hand-writing an equivalent
   * {@code CREATE TABLE} is unreliable: the migrator treats a missing column *comment* as a
   * change too, not just a missing column.
   *
   * @param modelName the model name to bootstrap the schema for
   * @throws Exception whatever the create goal or the script execution threw
   */
  protected void bootstrapSchemaFromModel(String modelName) throws Exception {
    CreateSqlMojo createMojo = configure(new CreateSqlMojo(), modelName);
    SqlTestSupport.setField(createMojo, "sqlFileName", "createmodel.sql");
    SqlTestSupport.setField(createMojo, "backendNames", "H2");
    SqlTestSupport.setField(createMojo, "createSchemas", true);
    run(createMojo);

    String sql = SqlTestSupport.readFile(new File(new File(getSqlDir(), "H2"), "createmodel.sql"));
    Backend backend = BackendFactory.getInstance().getBackendByName("H2");
    backend.createScriptRunner(connection).run(sql);
  }

}
