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
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

import org.tentackle.maven.AbstractTentackleMojo;
import org.tentackle.maven.plugin.sql.reveng.RevengModelWriter;
import org.tentackle.maven.plugin.sql.reveng.TableNaming;
import org.tentackle.sql.Backend;
import org.tentackle.sql.BackendInfo;
import org.tentackle.sql.BackendInfoFactory;
import org.tentackle.sql.metadata.ModelMetaData;
import org.tentackle.sql.metadata.TableMetaData;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.StringTokenizer;

/**
 * Reverse-engineers tables of an existing database into Tentackle model source files.
 * <p>
 * Given a JDBC connection and a list of table names, this goal introspects each table's columns,
 * indexes and foreign keys (via {@code org.tentackle.sql.metadata}) and writes one model-source
 * file per table into {@link #outputDir}, in exactly the plain-text format that
 * {@code tentackle-wizard:pdo}'s {@code -DbatchDir} batch mode already reads. It does not generate
 * any Java code itself — running {@code pdo} on the output directory is the second, separate step:
 * <pre>
 * mvn tentackle-sql:reveng -Dtentackle.reveng.tables=CUSTOMER,ORDER_ITEM -Dtentackle.reveng.url=jdbc:... -Dtentackle.reveng.user=...
 * mvn tentackle-wizard:pdo -DbatchDir=target/reveng-model
 * </pre>
 * <p>
 * Foreign keys become plain, non-composite object relations using {@link #integrity}; composite
 * (aggregate) semantics cannot be inferred from schema metadata and are left for the user to add
 * by hand to the generated files before running {@code pdo}. A foreign key referencing a table
 * that is not part of the same {@code reveng} run is left as a plain attribute (with an explanatory
 * comment) rather than a relation, since the batch model cannot resolve a relation to an entity
 * that isn't among the files being loaded.
 *
 * @author harald
 */
@Mojo(name = "reveng", aggregator = true)
public class RevengMojo extends AbstractTentackleMojo {

  /**
   * The backend URL.
   */
  @Parameter(required = true, property = "tentackle.reveng.url")
  private String url;

  /**
   * The backend username.
   */
  @Parameter(required = true, property = "tentackle.reveng.user")
  private String user;

  /**
   * The backend password.
   */
  @Parameter(property = "tentackle.reveng.password")
  private String password;

  /**
   * Optional comma-separated list of schema names to restrict introspection to.
   */
  @Parameter(property = "tentackle.reveng.schemas")
  private String schemas;

  /**
   * Comma-separated list of table names to reverse-engineer.
   */
  @Parameter(required = true, property = "tentackle.reveng.tables")
  private String tables;

  /**
   * The directory the generated model source files are written to.<br>
   * Pass this directory as {@code -DbatchDir} to {@code tentackle-wizard:pdo}.
   */
  @Parameter(defaultValue = "${project.build.directory}/reveng-model", property = "tentackle.reveng.outputDir")
  private File outputDir;

  /**
   * The class id assigned to the first reverse-engineered entity; subsequent entities
   * (in the order of {@link #tables}) get consecutive ids.<br>
   * Batch-mode model source always needs a concrete, non-zero class id (see
   * {@code tentackle-wizard:pdo}'s batch mode), so the caller is responsible for choosing a
   * range that does not collide with the target project's already assigned class ids,
   * exactly as when hand-writing batch model files.
   */
  @Parameter(defaultValue = "1", property = "tentackle.reveng.startClassId")
  private int startClassId;

  /**
   * The {@code integrity :=} value applied to every generated entity.<br>
   * Defaults to {@code related} (application-checked, database foreign key expected) since a
   * physical foreign key was, by definition, already found in the database for any relation
   * this goal emits.
   */
  @Parameter(defaultValue = "related", property = "tentackle.reveng.integrity")
  private String integrity;

  /**
   * Optional stereotype (without the {@code #}) written into every generated entity's global
   * options line, so that {@code tentackle-wizard:pdo}'s batch mode can select the matching
   * {@code PdoProfile} when more than one is configured.
   */
  @Parameter(property = "tentackle.reveng.stereotype")
  private String stereotype;

  /**
   * Additional entity option flags written verbatim into every generated entity's global
   * options line, comma-separated, for example {@code remote, tracked}.
   */
  @Parameter(property = "tentackle.reveng.entityOptions")
  private String entityOptions;

  /**
   * Whether to overwrite already existing files in {@link #outputDir}.
   */
  @Parameter(defaultValue = "false", property = "tentackle.reveng.overwrite")
  private boolean overwrite;


