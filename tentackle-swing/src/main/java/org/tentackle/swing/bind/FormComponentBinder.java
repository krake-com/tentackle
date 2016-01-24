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

import java.util.Collection;
import java.util.List;
import org.tentackle.bind.Binder;
import org.tentackle.bind.Binding;
import org.tentackle.swing.FormComponent;
import org.tentackle.swing.FormContainer;



/**
 * The Binder that associates form components with object binding paths.
 *
 * @author harald
 */
public interface FormComponentBinder extends Binder {

  /**
   * Gets the container bound by this binder.
   *
   * @return the form
   */
  public FormContainer getFormContainer();


  /**
   * Binds with form inheritance.
   * <p>
   * The form will be scanned for members or submembers
   * annotated with the {@link org.tentackle.bind.Bindable}-annotation.
   * Inherited members of the form's parent classes
   * will be included as well.
   * <p>
   * Notice that the object references are <em>not</em> kept in the binding.
   * <p>
   * CAUTION: expensive operation!
   *
   * @return number of members bound
   */
  public int bindFormInherited();

  /**
   * Binds with bindable inheritance.
   * <p>
   * The form will be scanned for members or submembers
   * annotated with the {@link org.tentackle.bind.Bindable}-annotation.
   * Inherited members of all bindables will be included as well.
   * <p>
   * Notice that the object references are <em>not</em> kept in the binding.
   * <p>
   * CAUTION: expensive operation!
   *
   * @return number of members bound
   */
  public int bindBindableInherited();

  /**
   * Gets a binding by component.
   *
   * @param component the component assigned to the binding
   * @return the binding, null if component not bound
   */
  public FormComponentBinding getBinding(FormComponent component);

  /**
   * {@inheritDoc}
   * <p>
   * Overridden to cast.
   */
  @Override
  public FormComponentBinding getBinding(String bindingPath);


  /**
   * Gets the list of all bound components of this form.
   *
   * @return the list of bound components, never null
   */
  public Collection<FormComponent> getBoundComponents();

  /**
   * Gets the list of all unbound components of this form.
   * @return the list of unbound components, never null
   */
  public Collection<FormComponent> getUnboundComponents();


  /**
   * Programmatically removes a binding from this form.
   *
   * @param component the component assigned to the binding
   *
   * @return the removed binding, null if no such binding found
   */
  public FormComponentBinding removeBinding(FormComponent component);

  /**
   * {@inheritDoc}
   * <p>
   * Overridden to cast.
   */
  @Override
  public FormComponentBinding removeBinding(String bindingPath);


  /**
   * Gets the dynamically mandatory bindings.
   *
   * @return the mandatory bindings
   */
  public List<? extends Binding> getMandatoryBindings();

  /**
   * Request to update the mandatory attribute of all dynamically mandatory components.<br>
   * Used by {@link FormContainer#setFormValues()} and {@link FormContainer#setFormValuesKeepChanged()}.
   */
  public void requestMandatoryUpdate();


  /**
   * Gets the dynamically changeable bindings.
   *
   * @return the changaeble bindings
   */
  public List<? extends Binding> getChangeableBindings();

  /**
   * Request to update the changeable attribute of all dynamically mandatory components.<br>
   * Used by {@link FormContainer#setFormValues()} and {@link FormContainer#setFormValuesKeepChanged()}.
   */
  public void requestChangeableUpdate();

}
