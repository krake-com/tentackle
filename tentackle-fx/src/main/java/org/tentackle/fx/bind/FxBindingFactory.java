/*
 * Tentackle - http://www.tentackle.org.
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

import org.tentackle.bind.BindingFactory;
import org.tentackle.bind.BindingMember;
import org.tentackle.common.ServiceFactory;
import org.tentackle.fx.FxComponent;
import org.tentackle.fx.FxController;
import org.tentackle.fx.table.TableColumnConfiguration;
import org.tentackle.fx.table.TableConfiguration;

interface FxBindingFactory$Singleton {
  static FxBindingFactory INSTANCE = ServiceFactory.createService(FxBindingFactory.class);
}

/**
 * The fx binding factory.
 *
 * @author harald
 */
public interface FxBindingFactory extends BindingFactory {

  /**
   * The singleton.
   *
   * @return the singleton
   */
  static FxBindingFactory getInstance() {
    return FxBindingFactory$Singleton.INSTANCE;
  }



  /**
   * Defines the binding for a component class.
   *
   * @param componentClass the component class
   * @param bindingClass the binding class
   * @return the old binding class if replaced, else null
   */
  Class<? extends FxComponentBinding> setComponentBindingClass(Class<? extends FxComponent> componentClass,
                                                               Class<? extends FxComponentBinding> bindingClass);


  /**
   * Gets the binding class for a given component class.
   *
   * @param componentClass the component class
   * @return the binding class, null if no specific one, i.e. use a default binding
   */
  Class<? extends FxComponentBinding> getComponentBindingClass(final Class<? extends FxComponent> componentClass);




  /**
   * Creates a component binding.
   * <p>
   * Notice: this method must be implemented by the concrete factory.
   *
   * @param binder the binder managing the binding
   * @param component the GUI-component to bind
   * @param componentOptions options to configure the component.
   * @param parents the members building the declaration chain to this member, null if this binding's member is in controller
   * @param member the member field to bind
   * @return the created binding
   */
  FxComponentBinding createComponentBinding(FxComponentBinder binder, BindingMember[] parents, BindingMember member,
                                            FxComponent component, String componentOptions);

  /**
   * Creates a component binder.
   * <p>
   * Notice: this method must be implemented by the concrete factory.
   *
   * @param controller the controller for the created binder
   * @return the binder
   */
  FxComponentBinder createComponentBinder(FxController controller);


  /**
   * Creates a table binding.
   * <p>
   * Notice: this method must be implemented by the concrete factory.
   *
   * @param <S> type of the objects contained within the table's items list
   * @param <T> the cell value's type
   * @param binder the binder managing the binding
   * @param columnOptions options to configure the column
   * @param parents the members building the declaration chain to this member, null if this binding's member is in controller
   * @param columnConfig the table column configuration
   * @param member the member field to bind
   * @return the created binding
   */
  <S,T> FxTableBinding<S,T> createTableBinding(FxTableBinder<S> binder, BindingMember[] parents, BindingMember member,
                                               TableColumnConfiguration<S,T> columnConfig, String columnOptions);

  /**
   * Creates a table binder.
   * <p>
   * Notice: this method must be implemented by the concrete factory.
   *
   * @param <S> type of the objects contained within the table's items list
   * @param tableConfiguration the table configuration
   * @return the binder
   */
  <S> FxTableBinder<S> createTableBinder(TableConfiguration<S> tableConfiguration);


}
