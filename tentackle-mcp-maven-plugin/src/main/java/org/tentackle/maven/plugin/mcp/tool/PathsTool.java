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
import org.tentackle.maven.plugin.mcp.render.TextTable;
import org.tentackle.model.Entity;
import org.tentackle.model.ModelException;
import org.tentackle.model.Relation;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Shows how two entities are connected.
 * <p>
 * The relation graph is the part of the model that is hardest to reconstruct by reading sources: a relation is
 * written in one entity and answers questions in the other, and a path of three hops is written in three files.
 *
 * @author harald
 */
public class PathsTool implements ModelTool {

  /** The tool name. */
  public static final String NAME = "model_paths";

  /** The kinds of connection that can be traced. */
  public static final String[] KINDS = { "relations", "composite", "embedding" };

  /** The directions of a relation path may be traversed in. */
  public static final String[] DIRECTIONS = { "forward", "reverse", "both" };

  /** The default maximum number of hops. */
  public static final int DEFAULT_MAX_DEPTH = 4;

  private final int maxResults;

  /**
   * Creates the tool.
   *
   * @param maxResults the hard upper bound for the number of paths returned
   */
  public PathsTool(int maxResults) {
    this.maxResults = maxResults;
  }

  @Override
  public String getName() {
    return NAME;
  }

  @Override
  public String getTitle() {
    return "Paths between entities";
  }

  @Override
  public String getDescription() {
    return """
        Shows how entities are connected. With 'kind' = relations it searches the relation graph from 'from' \
        to 'to' and lists every path up to 'maxDepth' hops; omit 'to' to list the direct neighbors instead. \
        With 'kind' = composite it shows the aggregate paths from the root entity down to 'from', and with \
        'kind' = embedding the paths along which 'from' is embedded. Use it before writing a query or a \
        relation, to see whether a link already exists.""";
  }

  @Override
  public String getInputSchema() {
    return new SchemaBuilder()
        .requiredString("from", "The entity to start at: a name, 'table:<tableName>' or 'id:<classId>'.")
        .string("to", "The entity to reach. Omit to list the direct neighbors of 'from'.")
        .enumeration("kind", "Which kind of connection to trace.", "relations", KINDS)
        .enumeration("direction", "Whether to follow relations declared by the entity ('forward'), relations " +
                                  "pointing at it ('reverse') or both.", "both", DIRECTIONS)
        .integer("maxDepth", "Maximum number of hops when searching the relation graph.", DEFAULT_MAX_DEPTH)
        .integer("limit", "Maximum number of paths to return.", maxResults)
        .build();
  }

  @Override
  public String execute(ModelSession session, ToolArguments args) throws ModelException {
    Entity from = Entities.resolve(session.getModel(), args.requiredString("from"));
    String toSpec = args.string("to", null);
    Entity to = toSpec == null ? null : Entities.resolve(session.getModel(), toSpec);
    String kind = args.string("kind", "relations").toLowerCase(Locale.ROOT);
    String direction = args.string("direction", "both").toLowerCase(Locale.ROOT);
    int maxDepth = Math.max(1, args.integer("maxDepth", DEFAULT_MAX_DEPTH));
    // a limit below 1 would end the search before it starts and report a path as missing
    int limit = Math.clamp(args.integer("limit", maxResults), 1, maxResults);

    if (!List.of(KINDS).contains(kind)) {
      throw new IllegalArgumentException("unknown kind '" + kind + "', expected one of " + String.join(", ", KINDS));
    }
    if (!List.of(DIRECTIONS).contains(direction)) {
      throw new IllegalArgumentException("unknown direction '" + direction + "', expected one of " +
                                         String.join(", ", DIRECTIONS));
    }

    return switch (kind) {
      case "composite" -> describeSetOfPaths(from.getAllCompositePaths(), to,
                                             "composite paths from the aggregate root down to " + from.getName());
      case "embedding" -> describeSetOfPaths(from.getEmbeddingPaths(), to,
                                             "embedding paths of " + from.getName());
      default -> to == null ? neighbors(from, direction) : search(from, to, direction, maxDepth, limit);
    };
  }

  /**
   * One traversal step: a relation, and whether it was followed in its declared direction.
   *
   * @param relation the relation
   * @param forward true if followed from the declaring entity to the foreign entity
   */
  private record Step(Relation relation, boolean forward) {

    Entity target() {
      return forward ? relation.getForeignEntity() : relation.getEntity();
    }

    @Override
    public String toString() {
      return (forward ? " --" : " <--") + relation.getName() + (forward ? "--> " : "-- ") + target().getName();
    }
  }

