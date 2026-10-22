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

package org.tentackle.fx.bind;

import org.tentackle.bind.Binder;
import org.tentackle.bind.Binding;
import org.tentackle.fx.FxComponent;
import org.tentackle.fx.FxController;
import org.tentackle.fx.ValueTranslator;

import java.util.Collection;
import java.util.List;

/**
 * The binder that associates the components of a controller with object binding paths.
 *
 * @author harald
 */
public interface FxComponentBinder extends Binder {

  /**
   * Gets the controller bound by this binder.
   *
   * @return the controller
   */
  FxController getController();

  /**
   * Binds including inherited bindables.
   * <p>
   * The controller will be scanned for members or sub members
   * annotated with the {@link org.tentackle.bind.Bindable}-annotation.
   * Inherited members of the controller's parent classes
   * will be included as well.
   * <p>
   * CAUTION: expensive operation!
   *
   * @return number of members bound
   */
  int bindWithInheritedBindables();

  /**
   * Binds including inherited components.
   * <p>
   * The controller will be scanned for members or sub members
   * annotated with the {@link org.tentackle.bind.Bindable}-annotation.
   * Inherited components declared in parent controllers are included.
   * <p>
   * CAUTION: expensive operation!
   *
   * @return number of members bound
   */
  int bindWithInheritedComponents();

  /**
   * Gets a binding by component.
   *
   * @param component the component assigned to the binding
   * @return the binding, null if component not bound
   */
  FxComponentBinding getBinding(FxComponent component);

  /**
   * {@inheritDoc}
   * <p>
   * Overridden to cast.
   */
  @Override
  FxComponentBinding getBinding(String bindingPath);

  /**
   * Gets the list of all bound components of the controller.
   *
   * @return the list of bound components, never null
   */
  Collection<FxComponent> getBoundComponents();

  /**
   * Gets the list of all unbound components of the controller.
   *
   * @return the list of unbound components, never null
   */
  Collection<FxComponent> getUnboundComponents();

  /**
   * Programmatically removes a binding from the controller.
   *
   * @param component the component assigned to the binding
   * @return the removed binding, null if no such binding found
   */
  FxComponentBinding removeBinding(FxComponent component);

  /**
   * {@inheritDoc}
   * <p>
   * Overridden to cast.
   */
  @Override
  FxComponentBinding removeBinding(String bindingPath);

  /**
   * Gets the dynamically mandatory bindings.
   *
   * @return the mandatory bindings
   */
  List<? extends Binding> getMandatoryBindings();

  /**
   * Requests to update the mandatory attribute of all dynamically mandatory components.<br>
   * The update is performed on the next transfer to the view.
   */
  void requestMandatoryUpdate();

  /**
   * Gets the dynamically changeable bindings.
   *
   * @return the changeable bindings
   */
  List<? extends Binding> getChangeableBindings();

  /**
   * Requests to update the changeable attribute of all dynamically changeable components.<br>
   * The update is performed on the next transfer to the view.
   */
  void requestChangeableUpdate();

  /**
   * Invokes {@link ValueTranslator#bindingPropertiesUpdated()} on the translators
   * for all bound components.
   */
  void bindingPropertiesUpdated();

}
