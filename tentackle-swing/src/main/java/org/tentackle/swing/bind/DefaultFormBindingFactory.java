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

import java.lang.reflect.Constructor;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.tentackle.bind.AbstractBindingFactory;
import org.tentackle.bind.BindingException;
import org.tentackle.bind.BindingMember;
import org.tentackle.common.Service;
import org.tentackle.log.Logger;
import org.tentackle.log.LoggerFactory;
import org.tentackle.swing.BindableTableModel;
import org.tentackle.swing.FormComponent;
import org.tentackle.swing.FormContainer;


/**
 * Default implementation of a binding factory.
 *
 * @author harald
 */
@Service(FormBindingFactory.class)
public class DefaultFormBindingFactory extends AbstractBindingFactory implements FormBindingFactory {


  /**
   * logger for this class.
   */
  private static final Logger LOGGER = LoggerFactory.getLogger(DefaultFormBindingFactory.class);


  /**
   * Map of component classes to binding classes
   */
  private final Map<Class<? extends FormComponent>, Class<? extends FormComponentBinding>> componentClassMap = new ConcurrentHashMap<>();

  // same but with evaluated inheritance
  private final Map<Class<? extends FormComponent>, Class<? extends FormComponentBinding>> allComponentClassMap = new ConcurrentHashMap<>();

  /**
   * Map of component classes to binding classes
   */
  private final Map<Class<?>, Class<? extends FormTableBinding>> tableClassMap = new ConcurrentHashMap<>();

  // same but with evaluated inheritance
  private final Map<Class<?>, Class<? extends FormTableBinding>> allTableClassMap = new ConcurrentHashMap<>();



  /**
   * Creates a form binding factory.
   */
  public DefaultFormBindingFactory() {
    super();
  }


  @Override
  public Class<? extends FormComponentBinding> setFormComponentBindingClass(Class<? extends FormComponent> componentClass,
                                                                            Class<? extends FormComponentBinding> bindingClass) {
    return componentClassMap.put(componentClass, bindingClass);
  }


  @SuppressWarnings("unchecked")
  @Override
  public Class<? extends FormComponentBinding> getFormComponentBindingClass(final Class<? extends FormComponent> componentClass) {
    Class<? extends FormComponentBinding> bindingClass = allComponentClassMap.get(componentClass);
    if (bindingClass == null) {
      // not already evaluated
      Class<? extends FormComponent> clazz = componentClass;
      while (FormComponent.class.isAssignableFrom(clazz) &&
             (bindingClass = componentClassMap.get(clazz)) == null) {
        // no mapping found, try superclass of component
        clazz = (Class<? extends FormComponent>) clazz.getSuperclass();
      }
      // a value of FormComponentBinding.class means: no mapping
      allComponentClassMap.put(componentClass, bindingClass == null ? FormComponentBinding.class : bindingClass);
    }
    else if (bindingClass == FormComponentBinding.class) {
      bindingClass = null;    // no mapping
    }
    return bindingClass;
  }



  @Override
  public Class<? extends FormTableBinding> setFormTableBindingClass(Class<?> modelClass,
                                                                    Class<? extends FormTableBinding> bindingClass) {
    return tableClassMap.put(modelClass, bindingClass);
  }


  @SuppressWarnings("unchecked")
  @Override
  public Class<? extends FormTableBinding> getFormTableBindingClass(final Class<?> modelClass) {
    Class<? extends FormTableBinding> bindingClass = allTableClassMap.get(modelClass);
    if (bindingClass == null) {
      // not already evaluated
      Class<?> clazz = modelClass;
      while (clazz != null && (bindingClass = tableClassMap.get(clazz)) == null) {
        // no mapping found, try superclass of component
        clazz = clazz.getSuperclass();
      }
      // a value of FormTableBinding.class means: no mapping
      allTableClassMap.put(modelClass, bindingClass == null ? FormTableBinding.class : bindingClass);
    }
    else if (bindingClass == FormTableBinding.class) {
      bindingClass = null;    // no mapping
    }
    return bindingClass;
  }


  @Override
  public FormComponentBinding createFormComponentBinding(
                                  FormComponentBinder binder, BindingMember[] parents, BindingMember member,
                                  FormComponent component, String componentOptions) {

    FormComponentBinding binding;
    Class<? extends FormComponentBinding> bindingClass = getFormComponentBindingClass(component.getClass());
    if (bindingClass != null) {
      try {
        Constructor<? extends FormComponentBinding> con = bindingClass.getConstructor(
            FormComponentBinder.class, BindingMember[].class, BindingMember.class, FormComponent.class, String.class);
        binding = con.newInstance(binder, parents, member, component, componentOptions);
      }
      catch (Exception ex) {
        throw new BindingException("could not instantiate binding " + bindingClass.getName(), ex);
      }
    }
    else {
      // if all else fails: createBinding a default binding
      binding = new DefaultFormComponentBinding(binder, parents, member, component, componentOptions);
    }

    LOGGER.fine("created component binding {0}", binding);

    return binding;
  }


  @Override
  public FormComponentBinder createFormComponentBinder(FormContainer container) {
    return new DefaultFormComponentBinder(container);
  }


  @Override
  public FormTableBinding createFormTableBinding(FormTableBinder binder, BindingMember[] parents, BindingMember member,
                                                        int mColumn, String columnOptions) {
    FormTableBinding binding;
    Class<? extends FormTableBinding> bindingClass = getFormTableBindingClass(member.getType());
    if (bindingClass != null) {
      try {
        Constructor<? extends FormTableBinding> con = bindingClass.getConstructor(
            FormTableBinder.class, BindingMember[].class, BindingMember.class, Integer.TYPE, String.class);
        binding = con.newInstance(binder, parents, member, mColumn, columnOptions);
      }
      catch (Exception ex) {
        throw new BindingException("could not instantiate binding " + bindingClass.getName(), ex);
      }
    }
    else {
      // if all else fails: createBinding a default binding
      binding = new DefaultFormTableBinding(binder, parents, member, mColumn, columnOptions);
    }

    LOGGER.fine("created table binding {0}", binding);

    return binding;
  }


  @Override
  @SuppressWarnings("unchecked")
  public FormTableBinder createFormTableBinder(BindableTableModel model) {
    return new DefaultFormTableBinder(model);
  }

}
