/**
 * Tentackle - http://www.tentackle.org
 * Copyright (C) 2001-2008 Harald Krake, harald@krake.de, +49 7722 9508-0
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
import java.text.DateFormatSymbols;


/**
 * A ComboBox to edit the day of week.
 * 
 * @author harald
 */
@SuppressWarnings("serial")
public class WeekdayComboBox extends FormFieldComboBox<String> {

  /**
   * Creates a WeekdayComboBox.
   * 
   * @param locale the locale, null if default
   */
  public WeekdayComboBox(Locale locale) {
    if (locale == null) {
      locale = Locale.getDefault();
    }
    String[] days = new DateFormatSymbols(locale).getWeekdays();
    for (int i=1; i <= 7; i++)  {
      addItem(days[i]);
    }
  }

  /**
   * Creates a WeekdayComboBox for the current locale.
   */
  public WeekdayComboBox()  {
    this(null);
  }
  
  
  /**
   * Sets the weekday.
   * 
   * @param weekday the weekday value 0-6, -1 to deselect
   */
  public void setWeekday(int weekday) {
    setFireRunning(true);
    setSelectedIndex(weekday);
    setFireRunning(false);
  }

  /**
   * Gets the weekday.
   * 
   * @return the weekday value 0-6, -1 if nothing selected
   */
  public int getWeekday() {
    return getSelectedIndex();
  }
  
}