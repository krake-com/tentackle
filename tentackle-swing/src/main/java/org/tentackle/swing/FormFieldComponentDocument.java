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

import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.PlainDocument;
import org.tentackle.misc.Toolkit;


/**
 * Document Model for FormFieldComponents.
 *
 * @author harald
 */
@SuppressWarnings("serial")
public class FormFieldComponentDocument extends PlainDocument {


  private boolean eraseFirst;               // true if first insert() erases contents
  private final FormFieldComponent field;   // the form field component


  /**
   * Creates a document model.
   *
   * @param field the formfield component
   */
  public FormFieldComponentDocument (FormFieldComponent field)  {
    this.field = field;   // remember formtextfield for checks below
  }



  /**
   * {@inheritDoc}
   * <p>
   * Verifies the input according to the component's rules.
   */
  @Override
  public void insertString (int offs, String str, AttributeSet a)
          throws BadLocationException {


    if (eraseFirst) {
      if (field.isAutoSelect() == false ||   // if not enabled for whatever reason
          field.hasFocus() == false)  {       // or only if autoSelect wouldn't work
        super.remove(0, getLength());
        offs = 0;
        // this will cause selectAll to be inhibited if eraseFirst is called after
        // the first delivered KeyEvent in a JTable
        field.setInhibitAutoSelect(true);
      }
      eraseFirst = false;
    }

    if (field == null)  {
      // assertion if called with wrong constructor
      super.insertString(offs, str, a);
    }

    else  {

      // check constraints:
      boolean valid = true;                   // default is okay
      char filler   = field.getFiller();      // fill char

      if (filler != ' ') {
        // translate fillers
        str = str.replace (' ', filler);
      }

      // check valid chars
      if (field.getValidChars() != null)  {
        String vchars = field.getValidChars();
        for (int i=0; i < str.length(); i++) {
          // filler is always allowed!
          if (str.charAt(i) == filler) {
            continue;
          }
          // check that char is in valid string
          if (vchars.indexOf(str.charAt(i)) < 0) {
            valid = false;
            break;
          }
        }
      }

      // check invalid chars
      if (field.getInvalidChars() != null)  {
        String ichars = field.getInvalidChars();
        for (int i=0; i < str.length(); i++) {
          if (ichars.indexOf(str.charAt(i)) >= 0) {
            valid = false;
            break;
          }
        }
      }

      // do conversions
      switch (field.getConvert()) {

      case AbstractFormField.CONVERT_UC:
        // convert to uppercase
        str = str.toUpperCase();
        break;

      case AbstractFormField.CONVERT_LC:
        // convert to lowercase
        str = str.toLowerCase();
        break;
      }

      if (valid)  {
        if (field.isOverwrite() == true) {
          // override mode: remove first
          int olen = getLength();
          int rlen = str.length();
          if (olen - offs < rlen) {
            rlen = olen - offs;
          }
          if (rlen > 0) {
            super.remove(offs, rlen);
          }
        }
        // everything is okay, safe to insert
        super.insertString(offs, str, a);
      }

      else    {
        Toolkit.beep();  // beep
      }
    }
  }


  /**
   * package wide method (not for applications!) to trigger an
   * autoerase before the first change on the contents.
   * Used in FormTableCellEditor if autoSelect-Feature turned on
   * and the field is edited in semi-editing mode.
   *
   * @param erasefirst true if erase first (once)
   */
  void setEraseFirst(boolean erasefirst) {
    this.eraseFirst = erasefirst;
  }

}
