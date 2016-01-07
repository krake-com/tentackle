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

// Created on February 5, 2004, 7:57 PM

package org.tentackle.swing.rdc;

import java.awt.datatransfer.Transferable;
import java.util.List;
import javax.swing.ImageIcon;
import javax.swing.JMenuItem;
import javax.swing.tree.DefaultMutableTreeNode;

/**
 * Interface to add features to objects displayed in an {@link PdoTree}.
 * <p>
 * 
 * Objects must implement this interface if:
 * <ul>
 * <li>they are not a persistent object (i.e. kind of "proxy"-object)</li>
 * <li>their handling differs from the default for a persistent object (see PdoTree when to use)</li>
 * </ul>
 * 
 * @author harald
 */
public interface PdoTreeExtension {

  /**
   * Defines an editor that will be invoked to "open" this tree object.<br>
   * "open" usually means to open a dialog.
   * A special menu-item will be created in the popup-menu for those objects.
   * (ex. to work on a PDF-Document)
   * The object itself will not be reloaded before the runnable is executed.
   * 
   * @return a editor if objects needs a special way to "open" it, null = none.
   */
  public Runnable getOpenEditor();
  
  
  /**
   * Defines an editor that will be invoked to edit this tree object.<br>
   * The editor should be a modal dialog, i.e. not return until editing is done.
   *
   * @return the editor, null = no special editor, i.e. use default editor
   */
  public PdoTreeExtensionEditor getEditor();
  
    
  /**
   * Defines additional menu items in the popup menu.
   * 
   * @param tree the tree
   * @param node the node the popup menu refers to
   * @return an array of additional {@code JMenuItems} to be appended to the popup-menu, null = none
   */
  public JMenuItem[] getExtraMenuItems(PdoTree tree, DefaultMutableTreeNode node);
  
  
  /**
   * Defines additional toggle nodes.
   * 
   * @param tree the tree
   * @param node the node the popup menu refers to
   * @return an array of additional {@code PdoTreeExtensionToggleNode} to be prepended to the popup-menu, null = none
   */
  public PdoTreeExtensionToggleNode[] getToggleNodes(PdoTree tree, DefaultMutableTreeNode node);
  
  
  
  
  // ------------------- from PdoGuiService, i.e. if tree object is not a persistent object -------
  
  
  /**
   * Gets the {@link Transferable} for this object.<br>
   * Used for drag and drop.
   *
   * @return the transferable, null if none
   */
  public Transferable getTransferable();
  
  
  /**
   * Drops a transferable on this object.<br>
   *
   * @param transferable the Transferable
   * @return true if drop succeeded, else false
   */
  public boolean dropTransferable(Transferable transferable);
  
  
  /**
   * Returns whether this object can be removed.
   * 
   * @return true if removable
   */
  public boolean isRemovable();

  
  /**
   * Determines whether this object may have child objects that should
   * be visible in a navigatable tree.
   * 
   * @return true if object may have childs
   */
  public boolean allowsTreeChildObjects();
 
  
  /**
   * Gets all childs of this objects that should be visible to the user
   * in a navigatable object tree.
   *
   * @return the childs, null = no childs
   */
  public List<Object> getTreeChildObjects();
  
  
  /**
   * Gets the childs with respect to the parent object this
   * object is displayed in the current tree.
   *
   * @param parentObject the parent object of this object in the tree, null = no parent
   * @return the list of childs
   */
  public List<Object> getTreeChildObjects(Object parentObject);
  
  
   /**
   * Gets the icon of this object.
   * 
   * @return the icon, null if none
   */
  public ImageIcon getIcon();
  

  /**
   * Gets the tooltip to be displayed for an object in a tree.
   * 
   * @return the tooltip text, null if none
   */
  public String getToolTipText();
  
}
