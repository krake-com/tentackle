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
import org.apache.maven.shared.model.fileset.FileSet;
import org.apache.maven.shared.model.fileset.util.FileSetManager;

import org.tentackle.common.Settings;
import org.tentackle.model.migrate.ColumnMigration;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Migration hints.
 * <p>
 *
 * The hint files are of the following format:
 *
 * <pre>
 * &lt;keyword&gt;: line
 * line
 * line
 * ...
 *
 * &lt;keyword&gt;:
 * </pre>
 *
 * Kewords start at the first character of a line and are immediately followed by a colon. Their scope spans to the end of the current and
 * all following lines until the next keyword.
 * <br>
 * Valid keywords are:
 *
 * <ul>
 * <li>always before: SQL code to be executed before everything else (even if nothing changed at all)</li>
 * <li>always after: SQL code to be executed after everything else (even if nothing changed at all)</li>
 * <li>before all: SQL code to be executed before everything else (if anything changed at all)</li>
 * <li>after all: SQL code to be executed after everything else (if anything changed at all)</li>
 * <li>before &lt;tablename&gt;: SQL code to be executed before the migration of given table</li>
 * <li>after &lt;tablename&gt;: SQL code to be executed after the migration of given table</li>
 * <li>migrate &lt;tablename&gt;: explicit SQL code to migrate given table</li>
 * <li>migrate &lt;tablename&gt;#&lt;columnname&gt;[/&lt;newcolumnname&gt;]: explicit SQL code to migrate given column</li>
 * <li>rename &lt;tablename&gt;: &lt;newtablename&gt;. Rename table hint. If <em>newtablename</em> does not exist and belongs to the model and
 * <em>tablename</em> exists, but does not belong to the model, then rename <em>tablename</em> to <em>newtablename</em> and continue with migration.
 * Notice: newtablename must be given without schema since tables can only be renamed within the same schema.</li>
 * <li>rename &lt;tablename&gt;#&lt;columnname&gt;: &lt;newcolumnname&gt;. Rename column hint. If <em>newcolumn</em> does not exist and belongs to the model and
 * <em>columnname</em> exists, but does not belong to the model, then rename <em>columnname</em> to <em>newcolumnname</em> and continue with migration.</li>
 * <li>drop &lt;tablename&gt;: <em>comment</em>. Drop table hint. If <em>tablename</em> exists in the database, but does not belong to
 * the model, generate a drop table statement at the end of the migration script.</li>
 * <li>hint &lt;tablename&gt;: migration hint (regular expression). Used to select automatic migrations that were commented out
 * as alternatives by the table migrator.</li>
 * <li>depend &lt;tablename&gt;: tablename, tablename,... wait until all given tablenames are migrated
 * <li>--: comment describing the hint (not copied to the generated SQL)</li>
 * </ul>
 *
 * Several hints for the same table/section will be concatenated.
 *
 * @author harald
 */
public class MigrationHints {

  // keywords

  private static final String COMMENT = "--";
  private static final String ALWAYS_BEFORE = "always before";
  private static final String ALWAYS_AFTER = "always after";
  private static final String BEFORE_ALL = "before all";
  private static final String AFTER_ALL = "after all";
  private static final String BEFORE = "before";
  private static final String AFTER = "after";
  private static final String MIGRATE = "migrate";
  private static final String RENAME = "rename";
  private static final String DROP = "drop";
  private static final String HINT = "hint";
  private static final String DEPEND = "depend";


  /**
   * Key for explicit column migration.
   */
  private record ColumnKey(String columnName, String newColumnName) {}


