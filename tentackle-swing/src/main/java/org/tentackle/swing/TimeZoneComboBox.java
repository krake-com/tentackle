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

// Created on October 13, 2004, 11:06 AM

package org.tentackle.swing;

import java.util.Arrays;
import java.util.TimeZone;

/**
 * Combobox for all supported timezones.
 *
 * @author harald
 */
@SuppressWarnings("serial")
public class TimeZoneComboBox extends FormFieldComboBox<String> {
  
  
  private static final String[] zones;    // the timezones
  
  static {
    zones = TimeZone.getAvailableIDs();
    Arrays.sort(zones);
  }
  
  
  /**
   * Creates a TimeZoneComboBox initialized
   * with all available timezones.
   */
  public TimeZoneComboBox() {
    setMultiKeySelectionManager(true);
    for (int i=0; i < zones.length; i++)  {
      addItem(zones[i]);
    }
  }
  
  
  
  /**
   * Gets the selected TimeZone.
   *
   * @return the selected TimeZone, null if none
   */
  public TimeZone getTimeZone() {
    String id = getFormValueText();
    return id == null ? null : TimeZone.getTimeZone(id);
  }
  
  
  /**
   * Sets the selected TimeZone.
   *
   * @param timeZone the timezone, null if deselect
   */
  public void setTimeZone(TimeZone timeZone) {
    setFormValue(timeZone == null ? null : timeZone.toString());
  }

}
