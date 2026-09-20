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

package org.tentackle.maven.plugin.sql.reveng;

import org.tentackle.common.Constants;
import org.tentackle.common.StringHelper;
import org.tentackle.sql.Backend;
import org.tentackle.sql.metadata.ColumnMetaData;
import org.tentackle.sql.metadata.ForeignKeyColumnMetaData;
import org.tentackle.sql.metadata.ForeignKeyMetaData;
import org.tentackle.sql.metadata.IndexColumnMetaData;
import org.tentackle.sql.metadata.IndexMetaData;
import org.tentackle.sql.metadata.TableMetaData;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Renders the database metadata of a single table, gathered via {@code org.tentackle.sql.metadata},
 * as Tentackle model source text — the same plain-text DSL format read by
 * {@code tentackle-wizard:pdo}'s {@code -DbatchDir} batch mode.
 * <p>
 * This is a write-only, best-effort translation: composite/cascade relation semantics cannot be
 * inferred from schema metadata alone, so every foreign key becomes a plain, non-composite object
 * relation using the entity's configured {@code integrity} level, ready for the user to refine by
 * hand before running the {@code pdo} goal. Foreign keys pointing outside the set of tables being
 * reverse-engineered together are left as plain attributes with an explanatory comment, since the
 * model they would point to may not exist in the batch (which would abort loading the whole batch).
 *
 * @author harald
 */
public final class RevengModelWriter {

  private RevengModelWriter() {
    // no instances
  }

  /**
   * The result of writing one table.
   *
   * @param modelSource the generated model source text
   * @param warnings human-readable notes about limitations applied while writing (for the mojo to log)
   */
  public record Result(String modelSource, List<String> warnings) {
  }

  /**
   * A single (single-column) foreign key resolved to a relation.
   *
   * @param foreignKeyColumnName the local column name
   * @param targetEntityName the referenced entity's name
   * @param relationName the relation's name (equals targetEntityName unless disambiguated)
   * @param javaAttributeName the local attribute name backing the relation ({@code <relationName>Id})
   */
  private record RelationPlan(String foreignKeyColumnName, String targetEntityName, String relationName, String javaAttributeName) {
  }

