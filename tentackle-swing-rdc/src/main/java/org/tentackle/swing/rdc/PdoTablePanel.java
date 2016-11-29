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

// Created on August 20, 2002, 7:58 PM

package org.tentackle.swing.rdc;

import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import javax.swing.JButton;
import javax.swing.ListSelectionModel;
import javax.swing.event.TableModelListener;
import org.tentackle.pdo.Pdo;
import org.tentackle.pdo.PersistentDomainObject;
import org.tentackle.swing.FormButton;
import org.tentackle.swing.FormPanel;
import org.tentackle.swing.FormTable;
import org.tentackle.swing.FormTableEntry;
import org.tentackle.swing.FormTableModel;
import org.tentackle.swing.FormTableSorter;
import org.tentackle.swing.FormUtilities;
import org.tentackle.swing.mocking.MockFormTableEntry;



/*
 * A table to edit a list of database objects.<br>
 *
 * @author harald
 */
public class PdoTablePanel<T extends PersistentDomainObject<T>> extends org.tentackle.swing.FormPanel {

  private static final long serialVersionUID = -7533825918254146044L;

  private boolean                     templateChanged;        // true = template changed -> setup datatable again if listChanged
  private List<T>                     objList;                // list of objects
  private boolean                     ordered;                // ordered List?
  private boolean                     unique;                 // true if objects cannot be added more than once
  private FormTableModel<T>           dataModel;              // data model
  private FormTableSorter<T>          dataSorter;             // data sorter
  private FormTable<T>                dataTable;              // the table itself
  private T                           current;                // current selected object, null = none
  private int                         rowIndex;               // current row index of selected object, -1 = none
  private boolean                     forceSort;              // some record appended
  private int                         buttonMask;             // enable/disabled buttons
  private boolean                     buttonsLessObstrusive;  // true = less obstrusive
  private boolean                     newBySearch;            // true = new button invokes search dialog
  private FormTableEntry<T>           template;               // table template


  public static final String ACTION_SAVE   = "save";          // save button pressed
  public static final String ACTION_CANCEL = "cancel";        // cancel button pressed

  public static final int SHOW_CANCEL_BUTTON   = 0x01;
  public static final int SHOW_DELETE_BUTTON   = 0x02;
  public static final int SHOW_DOWN_BUTTON     = 0x04;
  public static final int SHOW_UP_BUTTON       = 0x08;
  public static final int SHOW_SAVE_BUTTON     = 0x10;
  public static final int SHOW_NEW_BUTTON      = 0x20;



  /**
   * Creates a table panel.
   *
   * @param template  template for the table-row
   * @param objList   list of PDOs
   * @param ordered   true if objList is an ordered list, i.e. the position in the objList is relevant
   * @param prefName  is the FormTable-Name for preferences (installs FormTableUtility-Menu too), null = none (no Menu either)
   */
  public PdoTablePanel(FormTableEntry<T> template,
                       List<T> objList,
                       boolean ordered, String prefName) {

    initComponents();

    // initial button visibility
    if (!ordered)  {
      buttonMask = SHOW_CANCEL_BUTTON | SHOW_DELETE_BUTTON | SHOW_SAVE_BUTTON | SHOW_NEW_BUTTON;
    }
    else  {
      buttonMask = SHOW_CANCEL_BUTTON | SHOW_DELETE_BUTTON | SHOW_SAVE_BUTTON | SHOW_NEW_BUTTON | SHOW_DOWN_BUTTON | SHOW_UP_BUTTON;
    }

    setVisibleButtons(buttonMask);

    // setup the rest
    setup(template, objList, ordered, prefName);
  }


  /**
   * Creates an empty table panel.
   * (Constructor for UI-Designer)
   */
  @SuppressWarnings("unchecked")
  public PdoTablePanel() {
    this(new MockFormTableEntry(), new ArrayList<>(), false, null);
  }



