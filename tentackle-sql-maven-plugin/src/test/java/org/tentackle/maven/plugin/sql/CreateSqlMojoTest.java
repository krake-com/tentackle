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

import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import org.tentackle.maven.plugin.sql.SqlTestSupport.RecordingLog;

import java.io.File;

import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

/**
 * Tests the {@code create} goal.
 * <p>
 * {@code backendNames} lets {@code AbstractSqlMojo#validate} resolve a {@code Backend} without
 * connecting to anything, which is all {@code create} needs - it only generates text. Each test
 * uses its own {@code modelName} so the shared {@code ModelManager} singleton starts with a fresh,
 * empty {@link org.tentackle.model.Model} (entities loaded via {@code Model#loadFromDirectory} are
 * cached for the JVM's lifetime and never cleared automatically).
 */
@SuppressWarnings({ "exports", "missing-explicit-ctor" })
public class CreateSqlMojoTest {

  private File basedir;
  private File modelDir;
  private File sqlDir;
  private RecordingLog log;


  @BeforeMethod
  public void setUp() {
    basedir = SqlTestSupport.createTempDir("create-sql");
    modelDir = SqlTestSupport.createDir(basedir, "model");
    sqlDir = SqlTestSupport.createDir(basedir, "sql");
    log = new RecordingLog();
  }

  @AfterMethod
  public void tearDown() {
    SqlTestSupport.deleteRecursively(basedir);
  }


  @Test
  public void createsTableForARootEntity() throws Exception {
    SqlTestSupport.writeFile(modelDir, "Item.txt",
        "name := Item\n" +
        "id := 1\n" +
        "table := item\n" +
        "\n" +
        "## attributes\n" +
        "[root]\n" +
        "\n" +
        "String(30)      name            name            the item's name\n");

    CreateSqlMojo mojo = configure("createsTableForARootEntity");
    run(mojo);

    String sql = sql();
    assertTrue(sql.contains("CREATE TABLE item"), sql);
    assertTrue(sql.contains("name VARCHAR(30)"), sql);
    assertTrue(sql.contains("id BIGINT NOT NULL PRIMARY KEY"), sql);
    assertFalse(sql.contains("referential integrity"), sql);
  }

  /**
   * A reference from one entity to another produces a foreign key - scoped to only the entities
   * actually generated in this fileset.
   */
  @Test
  public void createsForeignKeyForAReference() throws Exception {
    SqlTestSupport.writeFile(modelDir, "Customer.txt",
        "name := Customer\n" +
        "id := 1\n" +
        "table := customer\n" +
        "integrity := related\n" +
        "\n" +
        "## attributes\n" +
        "[root]\n" +
        "\n" +
        "String(30)      name            name            the customer's name\n");
    SqlTestSupport.writeFile(modelDir, "Order.txt",
        "name := Order\n" +
        "id := 2\n" +
        "table := orders\n" +
        "\n" +
        "## attributes\n" +
        "[root]\n" +
        "\n" +
        "Long            customerId      customer_id     object ID of the customer\n" +
        "\n" +
        "## relations\n" +
        "Customer:\n" +
        "    select = lazy\n");

    CreateSqlMojo mojo = configure("createsForeignKeyForAReference");
    run(mojo);

    String sql = sql();
    assertTrue(sql.contains("CREATE TABLE customer"), sql);
    assertTrue(sql.contains("CREATE TABLE orders"), sql);
    assertTrue(sql.contains("referential integrity"), sql);
    assertTrue(sql.contains("FOREIGN KEY") && sql.contains("customer_id") && sql.contains("REFERENCES customer"), sql);
  }

  @Test
  public void createsIndexForAnIndexLine() throws Exception {
    SqlTestSupport.writeFile(modelDir, "Item.txt",
        "name := Item\n" +
        "id := 1\n" +
        "table := item\n" +
        "\n" +
        "## attributes\n" +
        "[root]\n" +
        "\n" +
        "String(30)      name            name            the item's name\n" +
        "\n" +
        "## indexes\n" +
        "index itemname := name\n");

    CreateSqlMojo mojo = configure("createsIndexForAnIndexLine");
    run(mojo);

    String sql = sql();
    assertTrue(sql.toUpperCase().contains("CREATE INDEX") && sql.contains("itemname"), sql);
  }