  private final List<String> hintFileNames;                       // applied hint files
  private final StringBuilder alwaysBefore;                       // SQL to execute at the very beginning (unconditionally)
  private final StringBuilder alwaysAfter;                        // SQL to execute at the very end (unconditionally)
  private final StringBuilder beforeAll;                          // SQL to execute at the very beginning
  private final StringBuilder afterAll;                           // SQL to execute at the very end
  private final Map<String,String> beforeTable;                   // SQL to execute before automatic table migration <tablename:sql>
  private final Map<String,String> afterTable;                    // SQL to execute after automatic table migration <tablename:sql>
  private final Map<String,String> migrateTable;                  // SQL to execute instead automatic table migration <tablename:sql>
  private final Map<String,Map<ColumnKey,String>> migrateColumns; // SQL to execute instead automatic table migration <tablename:<column:sql>>
  private final Map<String,String> renameTable;                   // rename table hint <newtablename:oldtablename>
  private final Set<String> dropTable;                            // drop table hint <oldtablename>
  private final Map<String, Set<ColumnKey>> renameColumns;        // rename column hint <tablename:<column>>
  private final Map<String,Collection<String>> dependencies;      // dependencies <tablename:<tablenames>>
  private final Map<String,Collection<Pattern>> hints;            // hints for the automatic table migrators


  /**
   * parsing section.
   */
  private class Section {

    private final String keyword;       // the keyword
    private String tableName;           // the tablename, null if none
    private String oldColumnName;       // optional old column name
    private String newColumnName;       // optional new column name
    private StringBuilder text;         // the content

    /**
     * Creates a section.
     *
     * @param keyword the keyword
     */
    private Section(String keyword) {
      this.keyword = keyword;
    }

    /**
     * Finishes a parsed section.
     */
    private void finish() throws MojoExecutionException {
      switch(keyword) {
        case ALWAYS_BEFORE:
          if (!alwaysBefore.isEmpty()) {
            alwaysBefore.append('\n');
          }
          alwaysBefore.append(text);
          break;

        case ALWAYS_AFTER:
          if (!alwaysAfter.isEmpty()) {
            alwaysAfter.append('\n');
          }
          alwaysAfter.append(text);
          break;

        case BEFORE_ALL:
          if(!beforeAll.isEmpty()) {
            beforeAll.append('\n');
          }
          beforeAll.append(text);
          break;

        case AFTER_ALL:
          if (!afterAll.isEmpty()) {
            afterAll.append('\n');
          }
          afterAll.append(text);
          break;

        case BEFORE:
          String before = beforeTable.get(tableName);
          if (before != null) {
            text.insert(0, '\n');
            text.insert(0, before);
          }
          beforeTable.put(tableName, text.toString());
          break;

        case AFTER:
          String after = afterTable.get(tableName);
          if (after != null) {
            text.insert(0, '\n');
            text.insert(0, after);
          }
          afterTable.put(tableName, text.toString());
          break;

        case MIGRATE:
          if (oldColumnName != null) {
            // column migration
            ColumnKey colKey = new ColumnKey(oldColumnName, newColumnName);
            Map<ColumnKey, String> columns = migrateColumns.computeIfAbsent(tableName, k -> new HashMap<>());
            String migrate = columns.get(colKey);
            if (migrate != null) {
              text.insert(0, '\n');
              text.insert(0, migrate);
            }
            columns.put(colKey, text.toString());
          }
          else {
            // table migration
            String migrate = migrateTable.get(tableName);
            if (migrate != null) {
              text.insert(0, '\n');
              text.insert(0, migrate);
            }
            migrateTable.put(tableName, text.toString());
          }
          break;

        case RENAME:
          if (oldColumnName != null) {
            // rename column
            Set<ColumnKey> keys = renameColumns.computeIfAbsent(tableName, k -> new HashSet<>());
            String newColumnName = toName(text.toString());
            keys.add(new ColumnKey(oldColumnName, newColumnName));
          }
          else {
            // rename table
            String newTableName = toName(text.toString());
            renameTable.put(newTableName, tableName);
          }
          break;

        case DROP:
          dropTable.add(tableName);
          break;

        case HINT:
          try {
            Pattern pattern = Pattern.compile(text.toString(), Pattern.DOTALL);
            Collection<Pattern> patterns = hints.computeIfAbsent(tableName, k -> new ArrayList<>());
            patterns.add(pattern);
          }
          catch (PatternSyntaxException pex) {
            throw new MojoExecutionException("migration hint malformed", pex);
          }
          break;

        case DEPEND:
          Collection<String> tables = dependencies.computeIfAbsent(tableName, k -> new ArrayList<>());
          StringTokenizer stok = new StringTokenizer(text.toString(), ",; \t\r\n");
          while (stok.hasMoreTokens()) {
            tables.add(stok.nextToken().trim().toLowerCase(Locale.ROOT));
          }
          break;
      }
    }
  }




