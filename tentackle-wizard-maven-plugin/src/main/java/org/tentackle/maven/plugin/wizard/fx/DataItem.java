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

package org.tentackle.maven.plugin.wizard.fx;

import java.util.List;

/**
 * A simple java attribute or property of a PDO.<br>
 * Such as <pre>String blah = "some text"</pre>
 */
public class DataItem implements DataNode {

  private final String type;    // the type basename
  private final String name;    // the property name
  private final String value;   // the property value, may be null

  /**
   * Creates a DataItem.
   *
   * @param type the type basename
   * @param name the property name
   * @param value the property value, may be null
   */
  public DataItem(String type, String name, String value) {
    this.type = type;
    this.name = name;
    this.value = value;
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

  /**
   * Gets the property value, may be null.
   *
   * @return the property value, may be null
   */
  @Override
  public String getValue() {
    return value;
  }

  @Override
  public List<DataNode> getNodes() {
    return null;
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