  /**
   * Creates a table panel.
   *
   * @param template  the template for the table-row
   * @param objList   the list of PDOs
   * @param ordered   true if objList is an ordered list, i.e. the position in the objList is relevant
   * @param prefName  the FormTable-Name for preferences (installs FormTableUtility-Menu too), null = none (no Menu either)
   */
  @SuppressWarnings("unchecked")
  public void setup(FormTableEntry<T> template,
                    List<T> objList,
                    boolean ordered, String prefName) {

    this.template  = template;
    this.objList   = objList;
    this.current   = null;
    this.rowIndex  = -1;
    this.forceSort = false;

    setOrdered(ordered);

    // setup table
    dataTable = createFormTable();

    if (dataModel != null) {
      dataModel.setTemplate(template);
    }
    else  {
      dataModel = createFormTableModel(template);
    }

    dataSorter = null;
    if (ordered) {
      dataTable.setAutoCreateRowSorter(false);  // clear for sure
      dataTable.setRowSorter(null);
    }
    else  {
      if (dataTable.getRowSorter() == null) {
        dataSorter = new FormTableSorter<>(dataModel);
      }
    }

    dataTable.addListSelectionListener((e) -> {
      if (!e.getValueIsAdjusting()) {
        updateSelection();
      }
    });

    dataTable.setDragEnabled(true);
    dataTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    dataTable.setCellTraversal(FormTable.CELLTRAVERSAL_COLUMN);
    dataTable.setSurrendersFocusOnKeystroke(true);
    dataTable.setDefaultRenderer(PersistentDomainObject.class, new PdoTableCellRenderer());
    setTableName(prefName);
    dateScrollPane.setViewportView(dataTable);
    dataTable.setModel(dataSorter == null ? dataModel : dataSorter);
    dataModel.listChanged(objList);

    updateSelection();
  }



  /**
   * Creates the formtable.
   *
   * @return the created formtable
   */
  @SuppressWarnings("unchecked")
  public FormTable<T> createFormTable() {
    return (FormTable<T>) Rdc.createFormTable();
  }


  /**
   * Creates the formtable model for a given object.
   * <p>
   * The default implementation just creates the model and binds it.
   *
   * @param template the formtable entry
   * @return the table model
   */
  public FormTableModel<T> createFormTableModel(FormTableEntry<T> template) {
    FormTableModel<T> model = new FormTableModel<>(template);
    model.bind();
    return model;
  }


  /**
   * Gets the button panel.<br>
   * Applications may add or remove buttons.
   *
   * @return the button panel
   */
  public FormPanel getButtonPanel() {
    return buttonPanel;
  }


  /**
   * Defines whether the data is ordered or not.
   *
   * @param ordered true if sort necessary, false no sort
   */
  public void setOrdered(boolean ordered) {
    this.ordered = ordered;
  }

  /**
   * Gets the ordered feature.
   *
   * @return true if ordered
   */
  public boolean isOrdered() {
    return ordered;
  }


  /**
   * Defines what happens when the new-button is pressed.
   * By default an empty object will be inserted.
   *
   * @param newBySearch true if open a search dialog to select object, else createPdo empty object
   */
  public void setNewBySearch(boolean newBySearch) {
    this.newBySearch = newBySearch;
  }

 /**
   * Gets the setting what happens when the new-button is pressed.
   *
   * @return true if open a search dialog to select object, else createPdo empty object
   */
  public boolean getNewBySearch() {
    return newBySearch;
  }


  /**
   * If newBySearch is enabled this method is invoked to run the search.
   * The default implementation just runs a modal PdoSearchDialog.
   *
   * @return the object, null if search cancelled
   */
  @SuppressWarnings("unchecked")
  public T searchObject() {
    T pdo = template.getObject();
    return (T) Rdc.createPdoSearchDialog(this, pdo.getDomainContext(), pdo.getEffectiveClass(),
                  (o) -> pdo.getEffectiveClass().isAssignableFrom(o.getClass()),
                  false, true).showDialog();
  }


  /**
   * Defines whether objects may not appear more than once.<br>
   * Setting unique makes only sense if newBySearch is enabled as well.
   * <p>
   * Notice that the initial object list is not checked, i.e. the
   * flag applies only to newly added objects.
   *
   * @param unique true if objects may not appear more than once
   */
  public void setUnique(boolean unique) {
    this.unique = unique;
  }

  /**
   * Gets the flag whether objects can be added more than once.
   *
   * @return true if objects are unique, false if not (default)
   */
  public boolean isUnique() {
    return unique;
  }