  /**
   * Creates the migration hints for a backend.
   */
  public MigrationHints() {
    hintFileNames = new ArrayList<>();
    alwaysBefore = new StringBuilder();
    alwaysAfter = new StringBuilder();
    beforeAll = new StringBuilder();
    afterAll = new StringBuilder();
    beforeTable = new HashMap<>();
    afterTable = new HashMap<>();
    migrateTable = new HashMap<>();
    migrateColumns = new HashMap<>();
    renameTable = new HashMap<>();
    renameColumns = new HashMap<>();
    dropTable = new HashSet<>();
    hints = new HashMap<>();
    dependencies = new HashMap<>();
  }

  /**
   * Gets the names of the analyzed hintfiles.<br>
   * The directory name is included as well.
   *
   * @return the hint file names
   */
  public List<String> getHintFileNames() {
    return hintFileNames;
  }

  /**
   * Gets the sql code to be executed before anything else, unconditionally.
   *
   * @return the sql code, never null
   */
  public String getAlwaysBefore() {
    return alwaysBefore.toString();
  }

  /**
   * Gets the sql code to be executed after anything else, unconditionally.
   *
   * @return the sql code, never null
   */
  public String getAlwaysAfter() {
    return alwaysAfter.toString();
  }

  /**
   * Gets the sql code to be executed before anything else.
   *
   * @return the sql code, never null
   */
  public String getBeforeAll() {
    return beforeAll.toString();
  }

  /**
   * Gets the sql code to be executed after anything else.
   *
   * @return the sql code, never null
   */
  public String getAfterAll() {
    return afterAll.toString();
  }

  /**
   * Gets the sql code to be executed before table migration.
   *
   * @param tableName the tablename
   * @return the sql code, null if no such sql
   */
  public String getBeforeTable(String tableName) {
    return beforeTable.get(tableName);
  }

  /**
   * Gets the sql code to be executed after table migration.
   *
   * @param tableName the tablename
   * @return the sql code, null if no such sql
   */
  public String getAfterTable(String tableName) {
    return afterTable.get(tableName);
  }

  /**
   * Gets the sql code to be executed instead of table migration.
   *
   * @param tableName the tablename
   * @return the sql code, null to use automatic migration
   */
  public String getMigrateTable(String tableName) {
    return migrateTable.get(tableName);
  }

  /**
   * Gets the "rename" table hint.
   *
   * @param newTableName the new tablename
   * @return the old tablename
   */
  public String getRenameTable(String newTableName) {
    return renameTable.get(newTableName);
  }

  /**
   * Gets the drop table hints.
   *
   * @return the old tablenames
   */
  public Set<String> getDropTables() {
    return dropTable;
  }

  /**
   * Gets the name columns hints.
   *
   * @param tableName the tablename
   * @return a map of new- to old column names, null if none
   */
  public Map<String, String> getRenameColumns(String tableName) {
    Set<ColumnKey> keys = renameColumns.get(tableName);
    if (keys != null) {
      Map<String, String> columnMap = new HashMap<>();
      for (ColumnKey key: keys) {
        columnMap.put(key.newColumnName, key.columnName);
      }
      return columnMap;
    }
    return null;
  }

  /**
   * Gets the migration hints.
   *
   * @param tableName the tablename
   * @return the hint patterns, null if no hints for this table
   */
  public Collection<Pattern> getHints(String tableName) {
    return hints.get(tableName);
  }