  /**
   * Writes the model source for a single table.
   *
   * @param backend the backend the table was read from
   * @param table the table metadata
   * @param entityName the entity name (see {@link TableNaming#toEntityName(String)})
   * @param classId the class id to assign
   * @param integrity the {@code integrity :=} value
   * @param globalOptions additional entity option flags/stereotypes (comma-separated), null/empty if none
   * @param batchEntityNames the entity names of every table being reverse-engineered in this run (including this one)
   * @return the generated model source plus any notes worth logging
   */
  public static Result write(Backend backend, TableMetaData table, String entityName, int classId,
                              String integrity, String globalOptions, Set<String> batchEntityNames) {

    List<String> warnings = new ArrayList<>();
    boolean hasId = table.getColumnByName(Constants.CN_ID) != null;

    Map<String, RelationPlan> relationByColumn = planRelations(table, batchEntityNames, warnings);

    // some backends/drivers (e.g. H2) return unquoted table names upper-cased; normalize to
    // lowercase for the generated source, matching Tentackle's own table/column naming convention
    String fullTableName = table.getFullTableName().toLowerCase(Locale.ROOT);

    StringBuilder buf = new StringBuilder();
    buf.append("# reverse-engineered from table ").append(fullTableName).append('\n');
    buf.append("name := ").append(entityName).append('\n');
    buf.append("id := ").append(classId).append('\n');
    buf.append("table := ").append(fullTableName).append('\n');
    buf.append("integrity := ").append(integrity).append('\n');
    buf.append('\n');

    buf.append("## attributes\n");
    List<String> options = new ArrayList<>();
    if (!hasId) {
      options.add("nopkey");
      warnings.add("table " + table.getFullTableName() + " has no 'id' column -> entity " + entityName +
                   " declared with [nopkey] and will not behave as a normal identity-bearing PDO");
    }
    if (globalOptions != null && !globalOptions.isBlank()) {
      for (String option : globalOptions.split(",")) {
        if (!option.isBlank()) {
          options.add(option.trim());
        }
      }
    }
    if (!options.isEmpty()) {
      buf.append('[').append(String.join(", ", options)).append("]\n");
    }
    buf.append('\n');

    List<String[]> attributeRows = new ArrayList<>();       // {typeDecl, javaName, columnName, comment}
    List<String> notNullValidations = new ArrayList<>();

    for (ColumnMetaData column : table.getColumns()) {
      String columnName = column.getColumnName();
      if (columnName.equalsIgnoreCase(Constants.CN_ID) || columnName.equalsIgnoreCase(Constants.CN_CLASSID)) {
        continue; // implicit, added automatically by the model
      }

      RelationPlan plan = relationByColumn.get(columnName);
      ColumnTypeMapper.ColumnType type = ColumnTypeMapper.map(backend, column);
      String javaName = plan != null ? plan.javaAttributeName() : TableNaming.toJavaName(columnName);

      StringBuilder comment = new StringBuilder();
      if (column.getComment() != null) {
        comment.append(column.getComment());
      }
      if (plan != null) {
        appendComment(comment, "-> " + plan.targetEntityName());
      }
      else if (isForeignKeyColumnOutsideBatch(table, columnName)) {
        appendComment(comment, "foreign key, target not in this batch -> add relation by hand");
      }
      if (!column.isNullable() && !type.nullabilityRepresentable()) {
        appendComment(comment, "NOT NULL in database, not representable by this Java type");
        notNullValidations.add(javaName);
      }

      attributeRows.add(new String[] {type.typeDeclaration(), javaName, columnName, comment.toString()});
    }

    appendAlignedRows(buf, attributeRows);
    buf.append('\n');

    buf.append("## indexes\n");
    List<IndexMetaData> indexes = new ArrayList<>(table.getIndexes());
    indexes.sort(Comparator.comparing(IndexMetaData::getIndexName));
    Set<String> seenSignatures = new HashSet<>();
    for (IndexMetaData index : indexes) {
      if (index.isPrimaryIdKey()) {
        continue; // implicit object-id primary key, not a declared index
      }
      List<IndexColumnMetaData> columns = new ArrayList<>(index.getColumns());
      columns.sort(Comparator.comparingInt(IndexColumnMetaData::getPosition));

      // some backends (e.g. H2) auto-create a backing index for a foreign key constraint,
      // which duplicates a hand-created index on the same columns; the model rejects two
      // logically identical indexes, so keep only the first one encountered.
      String signature = index.isUnique() + "|" + columns.stream()
          .map(c -> c.getColumnName().toLowerCase(Locale.ROOT) + (c.isDescending() ? " desc" : ""))
          .reduce((a, b) -> a + "," + b).orElse("");
      if (!seenSignatures.add(signature)) {
        warnings.add("index " + index.getIndexName() + " on " + table.getFullTableName() +
                     " duplicates another index on the same columns -> skipped");
        continue;
      }
      StringBuilder line = new StringBuilder();
      if (index.isUnique()) {
        line.append("unique ");
      }
      line.append("index ").append(index.getIndexName()).append(" := ");
      boolean first = true;
      for (IndexColumnMetaData column : columns) {
        if (!first) {
          line.append(", ");
        }
        first = false;
        if (column.isDescending()) {
          line.append('-');
        }
        line.append(column.getColumnName());
      }
      buf.append(line).append('\n');
    }
    buf.append('\n');

    buf.append("## validations\n");
    for (String attributeName : notNullValidations) {
      buf.append(attributeName).append(": @NotNull\n");
    }
    buf.append('\n');

    buf.append("## relations\n");
    boolean firstRelation = true;
    for (RelationPlan plan : relationByColumn.values()) {
      if (!firstRelation) {
        buf.append('\n');
      }
      firstRelation = false;
      buf.append(plan.targetEntityName()).append(":\n");
      buf.append("    relation = object");
      if (!plan.relationName().equals(plan.targetEntityName())) {
        buf.append(",\n    name = ").append(plan.relationName());
      }
      buf.append('\n');
    }

    return new Result(buf.toString(), warnings);
  }

