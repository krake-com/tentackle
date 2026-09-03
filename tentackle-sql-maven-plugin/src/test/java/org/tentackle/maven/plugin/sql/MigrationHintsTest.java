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
import org.apache.maven.shared.model.fileset.FileSet;

import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import org.tentackle.maven.plugin.sql.SqlTestSupport.RecordingLog;

import java.io.File;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.expectThrows;

/**
 * Tests the migration hint file parser.
 * <p>
 * {@link MigrationHints#load} only needs {@code mojo.createFileSetManager()} and
 * {@code mojo.getLog()}, both inherited from {@code AbstractTentackleMojo}, so a plain
 * {@code new CreateSqlMojo()} is enough - no {@code validate()}/{@code execute()} lifecycle
 * required.
 */
@SuppressWarnings({ "exports", "missing-explicit-ctor" })
public class MigrationHintsTest {

  private File dir;
  private RecordingLog log;


  @BeforeMethod
  public void setUp() {
    // the directory name deliberately does not start with a digit, so that
    // VersionFileFilter.isValid() treats it as "permanent" regardless of the given version
    dir = SqlTestSupport.createTempDir("hints");
    log = new RecordingLog();
  }

  @AfterMethod
  public void tearDown() {
    SqlTestSupport.deleteRecursively(dir);
  }


  @Test
  public void alwaysBeforeAndAlwaysAfterAreUnconditional() throws Exception {
    SqlTestSupport.writeFile(dir, "hints.txt",
        "always before: CREATE EXTENSION foo;\n" +
        "\n" +
        "always after: DROP EXTENSION foo;\n");

    MigrationHints hints = load("1.0.0");

    assertEquals(hints.getAlwaysBefore(), "CREATE EXTENSION foo;");
    assertEquals(hints.getAlwaysAfter(), "DROP EXTENSION foo;");
  }

  @Test
  public void beforeAllAndAfterAllWrapTheGeneratedMigration() throws Exception {
    SqlTestSupport.writeFile(dir, "hints.txt",
        "before all: -- start\n" +
        "\n" +
        "after all: -- end\n");

    MigrationHints hints = load("1.0.0");

    assertEquals(hints.getBeforeAll(), "-- start");
    assertEquals(hints.getAfterAll(), "-- end");
  }

  @Test
  public void beforeAndAfterTableScopeToASingleTable() throws Exception {
    SqlTestSupport.writeFile(dir, "hints.txt",
        "before mytable: -- before mytable\n" +
        "\n" +
        "after mytable: -- after mytable\n");

    MigrationHints hints = load("1.0.0");

    assertEquals(hints.getBeforeTable("mytable"), "-- before mytable");
    assertEquals(hints.getAfterTable("mytable"), "-- after mytable");
    assertNull(hints.getBeforeTable("othertable"));
  }

  @Test
  public void migrateTableReplacesTheAutomaticMigration() throws Exception {
    SqlTestSupport.writeFile(dir, "hints.txt",
        "migrate mytable: ALTER TABLE mytable ADD COLUMN foo INT;\n");

    MigrationHints hints = load("1.0.0");

    assertEquals(hints.getMigrateTable("mytable"), "ALTER TABLE mytable ADD COLUMN foo INT;");
  }

  @Test
  public void migrateColumnCanAlsoRenameIt() throws Exception {
    SqlTestSupport.writeFile(dir, "hints.txt",
        "migrate mytable#oldcol/newcol: ALTER TABLE mytable ALTER COLUMN oldcol RENAME TO newcol;\n");

    MigrationHints hints = load("1.0.0");

    Collection<org.tentackle.model.migrate.ColumnMigration> migrations = hints.getColumnMigrations("mytable");
    assertEquals(migrations.size(), 1);
    org.tentackle.model.migrate.ColumnMigration migration = migrations.iterator().next();
    assertEquals(migration.getColumnName(), "oldcol");
    assertEquals(migration.getNewColumnName(), "newcol");
    assertEquals(migration.getSql(), "ALTER TABLE mytable ALTER COLUMN oldcol RENAME TO newcol;");
  }

  @Test
  public void renameTableMapsNewNameToOldName() throws Exception {
    SqlTestSupport.writeFile(dir, "hints.txt",
        "rename oldtable: newtable\n");

    MigrationHints hints = load("1.0.0");

    assertEquals(hints.getRenameTable("newtable"), "oldtable");
    assertNull(hints.getRenameTable("oldtable"));
  }

  @Test
  public void renameColumnMapsNewNameToOldName() throws Exception {
    SqlTestSupport.writeFile(dir, "hints.txt",
        "rename mytable#oldcol: newcol\n");

    MigrationHints hints = load("1.0.0");

    Map<String, String> renamedColumns = hints.getRenameColumns("mytable");
    assertEquals(renamedColumns.get("newcol"), "oldcol");
  }

