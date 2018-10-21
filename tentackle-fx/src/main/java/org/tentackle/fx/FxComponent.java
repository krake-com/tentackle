/*
 * Tentackle - https://tentackle.org.
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
 *
 */

package org.tentackle.fx;

import javafx.beans.property.BooleanProperty;

import org.tentackle.fx.bind.FxComponentBinding;
import org.tentackle.fx.table.FxTableCell;

import java.lang.reflect.Type;

/**
 * Interface all tentackle Fx components must implement.
 *
 * @author harald
 */
public interface FxComponent extends FxControl, ErrorPopupSupported, InfoPopupSupported {

  @Override
  FxComponentDelegate getDelegate();

  /**
   * Sets the type for this component.
   * <p>
   * Notice that the type can be applied only once.
   * This is because previously installed listeners could cause memleaks.
   *
   * @param type the type
   * @throws FxRuntimeException if component cannot handle that type
   */
  void setType(Class<?> type);

  /**
   * Gets the type this component returns via {@link #getViewValue()}.
   *
   * @return the type
   */
  Class<?> getType();

  /**
   * Sets the generic type.
   *
   * @param genericType the generic type
   */
  void setGenericType(Type genericType);

  /**
   * Gets the generic type.
   *
   * @return the generic type
   */
  Type getGenericType();

  /**
   * Sets a value translator.
   *
   * @param valueTranslator the translator
   */
  void setValueTranslator(ValueTranslator<?,?> valueTranslator);

  /**
   * Gets the value translator.
   *
   * @return the translator
   */
  ValueTranslator<?,?> getValueTranslator();

  /**
   * Sets a value in this component.<br>
   * The value's type is that of the model.
   *
   * @param value is the value to set
   */
  void setViewValue (Object value);

  /**
   * Retrieves the value from this component.<br>
   * The value's type is that of the model.
   *
   * @param <V> the value's type
   * @return the value shown by the component
   */
  <V> V getViewValue();

  /**
   * Retrieves the internal native object of the component.
   *
   * @return the component's object
   */
  Object getViewObject();

  /**
   * Sets the internal native object of the component.
   *
   * @param viewObject the internal object
   */
  void setViewObject(Object viewObject);

  /**
   * Retrieves the view value saved with {@link #saveView()}.<br>
   * The returned type is the internal native type of the component.
   *
   * @return the saved view value
   */
  Object getSavedViewObject();

  /**
   * Sets the mandatory option.<br>
   * Mandatory fields are differently visualized.
   * There is no further functional difference.
   *
   * @param mandatory true if mandatory
   */
  void setMandatory(boolean mandatory);

  /**
   * Gets the mandatory option.
   * @return true if mandatory
   */
  boolean isMandatory();

  /**
   * Gets the mandatory property.
   *
   * @return the property
   */
  BooleanProperty mandatoryProperty();



  // ------------------- binding -------------------------------------

  /**
   * Sets the path to bind this component to.
   * <p>
   * Usually the binding path will be determined from the components declared name.
   * However, it can be set programatically as well.
   *
   * @param bindingPath the binding path, for ex. <tt>"invoiceNumber"</tt>, null if autobinding
   * @see org.tentackle.bind.Binder
   */
  void setBindingPath(String bindingPath);

  /**
   * Gets the binding path.
   *
   * @return the field path, null if autobinding (default)
   */
  String getBindingPath();

  /**
   * Sets the component path.
   * <p>
   * The path is <tt>&lt;declaring-class-name&gt;.&lt;field-name&gt;</tt>.
   * Example <tt>"de.krake.invoicer.InvoicePanel.customerNumberField"</tt>.
   * <p>
   * Notice: the component path is set during binding.
   *
   * @param componentPath the component path
   */
  void setComponentPath(String componentPath);

  /**
   * Gets the component path.
   * 
   * @return the component path, null if not bound
   */
  String getComponentPath();


  /**
   * Sets the binding.
   *
   * @param binding the binding, null if none.
   */
  void setBinding(FxComponentBinding binding);

  /**
   * Gets the binding.
   *
   * @return the binding, null if none
   */
  FxComponentBinding getBinding();

  /**
   * Returns whether model was updated and updateview not invoked since then.<br>
   * Some components, such as FxTableView, never update the model because this
   * is already done by the ObservableList. On those cases, false will be returned.
   *
   * @return true if updateView could make sense
   */
  boolean isModelUpdated();

  /**
   * Sets the table cell if the component is used as a table cell editor.
   *
   * @param tableCell the cell, null to disconnect from table cell
   */
  void setTableCell(FxTableCell<?,?> tableCell);

  /**
   * Gets the table cell if the component is used as a table cell editor.
   *
   * @return the cell, null if not a cell editor
   */
  FxTableCell<?,?> getTableCell();

}
