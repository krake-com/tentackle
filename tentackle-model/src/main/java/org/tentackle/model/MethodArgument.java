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

package org.tentackle.model;

import org.tentackle.common.Constants;
import org.tentackle.common.StringHelper;

/**
 * Argument to use in select- and delete-methods.
 *
 * @author harald
 */
public class MethodArgument {

  private final Relation relation;        // relation to foreign entity for delayed determination
  private String foreignAttributeName;    // name of the foreign attribute for delayed determination
  private Attribute foreignAttribute;     // the foreign attribute
  private Attribute attribute;            // attribute of the current entity (null if value)
  private String value;                   // a fixed value (string representation)

  /**
   * Creates a method argument.
   *
   * @param relation relation for delayed determination of the foreign attribute
   * @param attribute attribute of the current entity
   */
  public MethodArgument(Relation relation, Attribute attribute) {
    this.relation = relation;
    this.attribute = attribute;
  }

  /**
   * Creates a method argument for a foreign attribute other than the one the relation points to.
   *
   * @param relation relation for delayed determination of the foreign attribute
   * @param foreignAttributeName the name of the foreign attribute
   * @param attribute attribute of the current entity
   */
  public MethodArgument(Relation relation, String foreignAttributeName, Attribute attribute) {
    this.relation = relation;
    this.foreignAttributeName = foreignAttributeName;
    this.attribute = attribute;
  }

  /**
   * Creates a fixed-value method argument for a foreign attribute other than the one the relation points to.<br>
   * If value is a String it must be enclosed in double quotes.
   *
   * @param relation relation for delayed determination of the foreign attribute
   * @param foreignAttributeName the name of the foreign attribute
   * @param value the fixed value in its representation as a string
   */
  public MethodArgument(Relation relation, String foreignAttributeName, String value) {
    this.relation = relation;
    this.foreignAttributeName = foreignAttributeName;
    this.value = value;
  }

  /**
   * Returns whether the method argument is a fixed value.
   *
   * @return true if fixed value, else attribute
   */
  public boolean isValue() {
    return attribute == null;
  }

  /**
   * Gets the foreign attribute.
   *
   * @return the attribute of the foreign entity, null if not (yet) resolvable
   */
  public Attribute getForeignAttribute() {
    if (foreignAttribute == null) {
      if (foreignAttributeName != null) {
        Entity foreignEntity = relation.getForeignEntity();
        if (foreignEntity != null) {
          foreignAttribute = foreignEntity.getAttributeByJavaName(foreignAttributeName, true);
        }
      }
      else {
        foreignAttribute = relation.getForeignAttribute();
        if (foreignAttribute == null) {
          // ID
          Entity foreignEntity = relation.getForeignEntity();
          if (foreignEntity != null) {
            foreignAttribute = foreignEntity.getAttributeByJavaName(Constants.AN_ID, true);
          }
        }
      }
    }
    return foreignAttribute;
  }

  /**
   * Gets the name of the foreign attribute.
   *
   * @return the name of the foreign attribute, null if not set and no foreign attribute
   */
  public String getForeignAttributeName() {
    if (foreignAttributeName == null && foreignAttribute != null) {
      foreignAttributeName = foreignAttribute.getName();
    }
    return foreignAttributeName;
  }

  /**
   * Gets the local attribute.
   *
   * @return the attribute of the local entity, null if fixed value
   */
  public Attribute getAttribute() {
    return attribute;
  }

  /**
   * Gets the fixed value.
   *
   * @return the value (only valid if {@link #isValue()} is true)
   */
  public String getValue() {
    return value;
  }

  /**
   * Gets the string for the method argument.
   * <p>
   * This is the fixed value, the name of the local attribute, or the getter invocation
   * if the attribute is inherited or declared {@code super}.
   *
   * @return the argument string
   */
  public String getMethodArgument() {
    if (isValue()) {
      return value;
    }
    else {
      if (attribute.getOptions().isFromSuper() ||
          !attribute.getEntity().equals(relation.getEntity())) {
        return attribute.getGetterName() + "()";
      }
      else {
        return attribute.getName();
      }
    }
  }

  /**
   * Gets the name of the setter method of the foreign attribute.
   *
   * @return the setter method
   */
  public String getSetterMethod() {
    return "set" + StringHelper.firstToUpper(getForeignAttribute().getName());
  }

  @Override
  public String toString() {
    StringBuilder buf = new StringBuilder();
    Attribute fa = getForeignAttribute();
    if (fa != null) {
      buf.append(fa);
    }
    else {
      buf.append('\'').append(foreignAttributeName).append('\'');
    }
    buf.append('=').append(getMethodArgument());
    return buf.toString();
  }

}
