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

package org.tentackle.maven.plugin.wizard.pdodata;

import org.tentackle.model.Attribute;
import org.tentackle.model.Entity;
import org.tentackle.model.Relation;

import java.util.List;

/**
 * A simple Java attribute or property of a PDO.<br>
 * Such as <pre>String blah = "some text"</pre>
 */
public class DataItem implements DataNode {

  private final String type;                                  // the type basename
  private final String name;                                  // the property name
  private final Object value;                                 // the property value, may be null
  private final String comment;                               // the optional comment
  private final Entity entity;                                // the effective entity
  private final Attribute attribute;                          // the model attribute
  private final boolean idOfCompositeRelation;                // true if this is an ID of a composite object relation
  private final NonCompositeRelation nonCompositeRelation;    // the optional non-composite relation
  private final SqlCondition sqlCondition;                    // the SQL where condition

  private String displayValue;                                // formatted value displayed
  private String configurationPath;                           // the optional configuration path

  /**
   * Creates a DataItem.
   *
   * @param type the type basename
   * @param name the property name
   * @param value the property value, may be null
   * @param comment the optional comment
   * @param entity the effective entity
   * @param attribute the model attribute, null if not in model
   * @param idOfCompositeRelation true if this is an ID of a composite object relation
   * @param nonCompositeRelation the non-composite relation, may be null
   * @param sqlCondition the SQL where condition
   */
  public DataItem(String type, String name, Object value, String comment, Entity entity, Attribute attribute,
                  boolean idOfCompositeRelation, NonCompositeRelation nonCompositeRelation, SqlCondition sqlCondition) {
    this.type = type;
    this.name = name;
    this.value = value;
    this.comment = comment;
    this.entity = entity;
    this.attribute = attribute;
    this.idOfCompositeRelation = idOfCompositeRelation;
    this.nonCompositeRelation = nonCompositeRelation;
    this.sqlCondition = sqlCondition;
  }

  /**
   * Gets the type basename.
   *
   * @return the type basename
   */
  @Override
  public String getType() {
    return type;
  }

  /**
   * Gets the property name.
   *
   * @return the property name
   */
  @Override
  public String getName() {
    return name;
  }

  @Override
  public boolean isHidden() {
    return attribute != null && attribute.isHidden();
  }

  /**
   * Gets the formatted display value.<br>
   * Includes the referenced PDO, if this item is the ID of a non-composite relation.
   *
   * @return the display value, empty if the value is null and there is no related PDO
   */
  @Override
  public String getValue() {
    if (displayValue == null) {
      StringBuilder buf = new StringBuilder();
      if (value != null) {
        buf.append(value);
      }
      if (nonCompositeRelation != null) {
        if (!buf.isEmpty()) {
          buf.append(' ');
        }
        buf.append("-> ").append(nonCompositeRelation);
      }
      displayValue = buf.toString();
    }
    return displayValue;
  }

  /**
   * Gets the original object of the displayed value.
   *
   * @return the value, may be null
   */
  public Object getOrgValue() {
    return value;
  }

  @Override
  public String getComment() {
    return comment;
  }

  @Override
  public List<DataNode> getNodes() {
    return null;
  }

  /**
   * Gets the associated attribute in the model.<br>
   * Notice that some items have no model equivalent, such as classId.
   *
   * @return the attribute, null if not in model
   */
  public Attribute getAttribute() {
    return attribute;
  }

  @Override
  public Entity getEntity() {
    return entity;
  }

  /**
   * Returns whether this is the ID of a composite object relation.<br>
   * Such IDs are skipped in code generation and ignored when comparing data objects.
   *
   * @return true if ID of a composite relation
   */
  public boolean isIdOfCompositeRelation() {
    return idOfCompositeRelation;
  }

  /**
   * Gets the optional object relation.
   *
   * @return the non-composite 1:1 relation, null if none
   */
  public NonCompositeRelation getNonCompositeRelation() {
    return nonCompositeRelation;
  }

  @Override
  public void setConfigurationPath(String configurationPath) {
    this.configurationPath = configurationPath;
  }

  @Override
  public String getConfigurationPath() {
    return configurationPath;
  }

  @Override
  public SqlCondition getSqlCondition() {
    return sqlCondition;
  }

  /**
   * Determines whether this attribute is a counter for a composite list relation.<br>
   * Used by the code generator to skip such items.
   *
   * @return true if counter
   */
  public boolean isCounterForListRelation() {
    if (attribute != null) {
      for (Relation relation : attribute.getEntity().getRelations()) {
        if (attribute.equals(relation.getCountAttribute())) {
          return true;
        }
      }
    }
    return false;
  }

  /**
   * Returns whether this item is part of the unique domain key.
   *
   * @return true if key
   */
  public boolean isKey() {
    return attribute != null && attribute.getOptions().isDomainKey();
  }

  @Override
  public String toString() {
    StringBuilder buf = new StringBuilder();
    buf.append(type).append(' ')
       .append(name).append(" = ");
    if (value == null) {
      buf.append("<null>");
    }
    else {
      buf.append("'").append(value).append("'");
    }
    return buf.toString();
  }

}
