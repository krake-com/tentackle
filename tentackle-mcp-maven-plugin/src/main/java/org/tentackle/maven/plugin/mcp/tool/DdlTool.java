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
import org.tentackle.model.Entity;
import org.tentackle.model.ForeignKey;
import org.tentackle.model.Index;
import org.tentackle.model.ModelException;
import org.tentackle.sql.Backend;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Renders the DDL of the model.
 * <p>
 * Text only: nothing is executed and no database is contacted. Generating and running SQL is the
 * {@code tentackle-sql-maven-plugin}'s job.
 *
 * @author harald
 */
public class DdlTool implements ModelTool {

  /** The tool name. */
  public static final String NAME = "model_ddl";

  /** The parts of the DDL that can be rendered. */
  public static final String[] PARTS = { "table", "indexes", "foreignkeys" };

  /**
   * Creates the tool.
   */
  public DdlTool() {
  }

  @Override
  public String getName() {
    return NAME;
  }

  @Override
  public String getTitle() {
    return "Model DDL";
  }

  @Override
  public String getDescription() {
    return """
        Renders CREATE TABLE, CREATE INDEX and foreign key statements for one or more entities and a given \
        database backend. This is text for reading, not for running: nothing is executed and no database is \
        contacted. Requires the plugin's 'backends' parameter to be configured. Omit 'entity' to render the \
        whole model. Only entities with a table of their own are rendered: an entity of a single- or \
        multi-table hierarchy is stored in the table of its hierarchy root, and a 'provided' table is \
        maintained outside the application.""";
  }

  @Override
  public String getInputSchema() {
    return new SchemaBuilder()
        .stringArray("entity", "The entities to render, each a name, 'table:<tableName>' or 'id:<classId>'. " +
                               "Omit to render the whole model.")
        .string("backend", "The database backend, for instance 'PostgreSQL'. Defaults to the first " +
                           "configured backend.")
        .stringArray("parts", "Which statements to render. Defaults to all of them.", PARTS)
        .build();
  }

  @Override
  public String execute(ModelSession session, ToolArguments args) throws ModelException {
    if (session.getBackends().isEmpty() && args.string("backend", null) == null) {
      return "no backends configured: set the plugin's 'backends' parameter (for instance " +
             "<backends>PostgreSQL</backends>) or pass the 'backend' argument. Known backends: " +
             String.join(", ", ModelSession.allBackendNames());
    }

    String backendName = args.string("backend", null);
    Backend backend = backendName == null ? session.getBackends().iterator().next()
                                          : session.getBackend(backendName);
    List<String> parts = args.list("parts", List.of(PARTS));
    for (String part : parts) {
      if (!List.of(PARTS).contains(part.toLowerCase(Locale.ROOT))) {
        throw new IllegalArgumentException("unknown part '" + part + "', expected one of " +
                                           String.join(", ", PARTS));
      }
    }

    List<String> specs = args.list("entity", List.of());
    List<Entity> entities = new ArrayList<>();
    if (specs.isEmpty()) {
      entities.addAll(Entities.sorted(session.getModel().getAllEntities()));
    }
    else {
      for (String spec : specs) {
        entities.add(Entities.resolve(session.getModel(), spec));
      }
    }

    StringBuilder buf = new StringBuilder(4096);
    buf.append("-- backend: ").append(backend.getName()).append('\n');

    for (Entity entity : entities) {
      if (!Entities.providesTable(entity)) {
        // stored in another entity's table, or maintained externally: say so if the caller asked for it by name
        if (!specs.isEmpty()) {
          Entity tableProvider = entity.getTableProvidingEntity();
          buf.append("\n-- ").append(entity.getName()).append(": no table of its own, ")
             .append(tableProvider == null ? "not mapped to a table"
                     : tableProvider == entity ? "declared as 'provided' and maintained externally"
                     : "stored in the table of " + tableProvider.getName())
             .append('\n');
        }
        continue;
      }
      if (contains(parts, "table")) {
        buf.append('\n').append(entity.sqlCreateTable(backend)).append('\n');
      }
      if (contains(parts, "indexes")) {
        for (Index index : entity.getTableIndexes()) {
          buf.append(index.sqlCreateIndex(backend, entity)).append('\n');
        }
      }
    }

    if (contains(parts, "foreignkeys")) {
      buf.append("\n-- foreign keys\n");
      int count = 0;
      for (ForeignKey foreignKey : session.getModel().getForeignKeys()) {
        if (specs.isEmpty() || entities.contains(foreignKey.getReferencingEntity())
                               || entities.contains(foreignKey.getReferencedEntity())) {
          buf.append(foreignKey.sqlCreateForeignKey(backend)).append('\n');
          count++;
        }
      }
      if (count == 0) {
        buf.append("-- none\n");
      }
    }

    return buf.toString();
  }

  private boolean contains(List<String> parts, String part) {
    return parts.stream().anyMatch(p -> p.equalsIgnoreCase(part));
  }

}
