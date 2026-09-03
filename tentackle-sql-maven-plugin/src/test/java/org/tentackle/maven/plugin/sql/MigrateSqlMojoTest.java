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

import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.shared.model.fileset.FileSet;

import org.testng.annotations.Test;

import org.tentackle.maven.plugin.sql.SqlTestSupport.RecordingLog;

import java.io.File;
import java.util.List;

import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.expectThrows;

/**
 * Tests the {@code migrate} goal end to end against a real in-memory H2 database.
 */
@SuppressWarnings({ "exports", "missing-explicit-ctor" })
public class MigrateSqlMojoTest extends AbstractSqlDbMojoTest {

  private static final String ITEM_MODEL =
      "name := Item\n" +
      "id := 1\n" +
      "table := item\n" +
      "\n" +
      "## attributes\n" +
      "[root]\n" +
      "\n" +
      "String(30)      name            name            the item's name\n";


  @Test
  public void reportsNoChangesWhenTheDatabaseMatchesTheModel() throws Exception {
    String modelName = "reportsNoChangesWhenTheDatabaseMatchesTheModel";
    SqlTestSupport.writeFile(getModelDir(), "Item.txt", ITEM_MODEL);
    bootstrapSchemaFromModel(modelName);

    MigrateSqlMojo mojo = configure(modelName);
    run(mojo);

    assertTrue(RecordingLog.contains(getLog().getInfoMessages(), "database meta data matches object model"),
               getLog().getInfoMessages().toString());
  }

  @Test
  public void reportsChangesWhenAColumnIsMissing() throws Exception {
    SqlTestSupport.writeFile(getModelDir(), "Item.txt",
        "name := Item\n" +
        "id := 1\n" +
        "table := item\n" +
        "\n" +
        "## attributes\n" +
        "[root]\n" +
        "\n" +
        "String(30)      name            name            the item's name\n" +
        "String(100)     description     description     the item's description\n");
    // the existing table lacks the "description" column the model now declares
    execute("CREATE TABLE item (name VARCHAR(30), id BIGINT NOT NULL PRIMARY KEY, serial BIGINT NOT NULL DEFAULT 1)");

    MigrateSqlMojo mojo = configure("reportsChangesWhenAColumnIsMissing");
    run(mojo);

    assertTrue(RecordingLog.contains(getLog().getWarnMessages(), "database meta data differs from object model"),
               getLog().getWarnMessages().toString());
    String sql = SqlTestSupport.readFile(new File(new File(getSqlDir(), "H2"), "migratemodel.sql"));
    assertTrue(sql.toUpperCase().contains("ALTER TABLE") && sql.toLowerCase().contains("description"), sql);
  }

  @Test
  public void failOnUnexpectedTablesFailsTheBuildForATableNotInTheModel() throws Exception {
    String modelName = "failOnUnexpectedTablesFailsTheBuildForATableNotInTheModel";
    SqlTestSupport.writeFile(getModelDir(), "Item.txt", ITEM_MODEL);
    bootstrapSchemaFromModel(modelName);
    execute("CREATE TABLE leftover (id BIGINT)");

    MigrateSqlMojo mojo = configure(modelName);
    SqlTestSupport.setField(mojo, "failOnUnexpectedTables", true);

    MojoFailureException ex = expectThrows(MojoFailureException.class, () -> run(mojo));
    assertTrue(ex.getMessage().contains("leftover"), ex.getMessage());
  }

  @Test
  public void unexpectedTablesOnlyWarnByDefault() throws Exception {
    String modelName = "unexpectedTablesOnlyWarnByDefault";
    SqlTestSupport.writeFile(getModelDir(), "Item.txt", ITEM_MODEL);
    bootstrapSchemaFromModel(modelName);
    execute("CREATE TABLE leftover (id BIGINT)");

    MigrateSqlMojo mojo = configure(modelName);
    run(mojo);

    assertTrue(RecordingLog.contains(getLog().getInfoMessages(), "database meta data matches object model"),
               getLog().getInfoMessages().toString());
  }

