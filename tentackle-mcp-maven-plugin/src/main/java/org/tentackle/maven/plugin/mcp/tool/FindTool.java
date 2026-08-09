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

package org.tentackle.maven.plugin.mcp.tool;

import org.tentackle.maven.plugin.mcp.ModelSession;
import org.tentackle.maven.plugin.mcp.render.Entities;
import org.tentackle.maven.plugin.mcp.render.NamePattern;
import org.tentackle.maven.plugin.mcp.render.TextTable;
import org.tentackle.model.Attribute;
import org.tentackle.model.Entity;
import org.tentackle.model.Index;
import org.tentackle.model.ModelException;
import org.tentackle.model.Relation;

import java.util.List;
import java.util.Locale;

/**
 * Searches the model by name.
 * <p>
 * The question "where does {@code custId} appear?" cannot be answered by listing entities, and grepping the
 * sources misses everything the model derived. One pattern across six kinds of the element answers it.
 *
 * @author harald
 */
public class FindTool implements ModelTool {

  /** The tool name. */
  public static final String NAME = "model_find";

  /** The kinds of the element that can be searched. */
  public static final String[] KINDS = { "entity", "table", "attribute", "column", "relation", "index" };

  private final int maxResults;

  /**
   * Creates the tool.
   *
   * @param maxResults the hard upper bound for the number of rows returned
   */
  public FindTool(int maxResults) {
    this.maxResults = maxResults;
  }

  @Override
  public String getName() {
    return NAME;
  }

  @Override
  public String getTitle() {
    return "Find in model";
  }

  @Override
  public String getDescription() {
    return """
        Searches entity, table, attribute, column, relation and index names across the whole model, optionally \
        including comments. Use it when you know a name fragment but not where it lives -- for instance which \
        entities carry a 'customerId' attribute, or which table a column belongs to. The pattern is a \
        case-insensitive substring, a glob with * and ?, or a regular expression prefixed with 'regex:'.""";
  }

  @Override
  public String getInputSchema() {
    return new SchemaBuilder()
        .requiredString("pattern", "A case-insensitive substring, a glob with * and ?, or a regular " +
                                   "expression when prefixed with 'regex:'.")
        .stringArray("kinds", "The kinds of element to search. Defaults to all of them.", KINDS)
        .bool("comments", "Also search the comments, not just the names.", false)
        .integer("limit", "Maximum number of hits to return.", maxResults)
        .build();
  }

  @Override
  public String execute(ModelSession session, ToolArguments args) throws ModelException {
    NamePattern pattern = new NamePattern(args.requiredString("pattern"));
    List<String> kinds = args.list("kinds", List.of(KINDS));
    boolean comments = args.bool("comments", false);
    int limit = Math.clamp(args.integer("limit", maxResults), 1, maxResults);

    for (String kind : kinds) {
      if (!List.of(KINDS).contains(kind.toLowerCase(Locale.ROOT))) {
        throw new IllegalArgumentException("unknown kind '" + kind + "', expected one of " +
                                           String.join(", ", KINDS));
      }
    }

    TextTable table = new TextTable("KIND", "ENTITY", "NAME", "DETAIL");
    int matched = 0;

    for (Entity entity : Entities.sorted(session.getModel().getAllEntities())) {
      if (contains(kinds, "entity") && matches(pattern, entity.getName(), comments, entity.getOptions().getComment())) {
        matched++;
        add(table, limit, "entity", entity.getName(), entity.getName(),
            "id " + entity.getClassId() + ", table " + entity.getTableName());
      }
      if (contains(kinds, "table") && pattern.matches(entity.getTableName())) {
        matched++;
        add(table, limit, "table", entity.getName(), entity.getTableName(),
            "alias " + entity.getTableAlias());
      }
      if (contains(kinds, "attribute") || contains(kinds, "column")) {
        for (Attribute attribute : entity.getAttributes()) {
          if (contains(kinds, "attribute")
              && matches(pattern, attribute.getName(), comments, attribute.getOptions().getComment())) {
            matched++;
            add(table, limit, "attribute", entity.getName(), attribute.getName(),
                attribute.getJavaType() + " -> " + attribute.getColumnName());
          }
          if (contains(kinds, "column") && pattern.matches(attribute.getColumnName())) {
            matched++;
            add(table, limit, "column", entity.getName(), attribute.getColumnName(),
                entity.getTableName() + '.' + attribute.getColumnName());
          }
        }
      }
      if (contains(kinds, "relation")) {
        for (Relation relation : entity.getRelations()) {
          if (matches(pattern, relation.getName(), comments, relation.getComment())) {
            matched++;
            add(table, limit, "relation", entity.getName(), relation.getName(),
                relation.getRelationType() + " -> " +
                (relation.getForeignEntity() == null ? relation.getClassName()
                                                     : relation.getForeignEntity().getName()));
          }
        }
      }
      if (contains(kinds, "index")) {
        for (Index index : entity.getIndexes()) {
          if (matches(pattern, index.getName(), comments, index.getComment())) {
            matched++;
            add(table, limit, "index", entity.getName(), index.getName(),
                index.isUnique() ? "unique" : "");
          }
        }
      }
    }

    if (matched == 0) {
      return "nothing matches " + pattern + " in " + String.join(", ", kinds);
    }

    StringBuilder buf = new StringBuilder(1024);
    buf.append(matched).append(" hits for ").append(pattern);
    if (table.size() < matched) {
      buf.append(", showing the first ").append(table.size()).append(" (raise 'limit' or narrow 'pattern')");
    }
    return buf.append("\n\n").append(table).toString();
  }

  private void add(TextTable table, int limit, Object... cells) {
    if (table.size() < limit) {
      table.add(cells);
    }
  }

  private boolean contains(List<String> kinds, String kind) {
    return kinds.stream().anyMatch(k -> k.equalsIgnoreCase(kind));
  }

  private boolean matches(NamePattern pattern, String name, boolean comments, String comment) {
    return pattern.matches(name) || comments && comment != null && pattern.matches(comment);
  }

}