  private static Map<String, RelationPlan> planRelations(TableMetaData table, Set<String> batchEntityNames, List<String> warnings) {
    Map<String, RelationPlan> plans = new LinkedHashMap<>(); // local column name -> plan
    Set<String> usedRelationNames = new HashSet<>();

    for (ForeignKeyMetaData foreignKey : table.getForeignKeys()) {
      List<ForeignKeyColumnMetaData> columns = foreignKey.getForeignKeyColumns();
      if (columns.size() != 1) {
        warnings.add("foreign key " + foreignKey.getForeignKeyName() + " on " + table.getFullTableName() +
                     " spans " + columns.size() + " columns; Tentackle relations support single-column " +
                     "foreign keys only -> left as plain attributes");
        continue;
      }

      String localColumn = columns.get(0).getForeignKeyColumn();
      String targetEntityName = TableNaming.toEntityName(foreignKey.getPrimaryKeyTable());
      if (!batchEntityNames.contains(targetEntityName)) {
        warnings.add("foreign key " + foreignKey.getForeignKeyName() + " on " + table.getFullTableName() +
                     " references " + foreignKey.getFullPrimaryKeyTableName() +
                     ", which is not part of this reveng run -> left as a plain attribute");
        continue;
      }

      String baseName = TableNaming.toRelationBaseName(localColumn);
      String relationName = baseName.equalsIgnoreCase(targetEntityName) ? targetEntityName : baseName;
      if (!usedRelationNames.add(relationName)) {
        // extremely unlikely (two FKs deriving the same disambiguated name); make it unique
        int suffix = 2;
        String candidate;
        do {
          candidate = relationName + suffix++;
        } while (!usedRelationNames.add(candidate));
        relationName = candidate;
      }
      String javaAttributeName = StringHelper.firstToLower(relationName) + "Id";

      plans.put(localColumn, new RelationPlan(localColumn, targetEntityName, relationName, javaAttributeName));
    }

    return plans;
  }

  private static boolean isForeignKeyColumnOutsideBatch(TableMetaData table, String columnName) {
    for (ForeignKeyMetaData foreignKey : table.getForeignKeys()) {
      for (ForeignKeyColumnMetaData column : foreignKey.getForeignKeyColumns()) {
        if (column.getForeignKeyColumn().equalsIgnoreCase(columnName)) {
          return true;
        }
      }
    }
    return false;
  }

  private static void appendComment(StringBuilder comment, String text) {
    if (!comment.isEmpty()) {
      comment.append(" (");
      comment.append(text);
      comment.append(')');
    }
    else {
      comment.append(text);
    }
  }

  private static void appendAlignedRows(StringBuilder buf, List<String[]> rows) {
    int typeWidth = 0;
    int nameWidth = 0;
    int columnWidth = 0;
    for (String[] row : rows) {
      typeWidth = Math.max(typeWidth, row[0].length());
      nameWidth = Math.max(nameWidth, row[1].length());
      columnWidth = Math.max(columnWidth, row[2].length());
    }
    for (String[] row : rows) {
      buf.append(pad(row[0], typeWidth)).append("  ");
      buf.append(pad(row[1], nameWidth)).append("  ");
      if (row[3].isEmpty()) {
        buf.append(row[2]);
      }
      else {
        buf.append(pad(row[2], columnWidth)).append("  ").append(row[3]);
      }
      buf.append('\n');
    }
  }

  private static String pad(String str, int width) {
    if (str.length() >= width) {
      return str;
    }
    return str + " ".repeat(width - str.length());
  }

}
