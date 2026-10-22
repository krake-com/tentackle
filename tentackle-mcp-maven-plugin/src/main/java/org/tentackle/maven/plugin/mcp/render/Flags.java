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

import org.tentackle.model.Attribute;
import org.tentackle.model.AttributeOptions;
import org.tentackle.model.Entity;
import org.tentackle.model.EntityOptions;
import org.tentackle.model.ModelException;
import org.tentackle.model.Relation;
import org.tentackle.model.SelectionType;
import org.tentackle.model.TrackType;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Renders the boolean facets of a model element as a compact, greppable list.
 * <p>
 * A row in a table cannot carry thirty columns of booleans, but it can carry the handful that are true.
 *
 * @author harald
 */
public final class Flags {

  /** The entity filters usable in a listing, in the order they are documented. */
  public static final String[] ENTITY_FILTERS = {
      "composite", "abstract", "root", "embedded", "tracked", "remote", "cached", "provided",
      "has_subentities", "has_superentity", "deeply_referenced"
  };

  private Flags() {
  }

  /**
   * Gets the flags of an entity.
   *
   * @param entity the entity
   * @return the flags, empty if the entity is plain
   */
  public static List<String> of(Entity entity) {
    EntityOptions options = entity.getOptions();
    List<String> flags = new ArrayList<>();
    if (entity.isAbstract()) {
      flags.add("abstract");
    }
    if (entity.isComposite()) {
      flags.add("composite");
    }
    if (entity.isRootEntity()) {
      flags.add("root");
    }
    if (entity.isEmbedded()) {
      flags.add("embedded");
    }
    if (options.getTrackType() != null && options.getTrackType() != TrackType.UNTRACKED) {
      flags.add(options.getTrackType().name().toLowerCase(Locale.ROOT));
    }
    if (options.isRemote()) {
      flags.add("remote");
    }
    if (options.isCached()) {
      flags.add("cached");
    }
    if (options.isProvided()) {
      flags.add("provided");
    }
    if (options.isNoPrimaryKey()) {
      flags.add("nopkey");
    }
    if (entity.isDeeplyReferenced()) {
      flags.add("deeply_referenced");
    }
    flags.addAll(options.getStereotypes().stream().map(stereotype -> '#' + stereotype).toList());
    return flags;
  }

  /**
   * Returns whether an entity matches a filter.
   *
   * @param entity the entity
   * @param filter the filter name, see {@link #ENTITY_FILTERS}
   * @return true if the entity matches
   * @throws IllegalArgumentException if the filter is unknown
   */
  public static boolean matches(Entity entity, String filter) {
    return switch (filter.trim().toLowerCase(Locale.ROOT)) {
      case "composite" -> entity.isComposite();
      case "abstract" -> entity.isAbstract();
      case "root" -> entity.isRootEntity();
      case "embedded" -> entity.isEmbedded();
      case "tracked" -> entity.isTracked();
      case "remote" -> entity.getOptions().isRemote();
      case "cached" -> entity.getOptions().isCached();
      case "provided" -> entity.getOptions().isProvided();
      case "has_subentities" -> !entity.getSubEntities().isEmpty();
      case "has_superentity" -> entity.getSuperEntity() != null;
      case "deeply_referenced" -> entity.isDeeplyReferenced();
      default -> throw new IllegalArgumentException("unknown filter '" + filter + "', expected one of " +
                                                    String.join(", ", ENTITY_FILTERS));
    };
  }

  /**
   * Gets the flags of an attribute.
   *
   * @param attribute the attribute
   * @return the flags, empty if the attribute is plain
   * @throws ModelException if the model is inconsistent
   */
  public static List<String> of(Attribute attribute) throws ModelException {
    AttributeOptions options = attribute.getOptions();
    List<String> flags = new ArrayList<>();
    if (!attribute.isNullable()) {
      flags.add("notnull");
    }
    if (options.isDomainKey()) {
      flags.add("key");
    }
    if (options.isContextId()) {
      flags.add("contextid");
    }
    if (attribute.isImplicit()) {
      flags.add("implicit");
    }
    if (attribute.isEmbedded()) {
      flags.add("embedded");
    }
    if (attribute.isHidden()) {
      flags.add("hidden");
    }
    if (options.isReadOnly()) {
      flags.add("readonly");
    }
    if (options.isWriteOnly()) {
      flags.add("writeonly");
    }
    if (options.isUTC()) {
      flags.add("utc");
    }
    if (options.isWithTimezone()) {
      flags.add("tz");
    }
    if (options.isPartOfNormText()) {
      flags.add("normtext");
    }
    if (options.isShallow()) {
      flags.add("shallow");
    }
    flags.addAll(options.getStereotypes().stream().map(stereotype -> '#' + stereotype).toList());
    return flags;
  }

  /**
   * Gets the flags of a relation.
   *
   * @param relation the relation
   * @return the flags, empty if the relation is plain
   */
  public static List<String> of(Relation relation) {
    List<String> flags = new ArrayList<>();
    if (relation.isComposite()) {
      flags.add("composite");
    }
    if (relation.isEmbedding()) {
      flags.add("embedding");
    }
    if (relation.isEmbedded()) {
      flags.add("embedded");
    }
    if (relation.isTracked()) {
      flags.add("tracked");
    }
    if (relation.isDeletionCascaded()) {
      flags.add("cascade");
    }
    if (relation.isReadOnly()) {
      flags.add("readonly");
    }
    if (relation.isWriteOnly()) {
      flags.add("writeonly");
    }
    if (relation.isSerialized()) {
      flags.add("serialized");
    }
    if (relation.isShallow()) {
      flags.add("shallow");
    }
    if (relation.isImmutable()) {
      flags.add("immutable");
    }
    if (relation.isReversed()) {
      flags.add("reversed");
    }
    if (relation.isDeepReference()) {
      flags.add("deep");
    }
    if (relation.getNmRelation() != null) {
      flags.add("nm");
    }
    flags.addAll(relation.getStereotypes().stream().map(stereotype -> '#' + stereotype).toList());
    return flags;
  }

  /**
   * Joins flags into a single cell value.
   *
   * @param flags the flags
   * @return the joined flags, an empty string if there are none
   */
  public static String join(List<String> flags) {
    return String.join(",", flags);
  }

  /**
   * Renders the selection strategy of a relation.
   * <p>
   * An eager relation that cascades beyond the default single level is rendered with its
   * depth appended, e.g. {@code EAGER:2}, so the real fetch plan is visible.
   *
   * @param relation the relation
   * @return the cell value
   */
  public static String selection(Relation relation) {
    String selection = relation.getSelectionType().toString();
    if (relation.getSelectionType() == SelectionType.EAGER && relation.getEagerDepth() > 1) {
      selection += ":" + relation.getEagerDepth();
    }
    return selection;
  }

}
