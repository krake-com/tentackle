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

import java.awt.event.ActionEvent;
import java.text.MessageFormat;
import javax.swing.AbstractAction;
import javax.swing.JMenuItem;
import org.tentackle.pdo.PersistentDomainObject;
import org.tentackle.swing.plaf.PlafUtilities;


/**
 * A toggle node to display the "usage of..." an {@link PersistentDomainObject}.
 *
 * @author harald
 */
public class PdoTreeExtensionUsageToggleNode extends PdoTreeExtensionToggleNode {

  public static final int TOGGLENODEID_TREEUSAGE = 1;


  private JMenuItem inUseItem;    // the toggle menu item


  /**
   * Creates a usage toggle node
   */
  public PdoTreeExtensionUsageToggleNode() {
    super(TOGGLENODEID_TREEUSAGE);
    inUseItem = new JMenuItem(new AbstractAction("treeusage") {   // NOI18N
      @Override
      public void actionPerformed(ActionEvent e)  {
        if (inUseItem.getText().equals(RdcSwingRdcBundle.getString("COLLAPSE REFERENCES"))) {
          getTree().hideInUseTree(getChildIndex());
        }
        else  {
          getTree().showInUseTree();
        }
      }
    });
  }


  @Override
  public JMenuItem getMenuItem(boolean toggleNodeDisplayed) {
    inUseItem.setText(toggleNodeDisplayed ? RdcSwingRdcBundle.getString("COLLAPSE REFERENCES") : RdcSwingRdcBundle.getString("REFERENCED BY_..."));
    return inUseItem;
  }


  @Override
  @SuppressWarnings("unchecked")
  public PdoTreeToggleNodeObject getToggleNodeObject(Object popupObject) {
    return new PdoTreeToggleNodeObject(
            TOGGLENODEID_TREEUSAGE,
            MessageFormat.format(RdcSwingRdcBundle.getString("USAGE OF {0}"),
            Rdc.createGuiProvider((PersistentDomainObject)popupObject).getTreeText()),
            PlafUtilities.getInstance().getIcon("treeusage"));
  }

}