  @Test
  public void createsSequenceStatements() throws Exception {
    SqlTestSupport.writeFile(modelDir, "Item.txt",
        "name := Item\n" +
        "id := 1\n" +
        "table := item\n" +
        "\n" +
        "## attributes\n" +
        "[root]\n" +
        "\n" +
        "String(30)      name            name            the item's name\n");

    CreateSqlMojo mojo = configure("createsSequenceStatements");
    SequenceParameter sequence = new SequenceParameter();
    sequence.name = "item_sequence";
    sequence.comment = "the item id sequence";
    SqlTestSupport.setField(mojo, "sequences", java.util.List.of(sequence));
    run(mojo);

    String sql = sql();
    assertTrue(sql.toUpperCase().contains("CREATE SEQUENCE") && sql.contains("item_sequence"), sql);
  }

  /**
   * An entity with a schema name produces a {@code CREATE SCHEMA} statement, unless
   * {@code mapSchemas} folds the schema into the flat table name instead.
   */
  @Test
  public void createsSchemaUnlessSchemasAreMapped() throws Exception {
    SqlTestSupport.writeFile(modelDir, "Item.txt",
        "name := Item\n" +
        "id := 1\n" +
        "table := myschema.item\n" +
        "\n" +
        "## attributes\n" +
        "[root]\n" +
        "\n" +
        "String(30)      name            name            the item's name\n");

    CreateSqlMojo mojo = configure("createsSchemaUnlessSchemasAreMapped");
    run(mojo);

    String sql = sql();
    assertTrue(sql.toUpperCase().contains("CREATE SCHEMA") && sql.contains("myschema"), sql);
  }

  @Test
  public void mapSchemasFoldsTheSchemaIntoTheTableName() throws Exception {
    SqlTestSupport.writeFile(modelDir, "Item.txt",
        "name := Item\n" +
        "id := 1\n" +
        "table := myschema.item\n" +
        "\n" +
        "## attributes\n" +
        "[root]\n" +
        "\n" +
        "String(30)      name            name            the item's name\n");

    CreateSqlMojo mojo = configure("mapSchemasFoldsTheSchemaIntoTheTableName");
    SqlTestSupport.setField(mojo, "mapSchemas", true);
    run(mojo);

    String sql = sql();
    assertFalse(sql.toUpperCase().contains("CREATE SCHEMA"), sql);
  }

  @Test
  public void prependAndAppendFilesetsAreCopiedVerbatim() throws Exception {
    SqlTestSupport.writeFile(modelDir, "Item.txt",
        "name := Item\n" +
        "id := 1\n" +
        "table := item\n" +
        "\n" +
        "## attributes\n" +
        "[root]\n" +
        "\n" +
        "String(30)      name            name            the item's name\n");
    File prepend = SqlTestSupport.writeFile(basedir, "prepend.sql", "-- prepended snippet\n");
    File append = SqlTestSupport.writeFile(basedir, "append.sql", "-- appended snippet\n");

    CreateSqlMojo mojo = configure("prependAndAppendFilesetsAreCopiedVerbatim");
    SqlTestSupport.setField(mojo, "prependFilesets", java.util.List.of(fileSetFor(prepend)));
    SqlTestSupport.setField(mojo, "appendFilesets", java.util.List.of(fileSetFor(append)));
    run(mojo);

    String sql = sql();
    assertTrue(sql.indexOf("-- prepended snippet") < sql.indexOf("CREATE TABLE item"), sql);
    assertTrue(sql.indexOf("CREATE TABLE item") < sql.indexOf("-- appended snippet"), sql);
  }