  /**
   * Sets the table name which is used to load the preferences.
   * Setting the name will also install a FormTableUtilityPopup.
   *
   * @param prefName the table name, null = none (uninstalls the popup, if any)
   */
  public void setTableName(String prefName) {
    dataTable.setName(prefName);
    if (prefName != null)  {
      dataTable.setCreateDefaultColumnsFromPreferences(true);
      dataTable.setUtilityPopup(Rdc.createPopup());
    }
    else  {
      dataTable.setCreateDefaultColumnsFromPreferences(false);
      dataTable.setUtilityPopup(null);
    }
  }


  /**
   * Gets the table preferences name.
   *
   * @return the preferences name
   */
  public String getTableName() {
    return dataTable.getName();
  }



  /**
   * Sets the visible buttons mask.
   *
   * @param mask is a bitmask of SHOW_..._BUTTON
   */
  public void setVisibleButtons(int mask) {
    buttonMask = mask;
    cancelButton.setVisible(isCancelButtonVisible());
    deleteButton.setVisible(isDeleteButtonVisible());
    moveDownButton.setVisible(isDownButtonVisible());
    moveUpButton.setVisible(isUpButtonVisible());
    saveButton.setVisible(isSaveButtonVisible());
    newButton.setVisible(isNewButtonVisible());
  }

  /**
   * Gets the visible buttons mask
   *
   * @return the bitmask of SHOW_...._BUTTON
   */

  public int getVisibleButtons()  {
    return buttonMask;
  }



  // same for singe buttons (from IDE)

  /**
   * Sets the visibility of the cancel button.
   *
   * @param visible is true if visible, false if not
   */
  public void setCancelButtonVisible(boolean visible) {
    buttonMask = visible ? (buttonMask | SHOW_CANCEL_BUTTON) : (buttonMask & ~SHOW_CANCEL_BUTTON);
    cancelButton.setVisible(visible);
  }

  /**
   * Gets the visibility of the cancel button.
   *
   * @return true if visible, false if not
   */
  public boolean isCancelButtonVisible() {
    return (buttonMask & SHOW_CANCEL_BUTTON) != 0;
  }


  /**
   * Sets the visibility of the delete button.
   *
   * @param visible is true if visible, false if not
   */
  public void setDeleteButtonVisible(boolean visible) {
    buttonMask = visible ? (buttonMask | SHOW_DELETE_BUTTON) : (buttonMask & ~SHOW_DELETE_BUTTON);
    deleteButton.setVisible(visible);
  }

  /**
   * Gets the visibility of the delete button.
   *
   * @return true if visible, false if not
   */
  public boolean isDeleteButtonVisible() {
    return (buttonMask & SHOW_DELETE_BUTTON) != 0;
  }



  /**
   * Sets the visibility of the down button.
   *
   * @param visible is true if visible, false if not
   */
  public void setDownButtonVisible(boolean visible) {
    buttonMask = visible ? (buttonMask | SHOW_DOWN_BUTTON) : (buttonMask & ~SHOW_DOWN_BUTTON);
    moveDownButton.setVisible(visible);
  }

  /**
   * Gets the visibility of the down button.
   *
   * @return true if visible, false if not
   */
  public boolean isDownButtonVisible() {
    return (buttonMask & SHOW_DOWN_BUTTON) != 0;
  }


  /**
   * Sets the visibility of the up button.
   *
   * @param visible is true if visible, false if not
   */
  public void setUpButtonVisible(boolean visible) {
    buttonMask = visible ? (buttonMask | SHOW_UP_BUTTON) : (buttonMask & ~SHOW_UP_BUTTON);
    moveUpButton.setVisible(visible);
  }

  /**
   * Gets the visibility of the up button.
   *
   * @return true if visible, false if not
   */
  public boolean isUpButtonVisible() {
    return (buttonMask & SHOW_UP_BUTTON) != 0;
  }


  /**
   * Sets the visibility of the save button.
   *
   * @param visible is true if visible, false if not
   */
  public void setSaveButtonVisible(boolean visible) {
    buttonMask = visible ? (buttonMask | SHOW_SAVE_BUTTON) : (buttonMask & ~SHOW_SAVE_BUTTON);
    saveButton.setVisible(visible);
  }

  /**
   * Gets the visibility of the save button.
   *
   * @return true if visible, false if not
   */
  public boolean isSaveButtonVisible() {
    return (buttonMask & SHOW_SAVE_BUTTON) != 0;
  }


  /**
   * Sets the visibility of the new button.
   *
   * @param visible is true if visible, false if not
   */
  public void setNewButtonVisible(boolean visible) {
    buttonMask = visible ? (buttonMask | SHOW_NEW_BUTTON) : (buttonMask & ~SHOW_NEW_BUTTON);
    newButton.setVisible(visible);
  }

