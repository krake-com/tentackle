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

/**
 * A group of components sharing the same properties.
 * 
 * @author harald
 */
@SuppressWarnings("serial")
public class PropertyGroup extends ComponentGroup {
  
  // properties handles by this group
  private boolean visible = true;
  private boolean changeable = true;


  /**
   * Creates a new property group.
   */
  public PropertyGroup() {
    super();
  }



  /**
   * Gets the changeability of the components in this group.
   *
   * @return true if changeable (default)
   */
  public boolean isChangeable() {
    return changeable;
  }

  /**
   * Sets the changeability of the components in this group.
   *
   * @param changeable true if changeable
   */
  public void setChangeable(boolean changeable) {
    this.changeable = changeable;
    for (Component comp: components) {
      if (comp instanceof FormChangeable) {
        ((FormChangeable) comp).setChangeable(changeable);
      }
    }
  }


  /**
   * Gets the visibility of the components in this group.
   *
   * @return true if visible (default)
   */
  public boolean isVisible() {
    return visible;
  }


  /**
   * Sets the visibility of the components in this group.
   *
   * @param visible true if visible
   */
  public void setVisible(boolean visible) {
    this.visible = visible;
    for (Component comp: components) {
      comp.setVisible(visible);
    }
  }


  // more properties to come...

}
