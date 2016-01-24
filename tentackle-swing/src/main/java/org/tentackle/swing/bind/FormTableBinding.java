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


package org.tentackle.swing.bind;

import org.tentackle.bind.Binding;
import org.tentackle.swing.FormTableRowObject;


/**
 * Binding between a class member and a table model column.
 *
 * @author harald
 */
public interface FormTableBinding extends Binding {

  /**
   * {@inheritDoc}
   * <p>
   * Overridden to cast.
   */
  @Override
  FormTableBinder getBinder();

  /**
   * Gets the model column.
   *
   * @return the model column according to the table model
   */
  int getColumn();

  /**
   * Gets the row object.
   *
   * @return the row object for this binding
   */
  FormTableRowObject getBoundRootObject();

  /**
   * Sets the row object.
   *
   * @param rowObject the row object for this binding
   */
  void setBoundRootObject(FormTableRowObject rowObject);


  /**
   * Gets the binding option for UC/LC-conversion.
   *
   * @return some of {@link org.tentackle.swing.FormFieldComponent}.CONVERT_??, null if not defined
   */
  Character getConvert();

  /**
   * Gets the binding option for autoselect.
   *
   * @return TRUE or FALSE, null if not defined
   */
  Boolean isAutoSelect();

  /**
   * Gets the binding option for maxcolumns.
   *
   * @return maxcolumns, null if not defined
   */
  Integer getMaxColumns();

  /**
   * Gets the binding option for the scale.
   *
   * @return the scale, null if not defined
   */
  Integer getScale();

}
