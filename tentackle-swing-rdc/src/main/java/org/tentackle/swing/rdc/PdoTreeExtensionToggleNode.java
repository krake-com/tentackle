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

import java.util.List;
import javax.swing.JMenuItem;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;
import org.tentackle.pdo.PersistentDomainObject;

/**
 * A childnode for nodes in an {@link PdoTree} to add a popup-menu-item
 * for toggling the display of subtrees.
 *
 * @author harald
 */
public abstract class PdoTreeExtensionToggleNode {

  private int toggleNodeId;                     // the unique toggle-node ID
  private PdoTree tree;                 // the tree
  private DefaultMutableTreeNode popupNode;     // the popupnode
  private TreePath popupPath;                   // path for popupnode
  private int childIndex;                       // index in child-nodes of popupNode


  /**
   * Creates a toggle node.
   * <p>
   * Each of toggle node must get its own unique identifier to determine
   * whether it is already displayed in the tree or not.
   *
   * @param toggleNodeId is some unique(!) id
   */
  public PdoTreeExtensionToggleNode(int toggleNodeId) {
    this.toggleNodeId = toggleNodeId;
  }


  /**
   * Gets the toggle node id.
   *
   * @return the unique id
   */
  public int getToggleNodeId() {
    return toggleNodeId;
  }


  /**
   * Gets the tree this toggle node is associated to.
   *
   * @return the tree
   */
  public PdoTree getTree() {
    return tree;
  }


  /**
   * Gets the popup node this toggle node is associated to.
   *
   * @return the popupNode
   */
  public DefaultMutableTreeNode getPopupNode() {
    return popupNode;
  }


  /**
   * Gets the index of this toggle-node withing the childs of the popup node.
   *
   * @return the index
   */
  public int getChildIndex() {
    return childIndex;
  }


  /**
   * Creates an PdoTreeToggleNodeObject to be inserted into the tree.
   * Needs to be implemented!
   *
   * @param popupObject is the object of the popupNode (usually an PersistentDomainObject, but not necessarily and may be null)
   * @return the toggle node object to be inserted as a child of the popupNode
   */
  public abstract PdoTreeToggleNodeObject getToggleNodeObject(Object popupObject);


  /**
   * Gets a JMenuItem for the popup-Menu.
   * Needs to be implemented!
   *
   * @param toggleNodeDisplayed is true if the toggle-node is displayed in the tree, false if not
   * @return the array of menu items, null if none.
   */
  public abstract JMenuItem getMenuItem(boolean toggleNodeDisplayed);


  /**
   * Creates a JMenuItem for the popup-Menu.
   *
   * @param tree the tree
   * @param popupNode is the node the popupMenu is displayed for
   * @param popupPath is the treepath for the popupNode
   * @return the menu item
   */
  public JMenuItem getMenuItem(PdoTree tree, DefaultMutableTreeNode popupNode, TreePath popupPath) {

    // save info for inserting and removing the toggle node
    this.tree      = tree;
    this.popupNode = popupNode;
    this.popupPath = popupPath;

    /**
     * check if togglenode is already displayed.
     * All togglenodes appear at the beginning of the node's children.
     * So we stop at the first non-togglenode.
     */
    boolean toggleNodeDisplayed = false;
    int childNum = popupNode.getChildCount();
    for (childIndex=0; childIndex < childNum; childIndex++) {
      TreeNode childNode = popupNode.getChildAt(childIndex);
      if (childNode instanceof DefaultMutableTreeNode) {
        Object userObject = ((DefaultMutableTreeNode)childNode).getUserObject();
        if (userObject instanceof PdoTreeToggleNodeObject) {
          if (((PdoTreeToggleNodeObject)userObject).getToggleNodeId() == toggleNodeId) {
            toggleNodeDisplayed = true;
            break;
          }
        }
        else  {
          // non toggle node
          break;
        }
      }
    }

    return getMenuItem(toggleNodeDisplayed);
  }


  /**
   * Sets the list of objects into this toggle node and
   * inserts the toggle node into the tree (invokes {@code showToggleNode()}.
   * @param list the list of objects
   */
  @SuppressWarnings("unchecked")
  public void insertObjects(List list) {
    DefaultTreeModel model = (DefaultTreeModel)tree.getModel();
    // insert the toggle node
    DefaultMutableTreeNode toggleNode = showToggleNode();
    // insert the list of objects into the toggle node
    if (list != null && list.isEmpty() == false) {
      for (Object obj: list) {
        if (tree.isObjectAppendable(obj))  {
          PdoTreeObject to = new PdoTreeObject(obj, null);
          DefaultMutableTreeNode childnode = new DefaultMutableTreeNode(to);
          childnode.setAllowsChildren(
            (obj instanceof PdoTreeExtension && ((PdoTreeExtension)obj).allowsTreeChildObjects()) ||
            (obj instanceof PersistentDomainObject && Rdc.createGuiProvider((PersistentDomainObject)obj).allowsTreeChildObjects()));
          model.insertNodeInto(childnode, toggleNode, toggleNode.getChildCount());
        }
      }
    }
    tree.doExpandPath(0, 1, null, popupPath);    // expand one level if not yet done to show the togglenode
    tree.doExpandPath(0, 1, null, popupPath.pathByAddingChild(toggleNode));   // expand the togglenode
  }


  /**
   * Inserts the toggle-node as the first childnode.
   *
   * @return the toggle node
   */
  public DefaultMutableTreeNode showToggleNode() {
    DefaultTreeModel model = (DefaultTreeModel)tree.getModel();
    Object popupObject = ((PdoTreeObject)popupNode.getUserObject()).getObject();
    // insert the toggle node
    DefaultMutableTreeNode toggleNode = new DefaultMutableTreeNode(getToggleNodeObject(popupObject));
    model.insertNodeInto(toggleNode, popupNode, 0);   // insert at top of tree
    return toggleNode;
  }


  /**
   * Removes the togglenode from the tree.
   */
  public void hideToggleNode() {
    ((DefaultTreeModel)tree.getModel()).removeNodeFromParent((DefaultMutableTreeNode)popupNode.getChildAt(childIndex));
  }

}
