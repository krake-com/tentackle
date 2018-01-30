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

// Created on May 15, 2003, 4:06 PM


package org.tentackle.swing;

import java.beans.PropertyEditorSupport;


/**
 * Property editor for FormCalendar time modes.
 *
 * @author harald
 */
public class CalendarTimeModePropertyEditor extends PropertyEditorSupport {

  private static final String[] options = {
    "AUTO",     // -1 = from format (see FormCalendarField)
    "DATE",     //  0 = show only Date
    "HOUR",     //  1 = show date and hour
    "MINUTE",   //  2 = show date, hour and minutes
    "SECOND"    //  3 = show date, hour, minutes and seconds
  };


  /**
   * Creates a time mode editor
   */
  public CalendarTimeModePropertyEditor() {
  }

  @Override
  public String[] getTags() {
    return options;
  }

  @Override
  public String getAsText() {
    int time = ((Integer) getValue()) + 1;
    if (time >= 0 && time < options.length) {
      return options[time];
    }
    else {
      return options[0];
    }
  }

  @Override
  public void setAsText(String s) {
    for (int time=0; time < options.length; time++) {
      if (options[time].compareTo(s) == 0) {
        setValue(time - 1);
        return;
      }
    }
    setValue(-1);
  }

  @Override
  public String getJavaInitializationString() {
    if (((Integer) getValue()) < 0) {
      return FormCalendarField.class.getName() + ".SHOW_AUTO";
    }
    return FormCalendar.class.getName() + ".SHOW_" +  getAsText();
  }

}
