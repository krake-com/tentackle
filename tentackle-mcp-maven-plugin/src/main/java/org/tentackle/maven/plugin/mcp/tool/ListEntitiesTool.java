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
import org.tentackle.maven.plugin.mcp.render.Flags;
import org.tentackle.maven.plugin.mcp.render.NamePattern;
import org.tentackle.maven.plugin.mcp.render.TextTable;
import org.tentackle.model.Entity;
import org.tentackle.model.ModelException;

import java.util.List;

/**
 * Lists the entities of the model.
 *
 * @author harald
 */
public class ListEntitiesTool implements ModelTool {

  /** The tool name. */
  public static final String NAME = "model_list_entities";

  private final int maxResults;

  /**
   * Creates the tool.
   *
   * @param maxResults the hard upper bound for the number of rows returned
   */
  public ListEntitiesTool(int maxResults) {
    this.maxResults = maxResults;
  }

  @Override
  public String getName() {
    return NAME;
  }

  @Override
  public String getTitle() {
    return "List entities";
  }

  @Override
  public String getDescription() {
    return """
        Lists the entities of the model as a column-aligned index of name, class id, table, super entity and \
        flags. Narrow it with 'pattern' (case-insensitive substring, a glob with * and ?, or 'regex:<expr>') \
        and 'filter' (all filters must match). Use this to find an entity's exact name before calling \
        model_describe_entity.""";
  }

  @Override
  public String getInputSchema() {
    return new SchemaBuilder()
        .string("pattern", "Matches the entity name: a case-insensitive substring, a glob with * and ?, " +
                           "or a regular expression when prefixed with 'regex:'. Omit to list everything.")
        .stringArray("filter", "Only entities matching every listed facet.", Flags.ENTITY_FILTERS)
        .integer("limit", "Maximum number of rows to return.", maxResults)
        .build();
  }

  @Override
  public String execute(ModelSession session, ToolArguments args) throws ModelException {
    NamePattern pattern = new NamePattern(args.string("pattern", null));
    List<String> filters = args.list("filter", List.of());
    int limit = Math.min(args.integer("limit", maxResults), maxResults);

    TextTable table = new TextTable("ENTITY", "ID", "TABLE", "EXTENDS", "FLAGS");
    int matched = 0;
    for (Entity entity : Entities.sorted(session.getModel().getAllEntities())) {
      if (!pattern.matches(entity.getName())) {
        continue;
      }
      if (!filters.stream().allMatch(filter -> Flags.matches(entity, filter))) {
        continue;
      }
      matched++;
      if (table.size() < limit) {
        table.add(entity.getName(),
                  entity.getClassId(),
                  entity.getTableName(),
                  entity.getSuperEntity() == null ? "" : entity.getSuperEntity().getName(),
                  Flags.join(Flags.of(entity)));
      }
    }

    if (matched == 0) {
      return "no entity matches pattern " + pattern +
             (filters.isEmpty() ? "" : " and filter " + String.join(", ", filters));
    }

    StringBuilder buf = new StringBuilder(1024);
    buf.append(matched).append(" of ").append(session.getModel().getAllEntities().size()).append(" entities");
    if (table.size() < matched) {
      buf.append(", showing the first ").append(table.size()).append(" (raise 'limit' or narrow 'pattern')");
    }
    return buf.append("\n\n").append(table).toString();
  }

}
