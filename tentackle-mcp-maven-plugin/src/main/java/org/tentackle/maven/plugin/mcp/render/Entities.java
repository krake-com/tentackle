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

package org.tentackle.maven.plugin.mcp.render;

import org.tentackle.model.Entity;
import org.tentackle.model.Model;
import org.tentackle.model.ModelException;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Locating and ordering entities.
 *
 * @author harald
 */
public final class Entities {

  /** The prefix addressing an entity by its table name. */
  public static final String TABLE_PREFIX = "table:";

  /** The prefix addressing an entity by its class id. */
  public static final String ID_PREFIX = "id:";

  private Entities() {
  }

  /**
   * Sorts entities by name.
   *
   * @param entities the entities
   * @return a new, sorted list
   */
  public static List<Entity> sorted(Collection<Entity> entities) {
    return entities.stream().sorted(Comparator.comparing(Entity::getName)).toList();
  }

  /**
   * Resolves an entity.
   * <p>
   * Accepts the entity name, {@code table:<tableName>} or {@code id:<classId>}. All three appear in the sources
   * an agent is reading, and none of them is reliably the one it has at hand.
   *
   * @param model the model
   * @param spec the entity name, table name, or class id
   * @return the entity, never null
   * @throws ModelException if the model is inconsistent
   * @throws IllegalArgumentException if the specification is malformed or names no entity
   */
  public static Entity resolve(Model model, String spec) throws ModelException {
    if (spec == null || spec.isBlank()) {
      throw new IllegalArgumentException("no entity given");
    }
    String text = spec.trim();
    String lower = text.toLowerCase(Locale.ROOT);

    Entity entity;
    if (lower.startsWith(TABLE_PREFIX)) {
      String tableName = text.substring(TABLE_PREFIX.length()).trim();
      entity = model.getByTableName(tableName);
      if (entity == null) {
        throw new IllegalArgumentException("no entity with table name '" + tableName + '\'');
      }
    }
    else if (lower.startsWith(ID_PREFIX)) {
      String id = text.substring(ID_PREFIX.length()).trim();
      int classId;
      try {
        classId = Integer.parseInt(id);
      }
      catch (NumberFormatException nfx) {
        throw new IllegalArgumentException("class id '" + id + "' is not a number", nfx);
      }
      entity = model.getByClassId(classId);
      if (entity == null) {
        throw new IllegalArgumentException("no entity with class id " + classId);
      }
    }
    else {
      entity = model.getByEntityName(text);
      if (entity == null) {
        throw new IllegalArgumentException("no entity named '" + text + "'. Use model_list_entities to see " +
                                           "what is loaded, or address the entity as '" + TABLE_PREFIX +
                                           "<tableName>' or '" + ID_PREFIX + "<classId>'");
      }
    }
    return entity;
  }

  /**
   * Returns whether an entity gets a table of its own in the generated DDL.
   * <p>
   * Neither "is abstract" nor "has a table name" is the criterion: the abstract root of a {@code single}- or
   * {@code multi}-table hierarchy provides the table its concrete sub entities are stored in, and a
   * {@code provided} entity is mapped to a table maintained elsewhere. This is the rule the
   * {@code tentackle-sql-maven-plugin} generates by, so that what is rendered here is what the build creates.
   *
   * @param entity the entity
   * @return true if the entity provides its own table
   */
  public static boolean providesTable(Entity entity) {
    return entity.getTableProvidingEntity() == entity && !entity.getOptions().isProvided();
  }

  /**
   * Renders a relation as a short, readable phrase.
   *
   * @param relation the relation
   * @return the phrase
   */
  public static String describe(org.tentackle.model.Relation relation) {
    StringBuilder buf = new StringBuilder(64);
    buf.append(relation.getEntity().getName()).append('.').append(relation.getName())
       .append(" -> ").append(target(relation));
    List<String> flags = Flags.of(relation);
    if (!flags.isEmpty()) {
      buf.append(" (").append(Flags.join(flags)).append(')');
    }
    return buf.toString();
  }

  /**
   * Renders a path of relations as an arrow chain.
   *
   * @param path the relations, in traversal order
   * @return the rendered path
   */
  public static String describePath(List<org.tentackle.model.Relation> path) {
    if (path.isEmpty()) {
      return "(empty)";
    }
    StringBuilder buf = new StringBuilder(80);
    buf.append(path.getFirst().getEntity().getName());
    for (org.tentackle.model.Relation relation : path) {
      buf.append(" --").append(relation.getName()).append("--> ").append(target(relation));
    }
    return buf.toString();
  }

  /**
   * Names what a relation points at.
   * <p>
   * A relation to a class outside the model has no foreign entity, only the class name it was written with.
   *
   * @param relation the relation
   * @return the name of the foreign entity, or the class name it refers to
   */
  private static String target(org.tentackle.model.Relation relation) {
    return relation.getForeignEntity() == null ? relation.getClassName() : relation.getForeignEntity().getName();
  }

}
