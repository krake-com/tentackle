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

package org.tentackle.swing.bind;

import org.tentackle.bind.BindingException;
import org.tentackle.bind.BindingMember;
import org.tentackle.common.Constants;
import org.tentackle.common.Timestamp;
import org.tentackle.swing.DateFormField;
import org.tentackle.swing.FormCalendarField;
import org.tentackle.swing.FormComponent;
import org.tentackle.swing.FormFieldComponent;
import org.tentackle.swing.SqlDateField;

/**
 * Binding for {@link DateFormField} and {@link FormCalendarField}.
 * <p>
 * The binding is necessary to detect whether the field is used for
 * {@link java.util.Date}, {@link org.tentackle.common.Date},
 * {@link org.tentackle.common.Timestamp} or {@link org.tentackle.common.Time}.
 *
 * @author harald
 */
public class SqlDateFieldBinding extends DefaultFormComponentBinding {

  private boolean utc;      // true if [UTC] option set for a Timestamp

  public SqlDateFieldBinding(FormComponentBinder binder, BindingMember[] parents, BindingMember member,
                             FormComponent component, String componentOptions) {
    super(binder, parents, member, component, componentOptions);
  }


  @Override
  protected boolean processOption(FormFieldComponent comp, String option) {
    if (Constants.BIND_UTC.equals(option)) {
      utc = true;
      return true;
    }
    return super.processOption(comp, option);
  }


  @Override
  public Object getViewValue() {

    /**
     * Notice: we still test for java.sql.xxxx types to allow apps
     * to use either java.sql.xx or org.tentackle.misc.xx.
     */
    try {
      Class<?> type = getMember().getType();
      if (java.sql.Timestamp.class.isAssignableFrom(type)) {
        Timestamp timestamp = ((SqlDateField) getComponent()).getTimestamp();
        if (timestamp != null && utc) {
          timestamp.setUTC(true);
        }
        return timestamp;
      }
      else if (java.sql.Time.class.isAssignableFrom(type)) {
        return ((SqlDateField) getComponent()).getTime();
      }
      else if (java.sql.Date.class.isAssignableFrom(type)) {
        return ((SqlDateField) getComponent()).getDate();
      }
      else {
        // else: java.util.Date
        return super.getViewValue();
      }
    }
    catch (Exception ex) {
      throw new BindingException("could not get component value for " + getMember(), ex);
    }
  }

}