  /**
   * Gets the visibility of the save button.
   *
   * @return true if visible, false if not
   */
  public boolean isNewButtonVisible() {
    return (buttonMask & SHOW_NEW_BUTTON) != 0;
  }




  /**
   * Makes the buttons less obtrusive.
   * Useful if PdoTablePanel is embedded in another dialog with buttons.
   *
   * @param flag true for less obstrusive buttons, false is default.
   */
  public void setButtonsLessObtrusive(boolean flag) {
    buttonsLessObstrusive = flag;
    setButtonLessObtrusive(cancelButton, flag);
    setButtonLessObtrusive(deleteButton, flag);
    setButtonLessObtrusive(moveDownButton, flag);
    setButtonLessObtrusive(moveUpButton, flag);
    setButtonLessObtrusive(saveButton, flag);
    setButtonLessObtrusive(newButton, flag);
  }


  /**
   * Get access to the cancel button.
   *
   * @return the cancel button
   */
  public FormButton getCancelButton() {
    return cancelButton;
  }

  /**
   * Get access to the delete button.
   *
   * @return the delete button
   */
  public FormButton getDeleteButton() {
    return deleteButton;
  }

  /**
   * Get access to the move-down button.
   *
   * @return the move-down button
   */
  public FormButton getMoveDownButton() {
    return moveDownButton;
  }

  /**
   * Get access to the move-up button.
   *
   * @return the move-up button
   */
  public FormButton getMoveUpButton() {
    return moveUpButton;
  }

  /**
   * Get access to the save button.
   *
   * @return the save button
   */
  public FormButton getSaveButton() {
    return saveButton;
  }

  /**
   * Get access to the new button.
   *
   * @return the new button
   */
  public FormButton getNewButton() {
    return newButton;
  }



  /**
   * @return true if buttons are less obstrusive.
   */
  public boolean getButtonsLessObtrusive() {
    return buttonsLessObstrusive;
  }


  private void setButtonLessObtrusive(JButton button, boolean flag) {
    if (flag) {
      button.setMargin(new Insets(0,0,0,0));
      button.setBorderPainted(false);
    }
    else  {
      button.setMargin(new Insets(1,3,1,3));
      button.setBorderPainted(true);
    }
  }


  /**
   * Changes the template (necessary if context changed).
   *
   * @param template the template
   */
  public void setTemplate(FormTableEntry<T> template)  {
    if (!Objects.equals(this.template, template)) {
      this.template = template;
      templateChanged = true;
      dataModel.setTemplate(template);
      dataModel.bind();
    }
  }

  /**
   * Gets the template.
   *
   * @return the current template
   */
  public FormTableEntry<T> getTemplate() {
    return template;
  }



  @Override
  public void setChangeable(boolean changeable)  {
    super.setChangeable(changeable);
    if (changeable) {
      updateSelection();
    }
    else  {
      cancelButton.setEnabled(true);  // should always work, if visible
    }
  }


  /**
   * Shows a list of data objects.
   *
   * @param objList the list of objects
   */
  @SuppressWarnings("unchecked")
  public void listChanged (List<T> objList)  {
    this.objList = objList;
    if (templateChanged) {
      templateChanged = false;
      setup(template, objList, ordered, getTableName());
    }
    else  {
      dataModel.listChanged(objList);
      dataTable.clearSelection();
    }
  }

  /**
   * Gets status info whether data has been changed or not.
   *
   * @return true if model data has been changed
   */
  public boolean isDataChanged()  {
    return dataModel.isDataChanged();
  }



  /**
   * Gets the table.
   *
   * @return the FormTable
   */
  public FormTable<T> getFormTable() {
    return dataTable;
  }


  /**
   * Gets the table model.
   *
   * @return the table model
   */
  public FormTableModel<T> getFormTableModel() {
    return dataModel;
  }


  /**
   * Gets the table sorter.
   *
   * @return the sorter
   */
  public FormTableSorter<T> getFormTableSorter() {
    return dataSorter;
  }


  /**
   * Adds a TableModelListener.
   *
   * @param l the listener to add
   */
  public void addTableModelListener (TableModelListener l) {
    dataModel.addTableModelListener(l);
  }

