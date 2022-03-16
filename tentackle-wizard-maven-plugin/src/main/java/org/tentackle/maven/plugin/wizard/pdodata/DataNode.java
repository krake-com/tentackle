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

import java.util.List;

/**
 * A node in a PDO data structure.
 */
public interface DataNode {

  /**
   * Returns the type of the node.<br>
   * Can be any Java type, classname (w/o package) including optional generics.<br>
   * Example: <pre>List&lt;Message&gt;</pre>
   *
   * @return the type, never null and never empty
   */
  @Bindable
  String getType();

  /**
   * Returns the name of the node.<br>
   * Can be the name of a property, an entity, class, interface, whatever.
   *
   * @return the name, never null and never empty
   */
  @Bindable
  String getName();

  /**
   * Returns the value of the node.<br>
   * This is usually the <code>toString</code> value.
   * For collections, it's the size.
   *
   * @return the value, null if it's java value is null
   */
  @Bindable
  String getValue();

  /**
   * The optional comment.
   *
   * @return the comment, null if none
   */
  String getComment();

  /**
   * Gets the optional subnodes of this node.
   *
   * @return the nodes, null if this node has no subnodes
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

}
