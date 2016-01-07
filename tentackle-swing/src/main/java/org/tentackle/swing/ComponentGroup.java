/**
 * Tentackle - http://www.tentackle.org
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


package org.tentackle.swing;

import java.awt.Component;
import java.io.Serializable;
import java.util.HashSet;
import java.util.Set;

/**
 * A group of components.
 *
 * @author harald
 */
@SuppressWarnings("serial")
public abstract class ComponentGroup implements Serializable {

  private String name;                    // the group name
  protected Set<Component> components;    // the components


  /**
   * Creates a component group.
   */
  public ComponentGroup() {
    components = new HashSet<Component>();
  }


  /**
   * Gets the group's name.
   *
   * @return the name
   */
  public String getName() {
    return name;
  }

  /**
   * Sets the group's name.
   *
   * @param name the name
   */
  public void setName(String name) {
    this.name = name;
  }


  /**
   * Gets an array of all compoments in this group.
   *
   * @return the component array
   */
  public Component[] getComponents() {
    return components.toArray(new Component[0]);
  }


  /**
   * Adds a component to this group.
   *
   * @param component the component to add
   * @return true if added, false if already in group
   */
  public boolean addComponent(Component component) {
    return components.add(component);
  }


  /**
   * Removes a component from this group.
   *
   * @param component the component to remove
   * @return true if removed, false if not in group
   */
  public boolean removeComponent(Component component) {
    return components.remove(component);
  }

}
