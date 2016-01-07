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

import java.util.Locale;



/**
 * A combobox for months as a spinfield.
 *
 * @author harald
 */
@SuppressWarnings("serial")
public class MonthSpinField extends FormSpinField implements SpinListener {

  private MonthComboBox monthCombo;


  /**
   * Creates a month combobox for the given locale.
   *
   * @param locale the locale, null if default
   */
  public MonthSpinField(Locale locale) {
    monthCombo = new MonthComboBox(locale);
    setFormComponent(monthCombo);
    addSpinListener(this);
  }


  /**
   * Creates a month combobox for the default locale.
   */
  public MonthSpinField()  {
    this(null);
  }

  /**
   * Sets the month.
   *
   * @param month the month value 0-11
   */
  public void setMonth(int month) {
    monthCombo.setMonth(month);
  }

  /**
   * Gets the month.
   *
   * @return the month value 0-11, -1 if nothing selected
   */
  public int getMonth() {
    return monthCombo.getMonth();
  }



  @Override
  public void increment (SpinEvent e, FormComponent c)  {
    int index = monthCombo.getSelectedIndex() + 1;
    if (index > 11) {
      index = 0;
    }
    monthCombo.setMonth(index);
  }

  @Override
  public void decrement (SpinEvent e, FormComponent c)  {
    int index = monthCombo.getSelectedIndex() - 1;
    if (index < 0) {
      index = 11;
    }
    monthCombo.setMonth(index);
  }

}