  /**
   * Gets the explicit column migrations for a table.
   *
   * @param tableName the table
   * @return the explicit column migrations, null if none
   */
  public Collection<ColumnMigration> getColumnMigrations(String tableName) {
    Map<ColumnKey,String> columns = migrateColumns.get(tableName);
    if (columns != null) {
      Collection<ColumnMigration> migrations = new ArrayList<>();
      for (Map.Entry<ColumnKey,String> entry: columns.entrySet()) {
        migrations.add(new ColumnMigration(entry.getKey().columnName, entry.getKey().newColumnName, entry.getValue()));
      }
      return migrations;
    }
    return null;
  }

  /**
   * Gets the dependencies.
   *
   * @param tableName the tablename
   * @return the dependencies, null if no dependencies for this table
   */
  public Collection<String> getDependencies(String tableName) {
    return dependencies.get(tableName);
  }


  /**
   * Load the migration hints.
   *
   * @param mojo the mojo
   * @param verbose true if verbose
   * @param filesets the filesets to load hints from
   * @param version the minimum hint file version
   * @param resourceDirs resource dirs, null if none
   * @throws MojoExecutionException if failed
   */
  public void load(AbstractSqlMojo mojo, boolean verbose, List<FileSet> filesets, String version, List<String> resourceDirs) throws MojoExecutionException {
    if (filesets != null) {
      for (FileSet fileset: filesets) {
        FileSetManager fileSetManager = mojo.createFileSetManager(verbose);
        List<String> hintDirNames;
        if (fileset.getDirectory() == null) {
          hintDirNames = resourceDirs;  // try all resource directories
          if (hintDirNames == null || hintDirNames.isEmpty()) {
            throw new MojoExecutionException("no <directory> given in <fileset> of <migrationHints> and no resource directories found");
          }
        }
        else  {
          hintDirNames = new ArrayList<>();
          hintDirNames.add(fileset.getDirectory());   // use given directory
        }
        for (String hintDirName: hintDirNames) {
          fileset.setDirectory(hintDirName);
          String[] fileNames = fileSetManager.getIncludedFiles(fileset);
          for (String filename : fileNames) {
            File hintFile = new File(new File(hintDirName), filename);
            if (VersionFileFilter.getInstance().isValid(version, hintFile)) {
              mojo.getLogger().debug("loading migration hints from {}", hintFile);
              hintFileNames.add(hintFile.getParentFile().getName() + "/" + hintFile.getName());
              StringBuilder buf = new StringBuilder();
              try (BufferedReader reader = new BufferedReader(
                                             new InputStreamReader(
                                               new FileInputStream(hintFile), Settings.getEncodingCharset()))) {
                char[] readBuf = new char[1024];
                int len;
                while ((len = reader.read(readBuf)) != -1) {
                  buf.append(readBuf, 0, len);
                }
              }
              catch (IOException ex) {
                throw new MojoExecutionException("reading migration hints from '" + hintFile + "' failed", ex);
              }

              String delims = "\n\r";
              StringTokenizer lineTokenizer = new StringTokenizer(buf.toString(), delims, true);
              int lineNumber = 1;
              Section section = null;       // current section
              try {
                while (lineTokenizer.hasMoreTokens()) {
                  String line = lineTokenizer.nextToken();
                  if (line.length() == 1 && delims.contains(line)) {
                    // delimiter
                    if (line.equals("\n")) {
                      lineNumber++;
                    }
                    continue;
                  }
                  Section newSection = nextSection(line);
                  if (newSection != null) {
                    if (section != null) {
                      section.finish();
                    }
                    section = newSection;
                  }
                  else if (section != null) {
                    section.text.append('\n');
                    section.text.append(line);
                  }
                }
                if (section != null) {
                  section.finish();
                }
              }
              catch (MojoExecutionException mox) {
                String msg = mox.getMessage() + " in " + hintFile + ", line " + lineNumber;
                if (mox.getCause() != null) {
                  throw new MojoExecutionException(msg);
                }
                throw new MojoExecutionException(msg, mox.getCause());
              }
            }
            else {
              mojo.getLogger().debug("{} skipped", hintFile);
            }
          }
        }
      }
    }
  }


