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

import org.tentackle.bind.Bindable;
import org.tentackle.model.Entity;

import java.util.List;

/**
 * A node in a PDO data structure.<br>
 * Implemented by {@link DataObject}, {@link DataList} and {@link DataItem} for the data of a PDO,
 * and by {@link DataDiff} for the comparison of two such structures.
 */
public interface DataNode {

  /**
   * Returns the type of the node.<br>
   * Can be any Java type, classname (w/o package) including optional generics.<br>
   * Example: <pre>List&lt;Message&gt;</pre>
   *
   * @return the type, null only for a reference without PDO and entity
   */
  @Bindable
  String getType();

  /**
   * Returns the name of the node.<br>
   * Can be the name of a property, an entity, class, interface, whatever.
   *
   * @return the name, null for the root object
   */
  @Bindable
  String getName();

  /**
   * Returns the value of the node.<br>
   * This is usually the {@code toString} value.
   * For collections, it's the size.
   *
   * @return the value, the representation of a null value is implementation-specific
   */
  @Bindable
  String getValue();

  /**
   * Gets the optional comment.
   *
   * @return the comment, null if none
   */
  String getComment();

  /**
   * Gets the hidden flag.
   *
   * @return true if object is hidden (no public method available)
   */
  boolean isHidden();

  /**
   * Gets the optional sub nodes of this node.
   *
   * @return the nodes, null if this node has no sub nodes
   */
  List<? extends DataNode> getNodes();

  /**
   * Gets the name of this node type.<br>
   * Used to locate the image, for example.
   *
   * @return the node type name
   */
  default String getNodeType() {
    return getClass().getSimpleName();
  }

  /**
   * Sets the optional configuration path.<br>
   * The path identifies the node in the code configuration, see {@link CodeConfigurationFactory}.
   *
   * @param configurationPath the path
   * @throws UnsupportedOperationException if not applicable, such as for a {@link DataDiff}
   */
  void setConfigurationPath(String configurationPath);

  /**
   * Gets the optional configuration path.
   *
   * @return the path, null if none
   */
  String getConfigurationPath();

  /**
   * Returns the SQL select configuration for the where clause.
   *
   * @return the SQL condition, null if none
   */
  SqlCondition getSqlCondition();

  /**
   * Gets the entity this node belongs to.
   *
   * @return the entity, null if unknown
   */
  Entity getEntity();

}
