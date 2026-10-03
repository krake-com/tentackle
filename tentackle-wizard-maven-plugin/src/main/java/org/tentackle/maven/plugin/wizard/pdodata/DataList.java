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

import org.tentackle.model.AccessScope;
import org.tentackle.model.Entity;
import org.tentackle.model.Relation;

import java.util.List;

/**
 * A list of PDOs.<br>
 * Such as <pre>List&lt;Message&gt; messages = [12]</pre>
 */
public class DataList implements DataNode {

  private final String genericType;         // the inner generic type
  private final String name;                // the name of the list property
  private final String comment;             // the optional comment
  private final List<DataObject> nodes;     // the items of the list, null if the list is null
  private final Relation relation;          // composite list relation, null if not in model
  private final SqlCondition sqlCondition;  // the SQL where condition
  private String configurationPath;         // the optional configuration path

  /**
   * Creates a DataList.
   *
   * @param genericType the inner generic type
   * @param name the name of the list property
   * @param comment the optional comment
   * @param nodes the items of the list, null if the list is null
   * @param relation the composite list relation, null if not in model
   * @param sqlCondition the SQL where condition
   */
  public DataList(String genericType, String name, String comment, List<DataObject> nodes, Relation relation, SqlCondition sqlCondition) {
    this.genericType = genericType;
    this.name = name;
    this.comment = comment;
    this.nodes = nodes;
    this.relation = relation;
    this.sqlCondition = sqlCondition;

    if (nodes != null) {
      int ndx = 0;
      for (DataObject node : nodes) {
        node.setDataList(this, ndx++);
      }
    }
  }

  /**
   * Gets the inner generic type.
   *
   * @return the inner generic type
   */
  public String getGenericType() {
    return genericType;
  }

  /**
   * Gets the name of the list property.
   *
   * @return the name of the list property
   */
  @Override
  public String getName() {
    return name;
  }

  @Override
  public String getComment() {
    return comment;
  }

  @Override
  public boolean isHidden() {
    return relation != null && relation.getAccessScope() != AccessScope.PUBLIC;
  }

  /**
   * Gets the items of the list.<br>
   * For 1:N relations, the elements come in the same order as returned by the PDO's select method.
   * For N:M relations, the elements are sorted by the type and unique domain key of the related PDO,
   * since the N:M-link itself is just a technical detail.
   *
   * @return the items of the list, null if the list is null
   */
  @Override
  public List<DataObject> getNodes() {
    return nodes;
  }

  @Override
  public SqlCondition getSqlCondition() {
    return sqlCondition;
  }

  /**
   * Gets the composite list relation.
   *
   * @return the relation, null if not in model
   */
  public Relation getRelation() {
    return relation;
  }

  @Override
  public Entity getEntity() {
    return relation == null ? null : relation.getForeignEntity();
  }

  @Override
  public String getType() {
    return "List<" + genericType + ">";
  }

  @Override
  public String getValue() {
    return nodes == null ? null : Integer.toString(nodes.size());
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
  public String toString() {
    StringBuilder buf = new StringBuilder();
    buf.append(getType()).append(' ').append(name).append(" = ");
    if (nodes == null) {
      buf.append("<null>");
    }
    else {
      buf.append('[').append(nodes.size()).append(']');
    }
    return buf.toString();
  }

}
