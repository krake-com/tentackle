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

package org.tentackle.pdo;

import java.lang.reflect.Method;
import java.util.Objects;

/**
 * A member of a PDO.<br>
 * Members can be either attributes or relations.
 * Their getters are annotated with {@link org.tentackle.session.Persistent}.
 */
public class PdoMember {

  /**
   * The member type.
   */
  public enum Type {
    /** member is an attribute. */
    ATTRIBUTE("attribute"),

    /** member is a relation. */
    RELATION("relation");

    private final String text;

    Type(String text) {
      this.text = text;
    }

    /**
     * Gets the member type as a lowercase string.
     *
     * @return the type as string
     */
    public String getText() {
      return text;
    }
  }


  private final Type type;
  private final String name;
  private final Method getter;
  private final Method setter;
  private final boolean component;
  private final boolean parent;
  private final boolean embedded;
  private final boolean domainKey;
  private final String comment;
  private final int ordinal;

  /**
   * Creates a member.
   *
   * @param type attribute or relation
   * @param name the member's name
   * @param getter the getter method
   * @param setter the optional setter method
   * @param component true if the member is a component
   * @param parent true if the member is a parent relation
   * @param embedded true if the member is an embedded PDO
   * @param domainKey true if the member is domain key
   * @param comment the comment
   * @param ordinal the model ordinal
   */
  public PdoMember(Type type, String name, Method getter, Method setter,
                   boolean component, boolean parent, boolean embedded, boolean domainKey, String comment, int ordinal) {
    this.type = type;
    this.name = name;
    this.getter = getter;
    this.setter = setter;
    this.component = component;
    this.parent = parent;
    this.embedded = embedded;
    this.domainKey = domainKey;
    this.comment = comment;
    this.ordinal = ordinal;
  }

  /**
   * Gets the member type.
   *
   * @return the type
   */
  public Type getType() {
    return type;
  }

  /**
   * Gets the member's name.
   *
   * @return the name (first letter capitalized)
   */
  public String getName() {
    return name;
  }

  /**
   * The getter method.
   *
   * @return the getter, never null
   */
  public Method getGetter() {
    return getter;
  }

  /**
   * The setter method.
   *
   * @return the setter, null if no setter
   */
  public Method getSetter() {
    return setter;
  }

  /**
   * Returns whether member is a component of the PDO.<br>
   * Notice that attributes are always components.
   *
   * @return true if component
   */
  public boolean isComponent() {
    return component;
  }

  /**
   * Returns whether this is a parent relation.
   *
   * @return true if parent relation
   */
  public boolean isParent() {
    return parent;
  }

  /**
   * Returns whether this is an embedded PDO.
   *
   * @return true if embedded PDO
   */
  public boolean isEmbedded() {
    return embedded;
  }

  /**
   * Returns whether this is a domain key.
   *
   * @return true if domain key
   */
  public boolean isDomainKey() {
    return domainKey;
  }

  /**
   * Gets the optional comment.
   *
   * @return the comment, null if none
   */
  public String getComment() {
    return comment;
  }

  /**
   * Gets the model ordinal for sorting.
   *
   * @return the ordinal
   */
  public int getOrdinal() {
    return ordinal;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    PdoMember member = (PdoMember) o;
    return Objects.equals(name, member.name);
  }

  @Override
  public int hashCode() {
    return Objects.hash(name);
  }

  @Override
  public String toString() {
    StringBuilder buf = new StringBuilder();
    if (type == Type.RELATION) {
      if (component) {
        buf.append("component ");
      }
      if (parent) {
        buf.append("parent ");
      }
    }
    buf.append(type.text).append(' ').append(name);
    if (comment != null) {
      buf.append(" \"").append(comment).append('"');
    }
    return buf.toString();
  }
}