  @Test
  public void dropTableCollectsTheTableName() throws Exception {
    SqlTestSupport.writeFile(dir, "hints.txt",
        "drop obsoletetable: no longer needed\n");

    MigrationHints hints = load("1.0.0");

    assertTrue(hints.getDropTables().contains("obsoletetable"));
  }

  @Test
  public void hintCompilesAPatternSelectingCommentedOutMigrations() throws Exception {
    SqlTestSupport.writeFile(dir, "hints.txt",
        "hint mytable: ALTER TABLE mytable.*RENAME.*\n");

    MigrationHints hints = load("1.0.0");

    Collection<java.util.regex.Pattern> patterns = hints.getHints("mytable");
    assertEquals(patterns.size(), 1);
    assertTrue(patterns.iterator().next().matcher("ALTER TABLE mytable RENAME COLUMN x TO y").matches());
  }

  @Test
  public void malformedHintRegexFailsWithTheLineNumber() {
    SqlTestSupport.writeFile(dir, "hints.txt",
        "hint mytable: [unterminated\n");

    MojoExecutionException ex = expectThrows(MojoExecutionException.class, () -> load("1.0.0"));
    assertTrue(ex.getMessage().contains("migration hint malformed"), ex.getMessage());
  }

  @Test
  public void dependTokenizesTheTableList() throws Exception {
    SqlTestSupport.writeFile(dir, "hints.txt",
        "depend mytable: other1, other2 other3\n");

    MigrationHints hints = load("1.0.0");

    assertEquals(hints.getDependencies("mytable"), List.of("other1", "other2", "other3"));
  }

  @Test
  public void twoHintsForTheSameTableAreConcatenated() throws Exception {
    SqlTestSupport.writeFile(dir, "hints.txt",
        "before mytable: -- first\n" +
        "\n" +
        "before mytable: -- second\n");

    MigrationHints hints = load("1.0.0");

    assertEquals(hints.getBeforeTable("mytable"), "-- first\n-- second");
  }

  @Test
  public void commentLinesAreDroppedFromTheOutput() throws Exception {
    SqlTestSupport.writeFile(dir, "hints.txt",
        "--: this explains the hint below and must not end up in the SQL\n" +
        "before all: -- kept\n");

    MigrationHints hints = load("1.0.0");

    assertEquals(hints.getBeforeAll(), "-- kept");
  }

  @Test
  public void missingTablenameFailsWithTheLineNumber() {
    SqlTestSupport.writeFile(dir, "hints.txt",
        "\n" +
        "before : -- oops, no tablename\n");

    MojoExecutionException ex = expectThrows(MojoExecutionException.class, () -> load("1.0.0"));
    assertTrue(ex.getMessage().contains("missing tablename"), ex.getMessage());
    assertTrue(ex.getMessage().contains("line 2"), ex.getMessage());
  }

  @Test
  public void invalidTableNamesAreSilentlyIgnoredRatherThanRejected() throws Exception {
    // starts with a digit, and two dots - neither is a valid table(.schema) name
    SqlTestSupport.writeFile(dir, "hints.txt",
        "drop 1table: ignored\n" +
        "drop schema.sub.table: ignored\n");

    MigrationHints hints = load("1.0.0");

    assertTrue(hints.getDropTables().isEmpty());
  }

  @Test
  public void versionFilterSkipsHintsThatAlreadyApplied() throws Exception {
    File hintsRoot = SqlTestSupport.createDir(dir, "root");
    SqlTestSupport.writeFile(hintsRoot, "0.9/old.txt", "drop obsolete: already migrated\n");
    SqlTestSupport.writeFile(hintsRoot, "2.0/future.txt", "drop notyetdropped: still pending\n");

    FileSet fileset = new FileSet();
    fileset.setDirectory(hintsRoot.getPath());
    MigrationHints hints = new MigrationHints();
    CreateSqlMojo mojo = new CreateSqlMojo();
    mojo.setLog(log);
    hints.load(mojo, false, List.of(fileset), "1.0.0", null);

    assertFalse(hints.getDropTables().contains("obsolete"), "0.9 hint already applied at version 1.0.0");
    assertTrue(hints.getDropTables().contains("notyetdropped"), "2.0 hint still pending at version 1.0.0");
  }


  private MigrationHints load(String version) throws MojoExecutionException {
    FileSet fileset = new FileSet();
    fileset.setDirectory(dir.getPath());
    MigrationHints hints = new MigrationHints();
    CreateSqlMojo mojo = new CreateSqlMojo();
    mojo.setLog(log);
    hints.load(mojo, false, List.of(fileset), version, null);
    return hints;
  }

}
