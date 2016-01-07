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

import java.text.DateFormatSymbols;
import java.util.Locale;

/**
 * A combobox for months.
 * 
 * @author harald
 */
@SuppressWarnings("serial")
public class MonthComboBox extends FormComboBox<String> {


  /**
   * Creates a month combobox for the given locale.
   *
   * @param locale the locale, null if default
   */
  public MonthComboBox(Locale locale) {
    setMaximumRowCount(12);
    setMultiKeySelectionManager(true);
    if (locale == null) {
      locale = Locale.getDefault();
    }
    for (String month: new DateFormatSymbols(locale).getMonths())  {
      if (month != null && !month.isEmpty()) {
        addItem(month);
      }
    }
  }

  public MonthComboBox() {
    this(null);
  }


  /**
   * Sets the month.
   *
   * @param month the month value 0-11
   */
  public void setMonth(int month) {
    month %= 12;
    if (month < 0) {
      month += 12;
    }
    setFormValueIndex(month);
  }

  /**
   * Gets the month.
   *
   * @return the month value 0-11, -1 if nothing selected
   */
  public int getMonth() {
    return getSelectedIndex();
  }

}
