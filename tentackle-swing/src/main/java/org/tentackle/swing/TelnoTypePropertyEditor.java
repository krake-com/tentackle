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

import java.beans.PropertyEditorSupport;


/**
 * Bean property editor for the phone type in {@link FormTelnoField}.
 *
 * @author harald
 */
public class TelnoTypePropertyEditor extends PropertyEditorSupport {

  private final String[] options = {
    "UNKNOWN", // '?'  could be anything
    "PHONE",   // 'P'  phone numbers only
    "FAX",     // 'F'  fax numbers only
    "CELL",    // 'C'  cell/mobile phone
    "MODEM"    // 'M'  modem
  };

  /** Creates new FormFieldConvertEditor */
  public TelnoTypePropertyEditor() {
  }

  @Override
  public String[] getTags() {
    return options;
  }

  @Override
  public String getAsText() {
    char convert = (Character) getValue();
    if (convert == FormTelnoField.MODEM) {
      return options[4];
    }
    else if (convert == FormTelnoField.CELL) {
      return options[3];
    }
    else if (convert == FormTelnoField.FAX) {
      return options[2];
    }
    else if (convert == FormTelnoField.PHONE) {
      return options[1];
    }
    else {
      return options[0];
    }
  }

  @Override
  public void setAsText(String s) {
    if      (options[1].equals(s)) {
      setValue(FormTelnoField.PHONE);
    }
    else if (options[2].equals(s)) {
      setValue(FormTelnoField.FAX);
    }
    else if (options[3].equals(s)) {
      setValue(FormTelnoField.CELL);
    }
    else if (options[4].equals(s)) {
      setValue(FormTelnoField.MODEM);
    }
    else {
      setValue(FormTelnoField.UNKNOWN);
    }
  }

  @Override
  public String getJavaInitializationString() {
    return FormTelnoField.class.getName() + "." + getAsText();
  }

}

