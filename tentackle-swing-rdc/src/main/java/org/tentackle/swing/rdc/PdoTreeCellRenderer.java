/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.tentackle.swing.rdc;

import java.awt.Component;
import javax.swing.ImageIcon;
import javax.swing.JTree;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeCellRenderer;



/**
 * The default renderer for an {@link PdoTree}.
 * @see PdoTree#createDefaultRenderer
 * @author harald
 */
@SuppressWarnings("serial")
public class PdoTreeCellRenderer extends DefaultTreeCellRenderer {

  PdoTreeObject tobj;   // != null if node holds an PdoTreeObject


  /**
   * {@inheritDoc}
   * <p>
   * Overridden to determine the {@link PdoTreeObject} and the icon.
   *
   * @param tree the tree
   * @param value the object to render
   * @param selected true if node is selected
   * @param expanded true if node is expanded
   * @param leaf true if node is a leaf
   * @param row the row number in the tree
   * @param hasFocus true if tree has focus
   * @return the component (i.e. "this")
   */
  @Override
  public Component getTreeCellRendererComponent (JTree tree, Object value,
                  boolean selected, boolean expanded, boolean leaf,
                  int row, boolean hasFocus)  {

    super.getTreeCellRendererComponent(tree, value, selected, expanded,
                                       leaf, row, hasFocus);

    tobj = null;
    ImageIcon icon = null;

    if (value != null &&
            value instanceof DefaultMutableTreeNode) {
      Object obj = ((DefaultMutableTreeNode) value).getUserObject();
      if (obj != null && obj instanceof PdoTreeObject) {
        tobj = (PdoTreeObject) obj;
        icon = tobj.getIcon();
      }
    }

    setIcon(icon);
    return this;
  }


  /**
   * {@inheritDoc}
   * <p>
   * Overridden to determine the tooltip from the {@link org.tentackle.pdo.PersistentDomainObject}.
   */
  @Override
  public String getToolTipText()  {
    return tobj != null ? tobj.getToolTipText() : null;
  }

}
