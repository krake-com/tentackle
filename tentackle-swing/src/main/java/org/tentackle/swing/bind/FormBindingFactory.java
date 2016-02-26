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

import org.tentackle.bind.BindingFactory;
import org.tentackle.bind.BindingMember;
import org.tentackle.swing.BindableTableModel;
import org.tentackle.swing.FormComponent;
import org.tentackle.swing.FormContainer;

/**
 * The binding factory for forms.
 * <p>
 * Extends the {@link BindingFactory} by methods for form components.
 *
 * @author harald
 */
public interface FormBindingFactory extends BindingFactory {


  /**
   * Defines the binding for a component class.
   *
   * @param componentClass the component class
   * @param bindingClass the binding class
   * @return the old binding class if replaced, else null
   */
  Class<? extends FormComponentBinding> setFormComponentBindingClass(Class<? extends FormComponent> componentClass,
                                                                            Class<? extends FormComponentBinding> bindingClass);


  /**
   * Gets the binding class for a given component class.
   *
   * @param componentClass the component class
   * @return the binding class, null if no specific one, i.e. use a default binding
   */
  Class<? extends FormComponentBinding> getFormComponentBindingClass(final Class<? extends FormComponent> componentClass);



  /**
   * Defines the table binding for a model class.
   *
   * @param modelClass the model class
   * @param bindingClass the binding class
   * @return the old binding class if replaced, else null
   */
  Class<? extends FormTableBinding> setFormTableBindingClass(Class<?> modelClass,
                                                                    Class<? extends FormTableBinding> bindingClass);


  /**
   * Gets the table binding class for a given model class.
   *
   * @param modelClass the model class
   * @return the binding class, null if no specific one, i.e. use a default binding
   */
  Class<? extends FormTableBinding> getFormTableBindingClass(final Class<?> modelClass);




  /**
   * Creates a component binding.
   * <p>
   * Notice: this method must be implemented by the concrete factory.
   *
   * @param binder the binder managing the binding
   * @param component the GUI-component to bind
   * @param componentOptions options to configure the component.
   * @param parents the members building the declaration chain to this member, null if this binding's member is in container
   * @param member the member field to bind
   * @return the created binding
   */
  FormComponentBinding createFormComponentBinding(
                                FormComponentBinder binder, BindingMember[] parents, BindingMember member,
                                FormComponent component, String componentOptions);

  /**
   * Creates a component binder.
   * <p>
   * Notice: this method must be implemented by the concrete factory.
   *
   * @param container the container for the created binder
   * @return the binder
   */
  FormComponentBinder createFormComponentBinder(FormContainer container);


  /**
   * Creates a table binding.
   * <p>
   * Notice: this method must be implemented by the concrete factory.
   *
   * @param binder the binder managing the binding
   * @param mColumn the model column
   * @param columnOptions options to configure the column
   * @param parents the members building the declaration chain to this member, null if this binding's member is in container
   * @param member the member field to bind
   * @return the created binding
   */
  FormTableBinding createFormTableBinding(
                                FormTableBinder binder, BindingMember[] parents, BindingMember member,
                                int mColumn, String columnOptions);

  /**
   * Creates a table binder.
   * <p>
   * Notice: this method must be implemented by the concrete factory.
   *
   * @param model the table model
   * @return the binder
   */
  FormTableBinder createFormTableBinder(BindableTableModel<?> model);

}
