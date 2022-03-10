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

import javafx.scene.image.ImageView;

import org.tentackle.fx.rdc.GuiProvider;

import java.util.List;

/**
 * A PDO object.<br>
 * Such as <pre>Message blah = [1256668/1]</pre>
 */
public class DataObject implements DataNode {

  private final String type;                  // the object type basename
  private final String name;                  // the object name, null if root entity or list member
  private final long id;                      // the object ID
  private final long serial;                  // the object serial
  private final boolean rootEntity;           // true if PDO is a root entity
  private final GuiProvider<?> guiProvider;   // optional GUI Provider
  private final List<DataNode> nodes;         // the items of the object, null if reference is null

  /**
   * Creates a DataObject.
   *
   * @param type the object type basename
   * @param name the object name, null if root entity or list member
   * @param id the object ID
   * @param serial the object serial
   * @param rootEntity true if PDO is a root entity
   * @param guiProvider the optional GUI provider
   * @param nodes the items of the object, null if reference is null
   */
  public DataObject(String type, String name, long id, long serial, boolean rootEntity, GuiProvider<?> guiProvider, List<DataNode> nodes) {
    this.type = type;
    this.name = name;
    this.id = id;
    this.serial = serial;
    this.rootEntity = rootEntity;
    this.guiProvider = guiProvider;
    this.nodes = nodes;
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

  /**
   * Gets the object ID.
   *
   * @return the object ID
   */
  public long getId() {
    return id;
  }

  /**
   * Gets the object serial.
   *
   * @return the object serial
   */
  public long getSerial() {
    return serial;
  }

  /**
   * Gets true if PDO is a root entity (transaction- or masterdata).
   *
   * @return true if PDO is a root entity (transaction- or masterdata)
   */
  public boolean isRootEntity() {
    return rootEntity;
  }

  @Override
  public ImageView createIcon() {
    return guiProvider != null ? guiProvider.createIcon() : DataNode.super.createIcon();
  }

  public GuiProvider<?> getGuiProvider() {
    return guiProvider;
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

  @Override
  public String getValue() {
    StringBuilder buf = new StringBuilder();
    if (nodes == null) {
      buf.append("<null>");
    }
    else {
      buf.append('[').append(id).append('/').append(serial).append(']');
    }
    return buf.toString();
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
