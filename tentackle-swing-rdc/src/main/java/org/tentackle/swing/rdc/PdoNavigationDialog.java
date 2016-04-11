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

// Created on August 6, 2002, 3:19 PM

package org.tentackle.swing.rdc;

import java.awt.BorderLayout;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.text.MessageFormat;
import java.util.List;
import org.tentackle.pdo.Pdo;
import org.tentackle.pdo.PersistentDomainObject;
import org.tentackle.swing.FormDialog;


/**
 * Navigation dialog for a list of {@link PersistentDomainObject}s.
 * <p>
 * This is just a dialog around a {@link PdoNavigationPanel}.
 * @param <T> the pdo type
 */
@SuppressWarnings("serial")
public class PdoNavigationDialog<T extends PersistentDomainObject<T>> extends FormDialog {

  private final PdoNavigationPanel<T> naviPanel;      // the navigation panel
  private PersistentDomainObject<?> selectedObject;   // the selected object



  /**
   * Creates a navigation dialog for a list of objects.
   *
   * @param owner the owner window of this dialog, null if none
   * @param list the list of objects
   * @param selectionFilter filter selectable objects, null if nothing selectable
   * @param buttonMode the visibility of buttons, one of {@code PdoNavigationPanel.SHOW_...}
   * @param showTable true if initially show the table view, false = tree view
   */
  public PdoNavigationDialog(Window owner, List<T> list, SelectionFilter selectionFilter, int buttonMode, boolean showTable)  {
    super(owner);
    naviPanel = Rdc.createPdoNavigationPanel(list, selectionFilter, buttonMode, showTable);
    setup();
  }

  /**
   * Creates a navigation dialog for a list of objects.
   *
   * @param owner the owner window of this dialog, null if none
   * @param list the list of objects
   * @param selectionFilter filter selectable objects, null if nothing selectable
   */
  public PdoNavigationDialog(Window owner, List<T> list, SelectionFilter selectionFilter)  {
    this(owner, list, selectionFilter, PdoNavigationPanel.SHOW_BUTTONS, false);
  }

  /**
   * Creates a navigation dialog for a list of objects.
   *
   * @param list the list of objects
   * @param selectionFilter filter selectable objects, null if nothing selectable
   * @param showTable true if initially show the table view, false = tree view
   */
  public PdoNavigationDialog(List<T> list, SelectionFilter selectionFilter, boolean showTable)  {
    this(null, list, selectionFilter, PdoNavigationPanel.SHOW_BUTTONS, showTable);
  }

  /**
   * Creates a navigation dialog for a list of objects.
   *
   * @param list the list of objects
   * @param selectionFilter filter selectable objects, null if nothing selectable
   */
  public PdoNavigationDialog(List<T> list, SelectionFilter selectionFilter)  {
    this(null, list, selectionFilter, PdoNavigationPanel.SHOW_BUTTONS, false);
  }


  /**
   * Creates a navigation dialog for a single object.
   *
   * @param owner the owner window of this dialog, null if none
   * @param obj the database object
   * @param selectionFilter filter selectable objects, null if nothing selectable
   * @param buttonMode the visibility of buttons, one of {@code PdoNavigationPanel.SHOW_...}
   * @param showTable true if initially show the table view, false = tree view
   */
  public PdoNavigationDialog(Window owner, T obj, SelectionFilter selectionFilter, int buttonMode, boolean showTable)  {
    super(owner);
    naviPanel = Rdc.createPdoNavigationPanel(obj, selectionFilter, buttonMode, showTable);
    setup();
  }

  /**
   * Creates a navigation dialog for a single object.
   *
   * @param owner the owner window of this dialog, null if none
   * @param obj the database object
   * @param selectionFilter filter selectable objects, null if nothing selectable
   */
  public PdoNavigationDialog(Window owner, T obj, SelectionFilter selectionFilter)  {
    this(owner, obj, selectionFilter, PdoNavigationPanel.SHOW_BUTTONS, false);
  }

  /**
   * Creates a navigation dialog for a single object.
   *
   * @param obj the database object
   * @param selectionFilter filter selectable objects, null if nothing selectable
   * @param showTable true if initially show the table view, false = tree view
   */
  public PdoNavigationDialog(T obj, SelectionFilter selectionFilter, boolean showTable)  {
    this(null, obj, selectionFilter, PdoNavigationPanel.SHOW_BUTTONS, showTable);
  }

  /**
   * Creates a navigation dialog for a single object.
   *
   * @param obj the database object
   * @param selectionFilter filter selectable objects, null if nothing selectable
   */
  public PdoNavigationDialog(T obj, SelectionFilter selectionFilter)  {
    this(null, obj, selectionFilter, PdoNavigationPanel.SHOW_BUTTONS, false);
  }




  /**
   * Gives access to the navigation panel.
   *
   * @return the navigation panel
   */
  public PdoNavigationPanel<T> getNaviPanel() {
    return naviPanel;
  }



  /**
   * Displays the dialog.<br>
   * Waits for selection if the dialog is modal. (default)
   *
   * @return the selected PersistentDomainObject, null if no selection or non-modal
   */
  public PersistentDomainObject<?> showDialog() {
    setVisible(true);
    return selectedObject;
  }




  private void setup()  {
    initComponents();
    naviPanel.addActionListener(this::naviPanel_actionPerformed);
    this.getContentPane().add(naviPanel, BorderLayout.CENTER);

    Class<T> clazz = naviPanel.getTableClass();
    if (clazz != null)  {
      // all objects of the same class -> table view allowed
      try {
        setTitle(MessageFormat.format(RdcSwingRdcBundle.getString("{0} BROWSER"), Pdo.create(clazz).getSingular()));
      }
      catch (Exception ex)  {
        setTitle(RdcSwingRdcBundle.getString("BROWSER") );
      }
    }
    pack();
  }


  private void naviPanel_actionPerformed(ActionEvent e) {
    selectedObject = naviPanel.getSelectedObject();
    dispose();
  }



  /** This method is called from within the constructor to
   * initialize the form.
   * WARNING: Do NOT modify this code. The content of this method is
   * always regenerated by the Form Editor.
   */
  // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
  private void initComponents() {

    setAutoPosition(true);
    setTitle(RdcSwingRdcBundle.getString("BROWSER")); // NOI18N
    setModal(true);

    pack();
  }// </editor-fold>//GEN-END:initComponents


  // Variables declaration - do not modify//GEN-BEGIN:variables
  // End of variables declaration//GEN-END:variables

}
