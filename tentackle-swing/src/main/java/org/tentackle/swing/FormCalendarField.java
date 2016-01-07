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
import java.util.Date;
import org.tentackle.swing.DateFormField.ReferenceDateProvider;
import org.tentackle.swing.plaf.PlafUtilities;


/**
 *
 * @author harald
 */
@SuppressWarnings("serial")
public class FormCalendarField extends FormFieldComponentPanel implements SqlDateField {

  static {
    // register the special binding due to DMoney/BMoney dual use
    FormUtilities.getInstance().getBindingFactory().setFormComponentBindingClass(FormCalendarField.class, SqlDateFieldBinding.class);
  }

  /** show only date (default) **/
  public static final int SHOW_AUTO  = -1; // set timeMode from format

  private int timeMode;



  /**
   * Creates a calendar field.
   */
  public FormCalendarField() {
    super(new DateFormField());
    timeMode = SHOW_AUTO;    // derive from format
    initComponents();
  }


  @Override
  public void setFormComponent(FormComponent comp) {
    throw new GUIRuntimeException("changing the component is not allowed");
  }

  @Override
  public DateFormField getFormComponent() {
    return (DateFormField) super.getFormComponent();
  }

  /**
   * {@inheritDoc}
   * <p>
   * Overridden to set the names in subcomponents.
   */
  @Override
  public void setName(String name) {
    super.setName(name);
    if (name != null) {
      ((Component) getFormComponent()).setName(name + "/date");
      calButton.setName(name + "/calendar");
    }
    else  {
      ((Component) getFormComponent()).setName("date");
      calButton.setName("calendar");
    }
  }


  /**
   * Sets the time mode.<br>
   * One of {@code FormCalendar.SHOW_...}.
   *
   * @param timeMode the time mode, -1 if from date format (default)
   * @see FormCalendar
   */
  public void setTimeMode(int timeMode) {
    this.timeMode = timeMode;
  }

  /**
   * Gets the time mode.<br>
   * One of {@link FormCalendar}.SHOW_...
   *
   * @return the time mode, -1 if from date format (default)
   */
  public int getTimeMode()  {
    return timeMode;
  }


  /**
   * Sets the "lenient" flag in date format.
   *
   * @param lenient true if lenient
   */
  public void setLenient(boolean lenient) {
    dateField.setLenient(lenient);
  }

  /**
   * Gets the lenient flag.
   *
   * @return the lenient flag
   */
  public boolean isLenient() {
    return dateField.isLenient();
  }


  @Override
  public void setEnabled(boolean enabled) {
    super.setEnabled(enabled);
    checkButtonEnabled();
  }

  @Override
  public void setChangeable(boolean changeable) {
    super.setChangeable(changeable);
    checkButtonEnabled();
  }

  @Override
  public void setFormValue(Object value)  {
    super.setFormValue(value);
    checkButtonEnabled();
  }

  @Override
  public Date getFormValue()  {
    return dateField.getFormValue();
  }

  /**
   * Gets the {@code org.tentackle.misc.Date}.
   *
   * @return the SQL date
   */
  @Override
  public org.tentackle.common.Date getDate() {
    return dateField.getDate();
  }


  /**
   * Gets the {@code org.tentackle.misc.Timestamp}.
   * @return the SQL timestamp
   */
  @Override
  public org.tentackle.common.Timestamp getTimestamp() {
    return dateField.getTimestamp();
  }

  /**
   * Gets the {@code org.tentackle.misc.Time}.
   * @return the SQL time
   */
  @Override
  public org.tentackle.common.Time getTime() {
    return dateField.getTime();
  }


  /**
   * Gets the reference date.
   *
   * @return the reference date, null = today
   */
  public Date getReferenceDate() {
    return dateField.getReferenceDate();
  }

  /**
   * Sets the reference date.
   *
   * @param referenceDate the reference date, null = today
   */
  public void setReferenceDate(Date referenceDate) {
    dateField.setReferenceDate(referenceDate);
  }



  /**
   * Gets the default unit.
   *
   * @return the default unit, blank if default
   */
  public char getDefaultUnit() {
    return dateField.getDefaultUnit();
  }