  @Override
  public void executeImpl() throws MojoExecutionException, MojoFailureException {

    List<String> tableNames = tokenize(tables);
    if (tableNames.isEmpty()) {
      throw new MojoFailureException("no tables given");
    }

    BackendInfo backendInfo = BackendInfoFactory.getInstance().create(
        url, user, password == null ? null : password.toCharArray(), tokenizeToArray(schemas));
    Backend backend = backendInfo.getBackend();

    DatabaseMetaData[] jdbcMetaData;
    try {
      jdbcMetaData = backend.getMetaData(backendInfo);
    }
    catch (SQLException sqx) {
      throw new MojoExecutionException("could not connect to " + url, sqx);
    }

    try {
      executeReveng(backend, backendInfo, jdbcMetaData, tableNames);
    }
    finally {
      for (DatabaseMetaData metaData : jdbcMetaData) {
        try {
          metaData.getConnection().close();
        }
        catch (SQLException sqx) {
          getLog().warn("could not close connection", sqx);
        }
      }
    }
  }

  private void executeReveng(Backend backend, BackendInfo backendInfo, DatabaseMetaData[] jdbcMetaData, List<String> tableNames)
      throws MojoExecutionException, MojoFailureException {
    try {
      ModelMetaData modelMetaData = backend.getModelMetaData(jdbcMetaData, backendInfo.getSchemas(), tableNames.toArray(new String[0]));

      Map<String, TableMetaData> tablesByRequestedName = new LinkedHashMap<>();
      for (String tableName : tableNames) {
        TableMetaData table = findTable(modelMetaData, tableName);
        if (table == null) {
          throw new MojoFailureException("table '" + tableName + "' not found" +
                                         (schemas != null ? " (check that --schemas matches its actual schema)" : ""));
        }
        tablesByRequestedName.put(tableName, table);
      }

      Set<String> batchEntityNames = new LinkedHashSet<>();
      Map<String, String> entityNameByRequestedName = new LinkedHashMap<>();
      for (Map.Entry<String, TableMetaData> entry : tablesByRequestedName.entrySet()) {
        String entityName = TableNaming.toEntityName(entry.getValue().getTableName());
        entityNameByRequestedName.put(entry.getKey(), entityName);
        if (!batchEntityNames.add(entityName)) {
          throw new MojoFailureException("two requested tables map to the same entity name '" + entityName + "'");
        }
      }

      if (!outputDir.exists() && !outputDir.mkdirs()) {
        throw new MojoExecutionException("could not create output directory " + outputDir);
      }

      String combinedOptions = combineOptions();
      int classId = startClassId;

      for (Map.Entry<String, TableMetaData> entry : tablesByRequestedName.entrySet()) {
        TableMetaData table = entry.getValue();
        String entityName = entityNameByRequestedName.get(entry.getKey());

        RevengModelWriter.Result result = RevengModelWriter.write(
            backend, table, entityName, classId++, integrity, combinedOptions, batchEntityNames);

        for (String warning : result.warnings()) {
          getLog().warn(warning);
        }

        File outputFile = new File(outputDir, entityName + ".map");
        if (outputFile.exists() && !overwrite) {
          getLog().warn("file " + outputFile + " already exists -> skipped (use -Dtentackle.reveng.overwrite=true to replace)");
          continue;
        }

        try {
          Files.writeString(outputFile.toPath(), result.modelSource(), StandardCharsets.UTF_8);
        }
        catch (IOException iox) {
          throw new MojoExecutionException("could not write " + outputFile, iox);
        }

        getLog().info("reverse-engineered " + table.getFullTableName() + " -> " + outputFile);
      }

      getLog().info("done. Next step: mvn tentackle-wizard:pdo -DbatchDir=" + outputDir);
    }
    catch (RuntimeException ex) {
      throw new MojoExecutionException("reverse engineering failed", ex);
    }
  }

  private TableMetaData findTable(ModelMetaData modelMetaData, String tableName) {
    for (TableMetaData table : modelMetaData.getTables()) {
      if (table.getModelTableName().equalsIgnoreCase(tableName)) {
        return table;
      }
    }
    return null;
  }

  private String combineOptions() {
    List<String> options = new ArrayList<>();
    if (entityOptions != null && !entityOptions.isBlank()) {
      options.add(entityOptions.trim());
    }
    if (stereotype != null && !stereotype.isBlank()) {
      options.add("#" + stereotype.trim());
    }
    return String.join(", ", options);
  }

  private static List<String> tokenize(String str) {
    List<String> tokens = new ArrayList<>();
    if (str != null) {
      StringTokenizer stok = new StringTokenizer(str, ",");
      while (stok.hasMoreTokens()) {
        tokens.add(stok.nextToken().trim());
      }
    }
    return tokens;
  }

  private static String[] tokenizeToArray(String str) {
    List<String> tokens = tokenize(str);
    return tokens.isEmpty() ? null : tokens.toArray(new String[0]);
  }

}
