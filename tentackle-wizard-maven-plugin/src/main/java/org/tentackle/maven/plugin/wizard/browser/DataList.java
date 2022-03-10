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

package org.tentackle.maven.plugin.wizard.browser;

import java.util.List;

/**
 * A list of PDOs.<br>
 * Such as <pre>List&lt;Message&gt; messages = [12]</pre>
 */
public class DataList implements DataNode {

  private final String genericType;         // the inner generic type
  private final String name;                // the name of the list property
  private final List<DataObject> nodes;     // the items of the list sorted by ID

  /**
   * Creates a DataList.
   *
   * @param genericType the inner generic type
   * @param name the name of the list property
   * @param nodes the items of the list sorted by ID
   */
  public DataList(String genericType, String name, List<DataObject> nodes) {
    this.genericType = genericType;
    this.name = name;
    this.nodes = nodes;
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

  /**
   * Gets the items of the list sorted by ID.
   *
   * @return the items of the list sorted by ID
   */
  @Override
  public List<DataObject> getNodes() {
    return nodes;
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