  private String neighbors(Entity from, String direction) {
    StringBuilder buf = new StringBuilder(1024);
    buf.append("direct neighbors of ").append(from.getName()).append('\n');

    if (!"reverse".equals(direction)) {
      buf.append("\n## outgoing (relations declared by ").append(from.getName()).append(")\n");
      TextTable table = new TextTable("RELATION", "TYPE", "TARGET", "SELECT", "FLAGS");
      for (Relation relation : from.getRelationsIncludingInherited()) {
        table.add(relation.getName(),
                  relation.getRelationType(),
                  relation.getForeignEntity() == null ? relation.getClassName()
                                                      : relation.getForeignEntity().getName(),
                  relation.getSelectionType(),
                  Flags.join(Flags.of(relation)));
      }
      buf.append(table.isEmpty() ? "none\n" : table.toString());
    }

    if (!"forward".equals(direction)) {
      buf.append("\n## incoming (relations pointing at ").append(from.getName()).append(")\n");
      TextTable table = new TextTable("ENTITY", "RELATION", "TYPE", "FLAGS");
      for (Relation relation : from.getReferencingRelationsIncludingInherited()) {
        table.add(relation.getEntity().getName(),
                  relation.getName(),
                  relation.getRelationType(),
                  Flags.join(Flags.of(relation)));
      }
      buf.append(table.isEmpty() ? "none\n" : table.toString());
    }

    return buf.toString();
  }

  private String search(Entity from, Entity to, String direction, int maxDepth, int limit) {
    List<List<Step>> found = new ArrayList<>();
    List<Entity> visited = new ArrayList<>();
    visited.add(from);
    walk(from, to, direction, maxDepth, new ArrayList<>(), visited, found, limit);

    if (found.isEmpty()) {
      return "no path from " + from.getName() + " to " + to.getName() +
             " within " + maxDepth + " hops (direction " + direction + ").\n" +
             "Try a larger 'maxDepth', or direction 'both'.";
    }

    StringBuilder buf = new StringBuilder(1024);
    buf.append(found.size()).append(found.size() == 1 ? " path" : " paths")
       .append(" from ").append(from.getName()).append(" to ").append(to.getName())
       .append(" (direction ").append(direction).append(", up to ").append(maxDepth).append(" hops)\n");
    for (List<Step> path : found) {
      buf.append('\n').append(from.getName());
      for (Step step : path) {
        buf.append(step);
      }
      buf.append("\n  ").append(path.size()).append(path.size() == 1 ? " hop" : " hops");
      List<String> flags = new ArrayList<>();
      for (Step step : path) {
        flags.addAll(Flags.of(step.relation()));
      }
      if (!flags.isEmpty()) {
        buf.append(": ").append(String.join(", ", flags.stream().distinct().toList()));
      }
      buf.append('\n');
    }
    return buf.toString();
  }

  private void walk(Entity current, Entity target, String direction, int maxDepth,
                    List<Step> path, List<Entity> visited, List<List<Step>> found, int limit) {
    if (found.size() >= limit || path.size() >= maxDepth) {
      return;
    }
    for (Step step : steps(current, direction)) {
      Entity next = step.target();
      if (next == null || visited.contains(next)) {
        continue;
      }
      path.add(step);
      if (next == target) {
        found.add(List.copyOf(path));
      }
      else {
        visited.add(next);
        walk(next, target, direction, maxDepth, path, visited, found, limit);
        visited.remove(next);
      }
      path.removeLast();
      if (found.size() >= limit) {
        return;
      }
    }
  }

  private List<Step> steps(Entity entity, String direction) {
    List<Step> steps = new ArrayList<>();
    if (!"reverse".equals(direction)) {
      for (Relation relation : entity.getRelationsIncludingInherited()) {
        steps.add(new Step(relation, true));
      }
    }
    if (!"forward".equals(direction)) {
      for (Relation relation : entity.getReferencingRelationsIncludingInherited()) {
        steps.add(new Step(relation, false));
      }
    }
    return steps;
  }

  private String describeSetOfPaths(Set<List<Relation>> paths, Entity to, String title) {
    List<List<Relation>> selected = new ArrayList<>();
    for (List<Relation> path : paths) {
      if (to == null || path.stream().anyMatch(relation -> relation.getEntity() == to
                                                           || relation.getForeignEntity() == to)) {
        selected.add(path);
      }
    }
    if (selected.isEmpty()) {
      return "no " + title + (to == null ? "" : " involving " + to.getName());
    }
    StringBuilder buf = new StringBuilder(512);
    buf.append(selected.size()).append(' ').append(title).append('\n');
    for (List<Relation> path : selected) {
      buf.append("\n  ").append(Entities.describePath(path));
    }
    return buf.append('\n').toString();
  }

}
