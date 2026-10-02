/*
 * Tentackle - https://tentackle.org
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


package org.tentackle.fx.bind;

import org.tentackle.bind.AbstractBindingFactory;
import org.tentackle.bind.BindingException;
import org.tentackle.bind.BindingMember;
import org.tentackle.common.Service;
import org.tentackle.fx.FxComponent;
import org.tentackle.fx.FxController;
import org.tentackle.fx.table.TableColumnConfiguration;
import org.tentackle.fx.table.TableConfiguration;
import org.tentackle.log.Logger;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;


/**
 * Default implementation of the FX binding factory.
 *
 * @author harald
 */
@Service(FxBindingFactory.class)
public class DefaultFxBindingFactory extends AbstractBindingFactory implements FxBindingFactory {

  private static final Logger LOGGER = Logger.get(DefaultFxBindingFactory.class);


  /**
   * Map of component classes to binding classes.
   */
  private final Map<Class<? extends FxComponent>, Class<? extends FxComponentBinding>> componentClassMap = new ConcurrentHashMap<>();

  /**
   * Same as {@link #componentClassMap}, but with evaluated inheritance.
   */
  private final Map<Class<? extends FxComponent>, Class<? extends FxComponentBinding>> allComponentClassMap = new ConcurrentHashMap<>();


  /**
   * Creates the FX binding factory.
   */
  public DefaultFxBindingFactory() {
    super();
  }


  @Override
  public Class<? extends FxComponentBinding> setComponentBindingClass(Class<? extends FxComponent> componentClass,
                                                                      Class<? extends FxComponentBinding> bindingClass) {
    return componentClassMap.put(componentClass, bindingClass);
  }


  @SuppressWarnings("unchecked")
  @Override
  public Class<? extends FxComponentBinding> getComponentBindingClass(final Class<? extends FxComponent> componentClass) {
    Class<? extends FxComponentBinding> bindingClass = allComponentClassMap.get(componentClass);
    if (bindingClass == null) {
      // not already evaluated
      Class<? extends FxComponent> clazz = componentClass;
      while (FxComponent.class.isAssignableFrom(clazz) &&
             (bindingClass = componentClassMap.get(clazz)) == null) {
        // no mapping found, try superclass of component
        clazz = (Class<? extends FxComponent>) clazz.getSuperclass();
      }
      // a value of FxComponentBinding.class means: no mapping
      allComponentClassMap.put(componentClass, bindingClass == null ? FxComponentBinding.class : bindingClass);
    }
    else if (bindingClass == FxComponentBinding.class) {
      bindingClass = null;    // no mapping
    }
    return bindingClass;
  }


  @Override
  public FxComponentBinding createComponentBinding(FxComponentBinder binder, BindingMember[] parents, BindingMember member,
                                                   FxComponent component, String componentOptions) {

    FxComponentBinding binding;
    Class<? extends FxComponentBinding> bindingClass = getComponentBindingClass(component.getClass());
    if (bindingClass != null) {
      try {
        Constructor<? extends FxComponentBinding> con = bindingClass.getConstructor(
            FxComponentBinder.class, BindingMember[].class, BindingMember.class, FxComponent.class, String.class);
        binding = con.newInstance(binder, parents, member, component, componentOptions);
      }
      catch (IllegalAccessException | IllegalArgumentException | InstantiationException | NoSuchMethodException |
             InvocationTargetException ex) {
        throw new BindingException("could not instantiate binding " + bindingClass.getName(), ex);
      }
    }
    else {
      // if all else fails: create a default binding
      binding = new DefaultFxComponentBinding(binder, parents, member, component, componentOptions);
    }

    LOGGER.fine("created component binding {0}", binding);

    return binding;
  }


  @Override
  public FxComponentBinder createComponentBinder(FxController controller) {
    return new DefaultFxComponentBinder(controller);
  }


  @Override
  public <S,T> FxTableBinding<S,T> createTableBinding(FxTableBinder<S> binder,
                                                      BindingMember[] parents,
                                                      BindingMember member,
                                                      TableColumnConfiguration<S,T> columnConfig,
                                                      String columnOptions) {

    DefaultFxTableBinding<S,T> binding = new DefaultFxTableBinding<>(binder, parents, member, columnConfig, columnOptions);
    columnConfig.setBinding(binding);
    return binding;
  }


  @Override
  public <S> FxTableBinder<S> createTableBinder(TableConfiguration<S> tableConfiguration) {
    return new DefaultFxTableBinder<>(tableConfiguration);
  }

}
