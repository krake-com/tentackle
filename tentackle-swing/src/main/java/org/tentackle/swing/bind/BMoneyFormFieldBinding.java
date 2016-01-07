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
import org.tentackle.common.DMoney;
import org.tentackle.swing.BMoneyFormField;
import org.tentackle.swing.FormComponent;

/**
 * Binding for {@link BMoneyFormField}.
 * <p>
 * The binding is necessary to detect whether the field is used for
 * {@link org.tentackle.misc.BMoney} or {@link org.tentackle.misc.DMoney} types.
 *
 * @author harald
 */
public class BMoneyFormFieldBinding extends DefaultFormComponentBinding {

  public BMoneyFormFieldBinding(FormComponentBinder binder, BindingMember[] parents, BindingMember member,
                                FormComponent component, String componentOptions) {
    super(binder, parents, member, component, componentOptions);
  }

  @Override
  public Object getViewValue() {
    try {
      if (DMoney.class.isAssignableFrom(getMember().getType())) {
        return ((BMoneyFormField)getFormComponent()).getDMoney();
      }
      else {
        return super.getViewValue();
      }
    }
    catch (Exception ex) {
      throw new BindingException("could not get component value for " + getMember(), ex);
    }
  }

}
