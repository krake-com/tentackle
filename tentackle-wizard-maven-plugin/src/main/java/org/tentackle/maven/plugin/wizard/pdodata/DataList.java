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
  private final List<DataObject> nodes;     // the items of the list sorted by ID
  private final Relation relation;          // composite list relation

  /**
   * Creates a DataList.
   *
   * @param genericType the inner generic type
   * @param name the name of the list property
   * @param comment the optional comment
   * @param nodes the items of the list sorted by ID
   * @param relation the composite list relation
   */
  public DataList(String genericType, String name, String comment, List<DataObject> nodes, Relation relation) {
    this.genericType = genericType;
    this.name = name;
    this.comment = comment;
    this.nodes = nodes;
    this.relation = relation;
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

  /**
   * Gets the items of the list sorted by ID.
   *
   * @return the items of the list sorted by ID
   */
  @Override
  public List<DataObject> getNodes() {
    return nodes;
  }

  /**
   * Gets the composite list relation.
   *
   * @return the relation
   */
  public Relation getRelation() {
    return relation;
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
