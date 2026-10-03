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

import org.tentackle.model.Entity;
import org.tentackle.model.Relation;
import org.tentackle.pdo.PersistentDomainObject;

import java.util.List;

/**
 * A PDO.<br>
 * Such as <pre>Message blah = [1256668/1]</pre>
 */
public class DataObject implements DataNode {

  private final PersistentDomainObject<?> pdo;          // the PDO
  private final String type;                            // the object type basename
  private final String name;                            // the object name, null if root
  private final List<DataNode> nodes;                   // the items of the object, null if reference is null
  private final String comment;                         // the optional comment
  private final Entity entity;                          // the entity of this object
  private final Relation relation;                      // the composite relation this object belongs to, null if root
  private final NonCompositeRelation nmRelation;        // the optional N:M-related PDO
  private final boolean hidden;                         // true if object is hidden (no public method available)
  private final SqlCondition sqlCondition;              // the SQL where condition

  private String configurationPath;                     // the optional configuration path
  private DataList dataList;                            // != null if element of a list
  private int listIndex;                                // index in dataList
  private String formattedName;                         // the formatted name

  /**
   * Creates a DataObject.
   *
   * @param pdo the PDO
   * @param name the object name, null if root
   * @param comment the optional comment
   * @param nodes the items of the object, null if reference is null
   * @param entity the entity of this object
   * @param relation the composite relation this object belongs to, null if root
   * @param nmRelation optional N:M-related PDO, null if none
   * @param hidden true if object is hidden (no public method available)
   * @param sqlCondition the SQL where condition
   */
  public DataObject(PersistentDomainObject<?> pdo, String name, String comment, List<DataNode> nodes,
                    Entity entity, Relation relation, NonCompositeRelation nmRelation, boolean hidden,
                    SqlCondition sqlCondition) {
    this.pdo = pdo;
    this.type = pdo == null ? (entity == null ? null : entity.getName()) : pdo.getEffectiveClass().getSimpleName();
    this.name = name;
    this.comment = comment;
    this.nodes = nodes;
    this.entity = entity;
    this.relation = relation;
    this.nmRelation = nmRelation;
    this.hidden = hidden;
    this.sqlCondition = sqlCondition;
  }

  /**
   * Gets the PDO.
   *
   * @return the PDO
   */
  public PersistentDomainObject<?> getPdo() {
    return pdo;
  }

  /**
   * Gets the object type basename.
   *
   * @return the object type basename
   */
  @Override
  public String getType() {
    return type;
  }

  /**
   * Gets the object name.<br>
   * For list elements, the name is followed by the index, for example {@code lines[2]}.
   * Elements of N:M relations are named after the N:M method.
   *
   * @return the object name, null if root
   */
  @Override
  public String getName() {
    if (formattedName == null) {
      if (dataList == null) {
        formattedName = name;
      }
      else {
        StringBuilder buf = new StringBuilder();
        if (nmRelation != null && nmRelation.getRelation().getNmMethodName() != null) {
          buf.append(nmRelation.getRelation().getNmMethodName());
        }
        else {
          buf.append(name);
        }
        buf.append('[').append(listIndex).append(']');
        formattedName = buf.toString();
      }
    }
    return formattedName;
  }

  @Override
  public String getComment() {
    return comment;
  }

  @Override
  public boolean isHidden() {
    return hidden;
  }

  @Override
  public SqlCondition getSqlCondition() {
    return sqlCondition;
  }

  /**
   * Gets the object ID.
   *
   * @return the object ID
   */
  public long getId() {
    return pdo == null ? 0 : pdo.getId();
  }

  /**
   * Gets the object serial.
   *
   * @return the object serial
   */
  public long getSerial() {
    return pdo == null ? 0 : pdo.getSerial();
  }

  /**
   * Gets the items of the object, null if reference is null.
   *
   * @return the items of the object, null if reference is null
   */
  @Override
  public List<DataNode> getNodes() {
    return nodes;
  }

  /**
   * Gets the entity of this object.
   *
   * @return the entity
   */
  @Override
  public Entity getEntity() {
    return entity;
  }

  /**
   * Gets the composite relation this object belongs to.<br>
   * This is the 1:1 object relation of a component or the list relation of a list element.
   *
   * @return the relation, null if root or unknown
   */
  public Relation getRelation() {
    return relation;
  }

  /**
   * Gets the optional N:M relation.<br>
   * Holds the composite list relation and the PDO the N:M link points to.
   *
   * @return the relation, null if none
   */
  public NonCompositeRelation getNmRelation() {
    return nmRelation;
  }

  @Override
  public String getValue() {
    StringBuilder buf = new StringBuilder();
    if (nodes == null) {
      buf.append("<null>");
    }
    else {
      if (pdo.isEmbedded()) {
        buf.append(pdo);
      }
      else {
        buf.append('[').append(getId()).append('/').append(getSerial()).append(']');
      }
      if (nmRelation != null) {
        buf.append(" -> ").append(nmRelation);
      }
    }
    return buf.toString();
  }

  @Override
  public void setConfigurationPath(String configurationPath) {
    this.configurationPath = configurationPath;
  }

  @Override
  public String getConfigurationPath() {
    return configurationPath;
  }

  /**
   * Sets the parent data list and the index if this is a list element.
   *
   * @param dataList the data list
   * @param listIndex the list index
   */
  public void setDataList(DataList dataList, int listIndex) {
    this.dataList = dataList;
    this.listIndex = listIndex;
    formattedName = null;
  }


  @Override
  public String toString() {
    StringBuilder buf = new StringBuilder();
    buf.append(type);
    String fmtName = getName();
    if (fmtName != null) {
      buf.append(' ').append(fmtName).append(" =");
    }
    buf.append(' ').append(getValue());
    return buf.toString();
  }

}