  @Test
  public void alienTablesAreExcludedFromTheUnexpectedTablesCheck() throws Exception {
    String modelName = "alienTablesAreExcludedFromTheUnexpectedTablesCheck";
    SqlTestSupport.writeFile(getModelDir(), "Item.txt", ITEM_MODEL);
    bootstrapSchemaFromModel(modelName);
    execute("CREATE TABLE leftover (id BIGINT)");

    MigrateSqlMojo mojo = configure(modelName);
    SqlTestSupport.setField(mojo, "failOnUnexpectedTables", true);
    SqlTestSupport.setField(mojo, "alienTables", List.of("leftover"));

    run(mojo);

    assertTrue(RecordingLog.contains(getLog().getInfoMessages(), "database meta data matches object model"),
               getLog().getInfoMessages().toString());
  }

  /**
   * A {@code validate=true} run fails the build instead of just warning - {@link ValidateSqlMojo}'s
   * entire behavior, tested here directly since it needs the same live database fixture.
   */
  @Test
  public void validateFlagFailsTheBuildInsteadOfJustWarning() throws Exception {
    SqlTestSupport.writeFile(getModelDir(), "Item.txt",
        "name := Item\n" +
        "id := 1\n" +
        "table := item\n" +
        "\n" +
        "## attributes\n" +
        "[root]\n" +
        "\n" +
        "String(30)      name            name            the item's name\n" +
        "String(100)     description     description     the item's description\n");
    execute("CREATE TABLE item (name VARCHAR(30), id BIGINT NOT NULL PRIMARY KEY, serial BIGINT NOT NULL DEFAULT 1)");

    MigrateSqlMojo mojo = configure("validateFlagFailsTheBuildInsteadOfJustWarning");
    SqlTestSupport.setField(mojo, "validate", true);

    MojoFailureException ex = expectThrows(MojoFailureException.class, () -> run(mojo));
    assertTrue(ex.getMessage().contains("database meta data differs from object model"), ex.getMessage());
  }

  @Test
  public void renameHintRenamesAnExistingTableInsteadOfDroppingAndRecreating() throws Exception {
    SqlTestSupport.writeFile(getModelDir(), "Item.txt", ITEM_MODEL);
    // the table already exists under its old name
    execute("CREATE TABLE olditem (name VARCHAR(30), id BIGINT NOT NULL PRIMARY KEY, serial BIGINT NOT NULL DEFAULT 1)");

    File hintsDir = SqlTestSupport.createDir(getModelDir().getParentFile(), "hints");
    SqlTestSupport.writeFile(hintsDir, "renames.txt", "rename olditem: item\n");

    MigrateSqlMojo mojo = configure("renameHintRenamesAnExistingTableInsteadOfDroppingAndRecreating");
    FileSet hintFileset = new FileSet();
    hintFileset.setDirectory(hintsDir.getPath());
    // configure() already installed a single-element list without hints - replace it with one carrying them
    BackendInfoParameter backendWithHints = new BackendInfoParameter();
    backendWithHints.url = URL;
    backendWithHints.migrationHints = List.of(hintFileset);
    // VersionFileFilter#isValid requires a non-null version even for a "permanent" hint directory
    backendWithHints.minVersion = "1.0.0";
    SqlTestSupport.setField(mojo, "backends", List.of(backendWithHints));

    run(mojo);

    String sql = SqlTestSupport.readFile(new File(new File(getSqlDir(), "H2"), "migratemodel.sql"));
    assertTrue(sql.toUpperCase().contains("RENAME") && sql.toLowerCase().contains("olditem"), sql);
    assertFalse(sql.toUpperCase().contains("DROP TABLE"), sql);
    assertFalse(sql.toUpperCase().contains("CREATE TABLE"), sql);
  }


  private MigrateSqlMojo configure(String modelName) {
    MigrateSqlMojo mojo = configure(new MigrateSqlMojo(), modelName);
    SqlTestSupport.setField(mojo, "sqlFileName", "migratemodel.sql");
    return mojo;
  }

}
