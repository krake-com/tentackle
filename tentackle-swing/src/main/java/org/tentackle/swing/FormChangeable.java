/**
 * Tentackle - http://www.tentackle.org
 * Copyright (C) 2001-2008 Harald Krake, harald@krake.de, +49 7722 9508-0
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

/**
 * A component that supports setting the changeability,
 *
 * @author harald
 */
public interface FormChangeable {

  /**
   * Set the component to be changeable.<br>
   * This is a unified abstraction and will be translated to setEditable or
   * setEnabled, whatever is appropriate to this component.
   *
   * @param changeable true the user can edit the data, false if show only
   */
  void setChangeable(boolean changeable);

  /**
   * Returns if this component is changeable.
   *
   * @return true the user can edit the data, false if show only
   */
  boolean isChangeable();

  /**
   * Sets whether this component should honour the changeable-attribute or not.<br>
   * The default is true.
   * Notice: if this component does not honour the changeable flag,
   * isValueChanged() always returns false and it will not perform any saveValue().
   *
   * @param honourChangeable true if honour the changeable attribute, false if not
   */
  void setHonourChangeable(boolean honourChangeable);

  /**
   * Returns whether this component should honour the changeable-attribute or not.
   * @return true if honour the changeable attribute, false if not
   */
  boolean isHonourChangeable();
  
}