  /**
   * Removes a TableModelListener.
   *
   * @param l the listener to remove
   */
  public void removeTableModelListener (TableModelListener l) {
    dataModel.removeTableModelListener(l);
  }



  /**
   * Moves the current object (row) in list up or down.
   *
   * @param down true if move down, else up
   */
  public void move(boolean down) {
    int swapIndex = rowIndex + (down ? 1 : -1);
    int currentRowHeight = dataTable.getRowHeight(rowIndex);
    int swapRowHeight = dataTable.getRowHeight(swapIndex);
    T swapObject = objList.get(swapIndex);
    objList.set(swapIndex, current);
    objList.set(rowIndex, swapObject);
    if (down) {
      dataModel.listUpdated(rowIndex, swapIndex);
    }
    else  {
      dataModel.listUpdated(swapIndex, rowIndex);
    }
    dataTable.setRowHeight(rowIndex, swapRowHeight);
    dataTable.setRowHeight(swapIndex, currentRowHeight);
    dataTable.setSelectedRow(swapIndex);
  }



  /**
   * Adds an object to the list.<br>
   * In unordered lists the object will always be appended to the
   * end of the list.
   * If the is ordered and there is only one row in the table, the object will be
   * appended after the current row.
   * Otherwise the object will be inserted before the current row and
   * the list gets sorted.
   * <p>
   * Notice: if the unique attribute is set, objects will not be inserted more than once.
   *
   * @param object the object to add
   * @return true if added, false object is null or unique==true and object already in list
   */
  public boolean add(T object) {
    if (object != null && (!unique || !objList.contains(object))) {
      if (ordered)  {
        int itemIndex = rowIndex + (objList.size() <= 1 ? 1 : 0);
        if (itemIndex < 0) {
          itemIndex = 0;    // in case rowIndex < 0
        }
        objList.add(itemIndex, object);
        dataModel.listInserted(itemIndex, itemIndex);
        dataTable.setSelectedRow(itemIndex);
      }
      else {
        objList.add(object);
        int itemIndex = objList.size() - 1;
        dataModel.listInserted(itemIndex, itemIndex);
        int tableRow = dataSorter == null ? itemIndex : dataSorter.getModelIndex(itemIndex);
        dataTable.setSelectedRow(tableRow);
        forceSort = true;
      }
      return true;
    }
    else  {
      return false;
    }
  }


  /**
   * Deletes the current object (row).
   *
   * @return true if deleted, false if rowIndex invalid
   */
  public boolean delete() {
    if (rowIndex >= 0) {    // rowIndex corresponds to the dataModel
      int tableIndex = dataTable.getSelectedRow();    // corresponds to visible index
      objList.remove(rowIndex);
      dataModel.listDeleted(rowIndex, rowIndex);
      if (tableIndex >= objList.size()) {
        if (objList.isEmpty())  {
          dataTable.clearSelection();
        }
        else  {
          tableIndex = objList.size() - 1;
          dataTable.setSelectedRow(tableIndex);
        }
      }
      return true;
    }
    else  {
      return false;
    }
  }



  /**
   * notify all ActionListeners (usually only one!) that editing should
   * finish. Save or Cancel.
   */
  private void fireActionPerformed (String action) {
    fireActionPerformed(new ActionEvent (this, ActionEvent.ACTION_PERFORMED, action));
  }




  // updates the selection
  private void updateSelection()  {
    rowIndex = dataTable.getSelectedRow();
    if (dataSorter != null) {
      rowIndex = dataSorter.getModelIndex(rowIndex);
    }
    if (rowIndex >= 0 && rowIndex < objList.size()) {
      rowIndex = dataTable.convertRowIndexToModel(rowIndex); // in case Rowsorter is activated
      if (dataSorter != null) {
        rowIndex = dataSorter.getModelIndex(rowIndex);
      }
      current = objList.get(rowIndex);
      if (current != null && (current.isNew() || current.isRemovable())) {
        deleteButton.setEnabled(isChangeable());
      }
      else  {
        deleteButton.setEnabled(false);
      }
      newButton.setEnabled(isChangeable());
      moveUpButton.setEnabled(isChangeable() && rowIndex > 0);
      moveDownButton.setEnabled(isChangeable() && rowIndex < objList.size() - 1);
    }
    else  {
      current = null;
      newButton.setEnabled(isChangeable());
      deleteButton.setEnabled(false);
      moveUpButton.setEnabled(false);
      moveDownButton.setEnabled(false);
    }
    if (dataSorter != null && forceSort)  {
      dataSorter.sort();    // sort again
      forceSort = false;
    }
  }


