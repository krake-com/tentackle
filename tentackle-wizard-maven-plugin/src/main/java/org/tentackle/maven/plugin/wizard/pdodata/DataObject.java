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
import org.tentackle.pdo.PersistentDomainObject;

import java.util.List;

/**
 * A PDO object.<br>
 * Such as <pre>Message blah = [1256668/1]</pre>
 */
public class DataObject implements DataNode {

  private final PersistentDomainObject<?> pdo;          // the PDO
  private final String type;                            // the object type basename
  private final String name;                            // the object name, null if root entity or list member
  private final List<DataNode> nodes;                   // the items of the object, null if reference is null
  private final String comment;                         // the optional comment
  private final Relation relation;                      // composite object 1:1 relation
  private final NonCompositeRelation nmRelation;        // the optional N:M relation
  private String configurationPath;                     // the optional configuration path

  /**
   * Creates a DataObject.
   *
   * @param pdo the PDO
   * @param name the object name, null if root entity or list member
   * @param comment the optional comment
   * @param nodes the items of the object, null if reference is null
   * @param relation the composite object 1:1 relation
   * @param nmRelation optional N:M relation
   */
  public DataObject(PersistentDomainObject<?> pdo, String name, String comment, List<DataNode> nodes, Relation relation, NonCompositeRelation nmRelation) {
    this.pdo = pdo;
    this.type = pdo.getEffectiveClass().getSimpleName();
    this.name = name;
    this.comment = comment;
    this.nodes = nodes;
    this.relation = relation;
    this.nmRelation = nmRelation;
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
   * Gets the object name, null if root entity or list member.
   *
   * @return the object name, null if root entity or list member
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
   * Gets the object ID.
   *
   * @return the object ID
   */
  public long getId() {
    return pdo.getId();
  }

  /**
   * Gets the object serial.
   *
   * @return the object serial
   */
  public long getSerial() {
    return pdo.getSerial();
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
   * Gets the 1:1 composite object relation.
   *
   * @return the relation
   */
  public Relation getRelation() {
    return relation;
  }

  /**
   * Gets the optional N:M relation.
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
      buf.append('[').append(getId()).append('/').append(getSerial()).append(']');
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

  @Override
  public String toString() {
    StringBuilder buf = new StringBuilder();
    buf.append(type);
    if (name != null) {
      buf.append(' ').append(name).append(" =");
    }
    buf.append(' ').append(getValue());
    return buf.toString();
  }

}