  /**
   * Sets the default unit.
   * <p>
   * The default unit applies to number-only input, for example "6" in a time field.
   * By default, the unit is derived from the format and corresponds to the
   * smallest unit. For example, in "HH:mm" the default unit is minute and
   * in "yy/mm/dd" the default unit is day. Input of "6" will by default
   * be translated to "6 minutes (in current hour)", respectively 6th day
   * in current month.
   * If the default unit, however, is explicitly set to "H" (or "y") an input
   * of "6" will result in "06:00" ("06/xx/xx" respectively).
   *
   * @param defaultUnit the default unit (formatting char), blank or 0 if default
   */
  public void setDefaultUnit(char defaultUnit) {
    dateField.setDefaultUnit(defaultUnit);
  }


  /**
   * Gets the reference date provider.
   *
   * @return the provider, null if none
   */
  public ReferenceDateProvider getReferenceDateProvider() {
    return dateField.getReferenceDateProvider();
  }

  /**
   * Sets the reference date provider.
   * <p>
   * The provider is consulted whenever the input starts with a '@'.
   * It will be used to retrieve the reference date.
   * For example: "@+7" to add 7 days to a specific date from the model.
   *
   * @param refProvider the provider, null to clear
   */
  public void setReferenceDateProvider(ReferenceDateProvider refProvider) {
    dateField.setReferenceDateProvider(refProvider);
  }



  @Override
  public void setCellEditorUsage(boolean flag) {
    super.setCellEditorUsage(flag);
    /**
     * disable focus lost on datefield when used as a celleditor
     */
    calButton.setFocusable(!flag);
  }



  private void checkButtonEnabled() {
    calButton.setEnabled(getFormValue() != null || (isEnabled() && isChangeable()));
  }



  /** This method is called from within the constructor to
   * initialize the form.
   * WARNING: Do NOT modify this code. The content of this method is
   * always regenerated by the Form Editor.
   */
  // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
  private void initComponents() {
    java.awt.GridBagConstraints gridBagConstraints;

    dateField = (DateFormField)getFormComponent();
    calButton = new org.tentackle.swing.FormButton();

    setLayout(new java.awt.GridBagLayout());

    dateField.setName("date"); // NOI18N
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
    gridBagConstraints.weightx = 1.0;
    add(dateField, gridBagConstraints);

    calButton.setIcon(PlafUtilities.getInstance().getIcon("calendar_mini"));
    calButton.setMargin(new java.awt.Insets(0, 0, 0, 0));
    calButton.setName("calendar"); // NOI18N
    calButton.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        calButtonActionPerformed(evt);
      }
    });
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.fill = java.awt.GridBagConstraints.VERTICAL;
    add(calButton, gridBagConstraints);
  }// </editor-fold>//GEN-END:initComponents

  private void calButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_calButtonActionPerformed
    // determine timemode from date
    int tmode = timeMode;
    if (tmode == SHOW_AUTO) {
      // from format
      tmode = FormCalendar.SHOW_DATE;
      String fmt = dateField.getFormat();
      if (fmt != null) { // can it be null?
        if (fmt.indexOf('H') >= 0 || fmt.indexOf('K') >= 0 ||
            fmt.indexOf('h') >= 0 || fmt.indexOf('k') >= 0) {
          tmode = FormCalendar.SHOW_HOUR;
          if (fmt.indexOf('m') >= 0) {
            tmode = FormCalendar.SHOW_MINUTE;
            if (fmt.indexOf('s') >= 0) {
              tmode = FormCalendar.SHOW_SECOND;
            }
          }
        }
      }
    }
    // open the dialog
    Date date = FormCalendarDialog.createFormCalendarDialog(
                    this, null, getFormValue(), tmode).showDialog();
    if (isChangeable() && isEnabled() && date != null) {
      setFormValue(date);
      if (!isCellEditorUsage() && isAutoUpdate()) {
        fireValueEntered();
      }
      requestFocusLater();
    }
  }//GEN-LAST:event_calButtonActionPerformed


  // Variables declaration - do not modify//GEN-BEGIN:variables
  private org.tentackle.swing.FormButton calButton;
  private org.tentackle.swing.DateFormField dateField;
  // End of variables declaration//GEN-END:variables

}
