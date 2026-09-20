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
import org.testng.annotations.Test;

import org.tentackle.maven.plugin.sql.SqlTestSupport.RecordingLog;

import java.io.File;
import java.nio.file.Files;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;
import static org.tentackle.maven.plugin.sql.SqlTestSupport.callValidate;
import static org.tentackle.maven.plugin.sql.SqlTestSupport.createProject;
import static org.tentackle.maven.plugin.sql.SqlTestSupport.createTempDir;
import static org.tentackle.maven.plugin.sql.SqlTestSupport.deleteRecursively;
import static org.tentackle.maven.plugin.sql.SqlTestSupport.setField;

/**
 * Exercises {@link RevengMojo} end to end against a real H2 database: connection, table
 * lookup, output directory handling and the {@code overwrite} guard. The generated model
 * source's own correctness (attribute/index/relation shape) is covered in depth by
 * {@code org.tentackle.maven.plugin.sql.reveng.RevengModelWriterTest}; this test focuses on
 * the mojo's own orchestration.
 */
public class RevengMojoTest {

  private static final String URL = "jdbc:h2:mem:revengmojo;DB_CLOSE_DELAY=-1";

  private static Connection connection;

  private File basedir;
  private File outputDir;
  private RecordingLog log;

  @BeforeClass(alwaysRun = true)
  public void openDatabase() throws SQLException {
    connection = DriverManager.getConnection(URL, "sa", "");
    try (Statement stmt = connection.createStatement()) {
      stmt.execute("DROP ALL OBJECTS");
      stmt.execute("CREATE TABLE customer (id BIGINT NOT NULL PRIMARY KEY, name VARCHAR(60) NOT NULL)");
      stmt.execute("""
          CREATE TABLE order_item (
            id BIGINT NOT NULL PRIMARY KEY,
            customer_id BIGINT NOT NULL REFERENCES customer(id),
            quantity INT NOT NULL
          )
          """);
    }
  }

  @AfterClass(alwaysRun = true)
  public void closeDatabase() throws SQLException {
    if (connection != null) {
      connection.close();
    }
  }

  @BeforeMethod(alwaysRun = true)
  public void setUp() {
    basedir = createTempDir("reveng-mojo");
    outputDir = new File(basedir, "reveng-model");
    log = new RecordingLog();
  }

  @AfterMethod(alwaysRun = true)
  public void tearDown() {
    deleteRecursively(basedir);
  }

  private RevengMojo createMojo(String tables) {
    RevengMojo mojo = new RevengMojo();
    mojo.setLog(log);
    setField(mojo, "mavenProject", createProject(basedir));
    setField(mojo, "url", URL);
    setField(mojo, "user", "sa");
    setField(mojo, "password", "");
    setField(mojo, "tables", tables);
    setField(mojo, "outputDir", outputDir);
    setField(mojo, "startClassId", 500);
    setField(mojo, "integrity", "related");
    setField(mojo, "overwrite", false);
    return mojo;
  }

  private void run(RevengMojo mojo) throws Exception {
    assertTrue(callValidate(mojo), "validate() returned false");
    mojo.executeImpl();
  }


  @Test
  public void testReverseEngineersRequestedTablesInOrder() throws Exception {
    RevengMojo mojo = createMojo("customer,order_item");
    run(mojo);

    File customerFile = new File(outputDir, "Customer.map");
    File orderItemFile = new File(outputDir, "OrderItem.map");
    assertTrue(customerFile.isFile(), "missing " + customerFile);
    assertTrue(orderItemFile.isFile(), "missing " + orderItemFile);

    String customerSource = Files.readString(customerFile.toPath());
    String orderItemSource = Files.readString(orderItemFile.toPath());
    // consecutive class ids, in the order the tables were requested
    assertTrue(customerSource.contains("id := 500"));
    assertTrue(orderItemSource.contains("id := 501"));
    assertTrue(orderItemSource.contains("Customer:"));

    assertTrue(RecordingLog.contains(log.getInfoMessages(), "Customer.map"));
    assertTrue(RecordingLog.contains(log.getInfoMessages(), "tentackle-wizard:pdo -DbatchDir=" + outputDir));
  }

  @Test
  public void testUnknownTableFailsTheBuild() throws Exception {
    RevengMojo mojo = createMojo("customer,no_such_table");
    try {
      run(mojo);
      throw new AssertionError("expected a MojoFailureException");
    }
    catch (org.apache.maven.plugin.MojoFailureException expected) {
      assertTrue(expected.getMessage().contains("no_such_table"));
    }
    // must fail before writing anything, so a typo cannot produce a half-finished batch
    assertFalse(outputDir.exists());
  }

  @Test
  public void testExistingFileIsSkippedUnlessOverwriteIsSet() throws Exception {
    run(createMojo("customer"));
    File customerFile = new File(outputDir, "Customer.map");
    String firstVersion = Files.readString(customerFile.toPath());

    RecordingLog secondLog = new RecordingLog();
    RevengMojo mojo = createMojo("customer");
    mojo.setLog(secondLog);
    setField(mojo, "startClassId", 999); // different id -> would prove a rewrite happened
    run(mojo);

    assertEquals(Files.readString(customerFile.toPath()), firstVersion, "file must not have been rewritten");
    assertTrue(RecordingLog.contains(secondLog.getWarnMessages(), "already exists"));

    RevengMojo overwritingMojo = createMojo("customer");
    setField(overwritingMojo, "startClassId", 999);
    setField(overwritingMojo, "overwrite", true);
    run(overwritingMojo);
    assertTrue(Files.readString(customerFile.toPath()).contains("id := 999"));
  }

}