  /**
   * Returns the new section if line starts a new one.
   *
   * @param line the line
   * @return the section, null if line does not start with a keyword
   */
  private Section nextSection(String line) throws MojoExecutionException {
    Section section = null;

    if (line.startsWith(ALWAYS_BEFORE)) {
      section = new Section(ALWAYS_BEFORE);
    }
    else if (line.startsWith(ALWAYS_AFTER)) {
      section = new Section(ALWAYS_AFTER);
    }
    else if (line.startsWith(BEFORE_ALL)) {
      section = new Section(BEFORE_ALL);
    }
    else if (line.startsWith(AFTER_ALL)) {
      section = new Section(AFTER_ALL);
    }
    else if (line.startsWith(BEFORE)) {
      section = new Section(BEFORE);
    }
    else if (line.startsWith(AFTER)) {
      section = new Section(AFTER);
    }
    else if (line.startsWith(MIGRATE)) {
      section = new Section(MIGRATE);
    }
    else if (line.startsWith(RENAME)) {
      section = new Section(RENAME);
    }
    else if (line.startsWith(DROP)) {
      section = new Section(DROP);
    }
    else if (line.startsWith(HINT)) {
      section = new Section(HINT);
    }
    else if (line.startsWith(DEPEND)) {
      section = new Section(DEPEND);
    }
    else if (line.startsWith(COMMENT)) {
      section = new Section(COMMENT);
    }

    if (section != null) {
      // read optional tablename
      int colonNdx = line.indexOf(':');
      if (colonNdx == -1) {
        section = null;    // no valid section
      }
      else  {
        section.tableName = line.substring(section.keyword.length(), colonNdx).trim().toLowerCase(Locale.ROOT);
        int dotNdx = section.tableName.indexOf('#');
        if (dotNdx > 0) {
          section.oldColumnName = section.tableName.substring(dotNdx + 1);
          section.tableName = section.tableName.substring(0, dotNdx);
          int slashNdx = section.oldColumnName.indexOf('/');
          if (slashNdx > 0) {
            section.newColumnName = section.oldColumnName.substring(slashNdx + 1);
            section.oldColumnName = section.oldColumnName.substring(0, slashNdx);
          }
        }
        section.text = new StringBuilder(line.substring(colonNdx + 1).trim());
        // validate keyword
        switch (section.keyword) {
          case BEFORE:
          case AFTER:
          case MIGRATE:
          case RENAME:
          case DROP:
          case HINT:
          case DEPEND:
            if (section.tableName.isEmpty()) {
              throw new MojoExecutionException("missing tablename");
            }
            if (!isValidTableName(section.tableName)) {
              section = null;
            }
            break;

          case ALWAYS_BEFORE:
          case ALWAYS_AFTER:
          case BEFORE_ALL:
          case AFTER_ALL:
            if (!section.tableName.isEmpty()) {
              section = null;   // colon not immediately following the keyword
            }
        }
      }
    }

    return section;
  }


  /**
   * Checks if the given string is a valid tablename.
   *
   * @param str the string
   * @return true if string could be a tablename
   */
  private boolean isValidTableName(String str) {
    boolean lastWasDot = true;
    int dotCount = 0;
    if (str != null && !str.isEmpty()) {
      for (int pos = 0; pos < str.length(); pos++) {
        char c = str.charAt(pos);
        if (c == '.') {
          lastWasDot = true;
          dotCount++;
          if (dotCount > 1) {
            return false;   // only one dot is allowed to separate schema from tablename
          }
        }
        else {
          if (lastWasDot) {
            lastWasDot = false;
            if (Character.isDigit(c)) {
              return false;   // must not start with a digit
            }
          }
          else {
            if (!Character.isDigit(c) && // digit is ok if not at start of table- or schema name
                !Character.isAlphabetic(c) && c != '_') {
              // must be digit, alphabetic or underscore
              return false;
            }
          }
        }
      }
      return true;
    }
    else {
      return false;
    }
  }

  private String toName(String str) {
    str = str.trim();
    while(str.endsWith(";")) {    // prevents a common mistake...
      str = str.substring(0,str.length() - 1).trim();
    }
    return str;
  }

}