  /**
   * Creates a pdo for the table.
   *
   * @return the pdo
   */
  protected T createPdo() {
    return Pdo.create(template.getObject());
  }


  /** This method is called from within the constructor to
   * initialize the form.
   * WARNING: Do NOT modify this code. The content of this method is
   * always regenerated by the Form Editor.
   */
  // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
  private void initComponents() {
    java.awt.GridBagConstraints gridBagConstraints;

    dateScrollPane = new javax.swing.JScrollPane();
    buttonPanel = new org.tentackle.swing.FormPanel();
    newButton = new org.tentackle.swing.FormButton();
    saveButton = new org.tentackle.swing.FormButton();
    deleteButton = new org.tentackle.swing.FormButton();
    cancelButton = new org.tentackle.swing.FormButton();
    moveUpButton = new org.tentackle.swing.FormButton();
    moveDownButton = new org.tentackle.swing.FormButton();
    jLabel1 = new javax.swing.JLabel();

    setLayout(new java.awt.BorderLayout());
    add(dateScrollPane, java.awt.BorderLayout.CENTER);

    buttonPanel.setLayout(new java.awt.GridBagLayout());

    newButton.setIcon(org.tentackle.swing.plaf.PlafUtilities.getInstance().getIcon("add"));
    newButton.setText(RdcSwingRdcBundle.getString("ADD")); // NOI18N
    newButton.setToolTipText(RdcSwingRdcBundle.getString("ADD A NEW ROW")); // NOI18N
    newButton.setMargin(new java.awt.Insets(1, 3, 1, 3));
    newButton.setName("add"); // NOI18N
    newButton.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        newButtonActionPerformed(evt);
      }
    });
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 0;
    gridBagConstraints.fill = java.awt.GridBagConstraints.VERTICAL;
    gridBagConstraints.insets = new java.awt.Insets(2, 2, 2, 2);
    buttonPanel.add(newButton, gridBagConstraints);

    saveButton.setIcon(org.tentackle.swing.plaf.PlafUtilities.getInstance().getIcon("save"));
    saveButton.setText(RdcSwingRdcBundle.getString("SAVE")); // NOI18N
    saveButton.setToolTipText(RdcSwingRdcBundle.getString("SAVE ALL ROWS")); // NOI18N
    saveButton.setMargin(new java.awt.Insets(1, 3, 1, 3));
    saveButton.setName("save"); // NOI18N
    saveButton.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        saveButtonActionPerformed(evt);
      }
    });
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 5;
    gridBagConstraints.gridy = 0;
    gridBagConstraints.fill = java.awt.GridBagConstraints.VERTICAL;
    gridBagConstraints.insets = new java.awt.Insets(2, 2, 2, 2);
    buttonPanel.add(saveButton, gridBagConstraints);

    deleteButton.setIcon(org.tentackle.swing.plaf.PlafUtilities.getInstance().getIcon("subtract"));
    deleteButton.setText(RdcSwingRdcBundle.getString("REMOVE")); // NOI18N
    deleteButton.setToolTipText(RdcSwingRdcBundle.getString("REMOVE ROW")); // NOI18N
    deleteButton.setMargin(new java.awt.Insets(1, 3, 1, 3));
    deleteButton.setName("remove"); // NOI18N
    deleteButton.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        deleteButtonActionPerformed(evt);
      }
    });
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 2;
    gridBagConstraints.gridy = 0;
    gridBagConstraints.fill = java.awt.GridBagConstraints.VERTICAL;
    gridBagConstraints.insets = new java.awt.Insets(2, 2, 2, 2);
    buttonPanel.add(deleteButton, gridBagConstraints);

    cancelButton.setIcon(org.tentackle.swing.plaf.PlafUtilities.getInstance().getIcon("close"));
    cancelButton.setText(RdcSwingRdcBundle.getString("CANCEL")); // NOI18N
    cancelButton.setToolTipText(RdcSwingRdcBundle.getString("DISCARD CHANGES AND CLOSE")); // NOI18N
    cancelButton.setMargin(new java.awt.Insets(1, 3, 1, 3));
    cancelButton.setName("cancel"); // NOI18N
    cancelButton.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        cancelButtonActionPerformed(evt);
      }
    });
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 6;
    gridBagConstraints.gridy = 0;
    gridBagConstraints.fill = java.awt.GridBagConstraints.VERTICAL;
    gridBagConstraints.insets = new java.awt.Insets(2, 2, 2, 2);
    buttonPanel.add(cancelButton, gridBagConstraints);

    moveUpButton.setIcon(org.tentackle.swing.plaf.PlafUtilities.getInstance().getIcon("up"));
    moveUpButton.setText(RdcSwingRdcBundle.getString("UP")); // NOI18N
    moveUpButton.setToolTipText(RdcSwingRdcBundle.getString("MOVE UP ONE ROW")); // NOI18N
    moveUpButton.setMargin(new java.awt.Insets(1, 3, 1, 3));
    moveUpButton.setName("up"); // NOI18N
    moveUpButton.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        moveUpButtonActionPerformed(evt);
      }
    });
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 3;
    gridBagConstraints.gridy = 0;
    gridBagConstraints.fill = java.awt.GridBagConstraints.VERTICAL;
    gridBagConstraints.insets = new java.awt.Insets(2, 2, 2, 2);
    buttonPanel.add(moveUpButton, gridBagConstraints);

    moveDownButton.setIcon(org.tentackle.swing.plaf.PlafUtilities.getInstance().getIcon("down"));
    moveDownButton.setText(RdcSwingRdcBundle.getString("DOWN")); // NOI18N
    moveDownButton.setToolTipText(RdcSwingRdcBundle.getString("MOVE DOWN ONE ROW")); // NOI18N
    moveDownButton.setMargin(new java.awt.Insets(1, 3, 1, 3));
    moveDownButton.setName("down"); // NOI18N
    moveDownButton.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        moveDownButtonActionPerformed(evt);
      }
    });
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 4;
    gridBagConstraints.gridy = 0;
    gridBagConstraints.fill = java.awt.GridBagConstraints.VERTICAL;
    gridBagConstraints.insets = new java.awt.Insets(2, 2, 2, 2);
    buttonPanel.add(moveDownButton, gridBagConstraints);
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 0;
    gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
    gridBagConstraints.weightx = 1.0;
    buttonPanel.add(jLabel1, gridBagConstraints);

    add(buttonPanel, java.awt.BorderLayout.SOUTH);
  }// </editor-fold>//GEN-END:initComponents

  private void moveDownButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_moveDownButtonActionPerformed
    move(true);
  }//GEN-LAST:event_moveDownButtonActionPerformed

  private void moveUpButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_moveUpButtonActionPerformed
    move(false);
  }//GEN-LAST:event_moveUpButtonActionPerformed

  private void cancelButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_cancelButtonActionPerformed
    fireActionPerformed(ACTION_CANCEL);
  }//GEN-LAST:event_cancelButtonActionPerformed

  private void deleteButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_deleteButtonActionPerformed
    // Datensatz entfernen
    if (delete()) {
      FormUtilities.getInstance().requestFocusLater(dataTable);
    }
  }//GEN-LAST:event_deleteButtonActionPerformed

  private void saveButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_saveButtonActionPerformed
    fireActionPerformed(ACTION_SAVE);
  }//GEN-LAST:event_saveButtonActionPerformed

  private void newButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_newButtonActionPerformed
    // create new object from template
    T pdo = newBySearch ? searchObject() : createPdo();
    if (add(pdo)) {
      dataTable.getColumnModel().getSelectionModel().setSelectionInterval(0, 0);  // first column
      dataTable.requestFocusInWindow();
    }
  }//GEN-LAST:event_newButtonActionPerformed


  // Variables declaration - do not modify//GEN-BEGIN:variables
  private org.tentackle.swing.FormPanel buttonPanel;
  private org.tentackle.swing.FormButton cancelButton;
  private javax.swing.JScrollPane dateScrollPane;
  private org.tentackle.swing.FormButton deleteButton;
  private javax.swing.JLabel jLabel1;
  private org.tentackle.swing.FormButton moveDownButton;
  private org.tentackle.swing.FormButton moveUpButton;
  private org.tentackle.swing.FormButton newButton;
  private org.tentackle.swing.FormButton saveButton;
  // End of variables declaration//GEN-END:variables

}

