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
import org.tentackle.model.Integrity;
import org.tentackle.model.Model;
import org.tentackle.model.ModelException;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Reports what model is loaded and how it is shaped.
 * <p>
 * The entry point: an agent that starts here knows the model's size, its inheritance hierarchies, its aggregate
 * roots, and whether the server is configured well enough to be trusted.
 *
 * @author harald
 */
public class OverviewTool implements ModelTool {

  /** The tool name. */
  public static final String NAME = "model_overview";

  /**
   * Creates the tool.
   */
  public OverviewTool() {
  }

  @Override
  public String getName() {
    return NAME;
  }

  @Override
  public String getTitle() {
    return "Model overview";
  }

  @Override
  public String getDescription() {
    return """
        Start here. Reports the loaded Tentackle model: sources, backends, effective model defaults, entity \
        count, inheritance hierarchies, aggregate roots, the referential integrity distribution and any \
        configuration warnings. Takes no arguments. Follow up with model_list_entities and \
        model_describe_entity.""";
  }

  @Override
  public String getInputSchema() {
    return new SchemaBuilder().build();
  }

  @Override
  public String execute(ModelSession session, ToolArguments args) throws ModelException {
    Model model = session.getModel();
    StringBuilder buf = new StringBuilder(1024);

    buf.append("model:          ").append(model.getName());
    for (String dir : session.getConfiguration().modelDirs()) {
      buf.append("\nsource dir:     ").append(dir);
    }
    if (session.getConfiguration().fromResources()) {
      buf.append("\nsource:         plugin classpath resources");
    }
    buf.append("\nmodel files:    ").append(session.getModelFileCount());
    buf.append("\nmodel defaults: ").append(model.getModelDefaults() == null ? "none" : model.getModelDefaults());
    buf.append("\nbackends:       ")
       .append(session.backendNames().isEmpty() ? "none" : String.join(", ", session.backendNames()));

    List<Entity> entities = Entities.sorted(model.getAllEntities());
    int abstractCount = 0;
    int compositeCount = 0;
    List<String> rootEntities = new ArrayList<>();
    List<Entity> hierarchyRoots = new ArrayList<>();
    Map<Integrity, Integer> integrityCounts = new EnumMap<>(Integrity.class);

    for (Entity entity : entities) {
      if (entity.isAbstract()) {
        abstractCount++;
      }
      if (entity.isComposite()) {
        compositeCount++;
      }
      if (entity.isRootEntity()) {
        rootEntities.add(entity.getName());
      }
      if (entity.getSuperEntity() == null && !entity.getSubEntities().isEmpty()) {
        hierarchyRoots.add(entity);
      }
      Integrity integrity = entity.getIntegrity();
      if (integrity != null) {
        integrityCounts.merge(integrity, 1, Integer::sum);
      }
    }

    buf.append("\nentities:       ").append(entities.size())
       .append(" (").append(abstractCount).append(" abstract, ")
       .append(compositeCount).append(" composite)");

    buf.append("\n\n## aggregate roots (").append(rootEntities.size()).append(")\n");
    buf.append(rootEntities.isEmpty() ? "none" : String.join(", ", rootEntities));

    buf.append("\n\n## inheritance hierarchies (").append(hierarchyRoots.size()).append(')');
    if (hierarchyRoots.isEmpty()) {
      buf.append("\nnone");
    }
    else {
      for (Entity root : hierarchyRoots) {
        buf.append('\n').append(root.getName())
           .append("  (").append(root.getHierarchyInheritanceType()).append(root.isAbstract() ? ", abstract" : "")
           .append(')');
        appendSubEntities(buf, root, 1);
      }
    }

    buf.append("\n\n## integrity");
    if (integrityCounts.isEmpty()) {
      buf.append("\nnone");
    }
    else {
      for (Map.Entry<Integrity, Integer> entry : integrityCounts.entrySet()) {
        buf.append('\n').append(entry.getKey()).append(": ").append(entry.getValue());
      }
    }

    List<String> warnings = session.getWarnings();
    if (!warnings.isEmpty()) {
      buf.append("\n\n## warnings");
      for (String warning : warnings) {
        buf.append("\n- ").append(warning);
      }
    }

    return buf.toString();
  }

  private void appendSubEntities(StringBuilder buf, Entity entity, int level) {
    for (Entity sub : entity.getSubEntities()) {
      buf.append('\n').repeat("  ", level).append("+- ").append(sub.getName());
      if (sub.isAbstract()) {
        buf.append(" (abstract)");
      }
      appendSubEntities(buf, sub, level + 1);
    }
  }

}
