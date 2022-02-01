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

// Created on December 11, 2004, 6:13 PM

package org.tentackle.swing.rdc;

import org.tentackle.log.Logger.Level;
import org.tentackle.swing.FormUtilities;
import org.tentackle.swing.TooltipDisplay;
import org.tentackle.swing.plaf.PlafUtilities;

import javax.swing.AbstractListModel;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.ListSelectionModel;
import java.awt.Component;
import java.awt.Font;
import java.util.Arrays;
import java.util.List;

/**
 * Provides a tooltip-like info field and an error-list the user can
 * click on set the focus to the related component.
 *
 * @author harald
 */
public class TooltipAndErrorPanel extends org.tentackle.swing.FormPanel implements TooltipDisplay {

  private static final long serialVersionUID = 1L;

  private List<InteractiveError> errors;  // current errors
  private final ErrorModel model;         // model for JList
  private int visibleErrorCount = 4;      // the number of visible errors simultaneously

  /**
   * Creates the panel.
   */
  @SuppressWarnings("unchecked")
  public TooltipAndErrorPanel() {
    initComponents();
    tipField.setFont(tipField.getFont().deriveFont(Font.PLAIN));    // always plain font
    setTooltipEnabled(false);
    model = new ErrorModel();
    errorPane.setVisible(false);
    errorList.setModel(model);
    errorList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    errorList.setCellRenderer(new TooltipAndErrorPanel.ErrorCellRenderer());
    errorList.addListSelectionListener(e -> {
      if (!e.getValueIsAdjusting()) {
        InteractiveError error = errorList.getSelectedValue();
        if (error != null) {
          error.showComponent();
        }
      }
    });
  }



  @Override
  public boolean areValuesChanged() {
    return false;
  }



  /**
   * Enables/disables the tooltip-field.
   *
   * @param enable true to enable the tooltip display, default is false.
   */
  public void setTooltipEnabled(boolean enable) {
    tipField.setVisible(enable);
  }

  /**
   * Returns whether the tooltip display is enabled.
   *
   * @return true if the tooltip-field is visible
   */
  public boolean isTooltipEnabled() {
    return tipField.isVisible();
  }


  /**
   * Sets the maximum number of visible rows in the error list.<br>
   * If more errors are present a scrollbar will be shown.
   * The default is 4.
   *
   * @param rows the number of rows
   */
  public void setVisibleErrorCount(int rows)  {
    visibleErrorCount = rows;
    updateVisibleRows();
  }


  /**
   * Gets the maximum number of visible rows in error list.
   *
   * @return the number of rows.
   */
  public int getVisibleErrorCount()  {
    return visibleErrorCount;
  }



  @Override
  public void setTooltip(String tooltip)  {
    tipField.setText(tooltip);
  }


  /**
   * Gets the displayed tooltip.
   * @return the tooltip, null if none
   */
  public String getTooltip()  {
    return tipField.getText();
  }


  /**
   * Clears all errors and removes the errorpane.
   */
  public void clearErrors() {
    if (errors != null) {
      errors = null;
      errorPane.setVisible(false);
      FormUtilities.getInstance().packParentWindow(this);
    }
  }


  /**
   * Gets the current errors as a List of InteractiveErrors
   *
   * @return the list of errors, null if none
   */
  public List<InteractiveError> getErrors() {
    return errors;
  }

  /**
   * Sets the list of errors.
   *
   * @param errors the list of InteractiveErrors, null = clears all errors
   */
  public void setErrors(List<InteractiveError> errors)  {
    if (errors == null || errors.isEmpty()) {
      clearErrors();
    }
    else {
      this.errors = errors;
      updateVisibleRows();
      model.fireListChanged();
      errorPane.setVisible(true);
      InteractiveError ierr = errors.get(0);
      if (ierr.getFormComponent() != null) {
        // set focus on first component, cause the user will do that anyway
        ierr.getFormComponent().requestFocusLater();
      }
      errorList.clearSelection();
      FormUtilities.getInstance().packParentWindow(this);
    }
  }


  /**
   * Sets the errors.<br>
   * Alternative to {@link #setErrors(java.util.List)} saves typing if number
   * of errors is known.
   * <pre>
   * Example:
   *  setErrors(new InteractiveError("blah not set", blahField),
   *            new InteractiveError("foo must be less than blah", fooField));
   * </pre>
   *
   * @param error the error(s) to set
   */
  public void setErrors(InteractiveError... error) {
    setErrors(Arrays.asList(error));
  }



  // updates the number of visible rows
  private void updateVisibleRows() {
    if (errors != null) {
      int rows = errors.size();
      if (rows > visibleErrorCount) {
        rows = visibleErrorCount;
      }
      errorList.setVisibleRowCount(rows);
    }
  }



  private class ErrorModel extends AbstractListModel<InteractiveError> {

    private static final long serialVersionUID = 1L;

    @Override
    public int getSize() {
      return errors == null ? 0 : errors.size();
    }

    @Override
    public InteractiveError getElementAt(int i) {
      return errors == null ? null : errors.get(i);
    }

    public void fireListChanged() {
      fireContentsChanged(this, 0, errors.size() - 1);
    }
  }


  private static class ErrorCellRenderer extends DefaultListCellRenderer {

    private static final long serialVersionUID = 1L;

    @Override
    public Component getListCellRendererComponent(
                         JList<?> list,
                         Object value,
                         int index,
                         boolean isSelected,
                         boolean cellHasFocus)  {

      JLabel comp = (JLabel) super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
      if (value instanceof InteractiveError) {
        setIcon(((InteractiveError) value).getLevel() == Level.SEVERE
                ? PlafUtilities.getInstance().getIcon("error") : PlafUtilities.getInstance().getIcon("warning"));
      }
      return comp;
    }
  }



  /** This method is called from within the constructor to
   * initialize the form.
   * WARNING: Do NOT modify this code. The content of this method is
   * always regenerated by the Form Editor.
   */
  // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
  private void initComponents() {

    tipField = new org.tentackle.swing.StringFormField();
    errorPane = new javax.swing.JScrollPane();
    errorList = new javax.swing.JList<>();

    setTriggerValuesChangedEnabled(false);
    setLayout(new java.awt.BorderLayout());

    tipField.setChangeable(false);
    tipField.setName("tipField"); // NOI18N
    add(tipField, java.awt.BorderLayout.NORTH);

    errorList.setBackground(org.tentackle.swing.plaf.PlafUtilities.getInstance().getAlarmBackgroundColor());
    errorList.setFocusable(false);
    errorList.setName("errorList"); // NOI18N
    errorPane.setViewportView(errorList);

    add(errorPane, java.awt.BorderLayout.CENTER);
  }// </editor-fold>//GEN-END:initComponents


  // Variables declaration - do not modify//GEN-BEGIN:variables
  private javax.swing.JList<InteractiveError> errorList;
  private javax.swing.JScrollPane errorPane;
  private org.tentackle.swing.StringFormField tipField;
  // End of variables declaration//GEN-END:variables

}
