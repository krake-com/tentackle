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


package org.tentackle.swing.rdc;

import javax.swing.ImageIcon;

/**
 * Extended {@link PdoTreeObject} for displaying subtrees as childnodes
 * in an {@link PdoTree}.
 *
 * @author harald
 */
public class PdoTreeToggleNodeObject extends PdoTreeObject {

  private final int toggleNodeId;   // the unique toggle node id
  
  
  /**
   * Creates a toggle node object.
   * 
   * @param toggleNodeId the unique toggle node id
   * @param text some text which is displayed for the node, null = none
   * @param icon the icon for the node, null = none
   */
  public PdoTreeToggleNodeObject(int toggleNodeId, String text, ImageIcon icon)  {
    super(text, null);
    setIcon(icon);
    this.toggleNodeId = toggleNodeId;
  }
  
  
  /**
   * Gets the toggle node id.
   * 
   * @return the unique toggleNodeId
   */
  public int getToggleNodeId() {
    return toggleNodeId;
  }
  
  
  /**
   * Gets the tooltip text.<br>
   * The default implementation returns null.
   * 
   * @return the tooltip
   */
  @Override
  public String getToolTipText()  {
    return null;
  }
  
  
  /** 
   * Gets the object.<br>
   * The default implementation returns "this".
   */
  @Override
  public Object getObject() {
    return this;
  }
  
}