  /**
   * A table name matching {@code alienTables} is reserved for other uses: the goal still
   * generates the SQL (useful to see what it would look like), but reports an error and fails
   * the build.
   */
  @Test
  public void alienTableIsReportedAsAnErrorAndFailsTheBuild() throws Exception {
    SqlTestSupport.writeFile(modelDir, "Item.txt",
        "name := Item\n" +
        "id := 1\n" +
        "table := item\n" +
        "\n" +
        "## attributes\n" +
        "[root]\n" +
        "\n" +
        "String(30)      name            name            the item's name\n");

    CreateSqlMojo mojo = configure("alienTableIsReportedAsAnErrorAndFailsTheBuild");
    SqlTestSupport.setField(mojo, "alienTables", java.util.List.of("item"));

    org.apache.maven.plugin.MojoFailureException ex = org.testng.Assert.expectThrows(
        org.apache.maven.plugin.MojoFailureException.class,
        () -> {
          mojo.prepareExecute();
          assertTrue(SqlTestSupport.callValidate(mojo));
          mojo.executeImpl();
        });
    assertTrue(ex.getMessage().contains("1"), ex.getMessage());
    assertTrue(RecordingLog.contains(log.getErrorMessages(), "'item' is reserved for other uses"), log.getErrorMessages().toString());
  }

  /**
   * A table name colliding with a table reserved by the Tentackle framework itself is reported
   * as an error too (checked via {@code checkReservedTables}, on by default).
   */
  @Test
  public void reservedTableNameIsReportedAsAnError() throws Exception {
    // "tokenlock" is one of the framework's own reserved tables, see MetaDataUtilities
    SqlTestSupport.writeFile(modelDir, "Item.txt",
        "name := Item\n" +
        "id := 1\n" +
        "table := tokenlock\n" +
        "\n" +
        "## attributes\n" +
        "[root]\n" +
        "\n" +
        "String(30)      name            name            the item's name\n");

    CreateSqlMojo mojo = configure("reservedTableNameIsReportedAsAnError");

    org.testng.Assert.expectThrows(org.apache.maven.plugin.MojoFailureException.class, () -> {
      mojo.prepareExecute();
      assertTrue(SqlTestSupport.callValidate(mojo));
      mojo.executeImpl();
    });
    assertTrue(RecordingLog.contains(log.getErrorMessages(), "reserved by the Tentackle framework"), log.getErrorMessages().toString());
  }


  private org.apache.maven.shared.model.fileset.FileSet fileSetFor(File file) {
    org.apache.maven.shared.model.fileset.FileSet fileSet = new org.apache.maven.shared.model.fileset.FileSet();
    fileSet.setDirectory(file.getParentFile().getPath());
    fileSet.addInclude(file.getName());
    return fileSet;
  }


  /**
   * The container never boots, so no {@code @Parameter(defaultValue = ...)} is ever applied -
   * every default the test relies on has to be set explicitly, matching the mojo's own defaults.
   */
  private CreateSqlMojo configure(String modelName) {
    CreateSqlMojo mojo = new CreateSqlMojo();
    mojo.setLog(log);
    SqlTestSupport.setField(mojo, "mavenProject", SqlTestSupport.createProject(basedir));
    SqlTestSupport.setField(mojo, "modelDir", modelDir);
    SqlTestSupport.setField(mojo, "sqlDir", sqlDir);
    SqlTestSupport.setField(mojo, "modelName", modelName);
    SqlTestSupport.setField(mojo, "backendNames", "H2");
    SqlTestSupport.setField(mojo, "sqlFileName", "createmodel.sql");
    SqlTestSupport.setField(mojo, "createSchemas", true);
    SqlTestSupport.setField(mojo, "checkReservedTables", true);
    SqlTestSupport.setField(mojo, "checkBackendSpecificNames", true);
    SqlTestSupport.setField(mojo, "generateHeaderComment", false);
    return mojo;
  }

  private void run(CreateSqlMojo mojo) throws Exception {
    mojo.prepareExecute();
    assertTrue(SqlTestSupport.callValidate(mojo));
    mojo.executeImpl();
    mojo.finishExecute();
  }

  private String sql() {
    return SqlTestSupport.readFile(new File(new File(sqlDir, "H2"), "createmodel.sql"));
  }

}
