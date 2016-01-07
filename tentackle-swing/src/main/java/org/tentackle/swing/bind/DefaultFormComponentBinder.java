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

import java.awt.Container;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.TreeMap;
import org.tentackle.bind.AbstractBinder;
import org.tentackle.bind.BindableElement;
import org.tentackle.bind.Binding;
import org.tentackle.bind.BindingException;
import org.tentackle.bind.BindingMember;
import org.tentackle.bind.BindingVetoException;
import org.tentackle.log.Logger;
import org.tentackle.log.LoggerFactory;
import org.tentackle.misc.StringHelper;
import org.tentackle.reflect.ReflectionHelper;
import org.tentackle.swing.FormComponent;
import org.tentackle.swing.FormContainer;
import org.tentackle.swing.FormUtilities;
import org.tentackle.validate.ChangeableBindingEvaluator;
import org.tentackle.validate.MandatoryBindingEvaluator;
import org.tentackle.validate.ValidationContext;
import org.tentackle.validate.ValidationScopeFactory;
import org.tentackle.validate.Validator;


/**
 * Binding Workhorse.
 *
 * @author harald
 */
public class DefaultFormComponentBinder extends AbstractBinder implements FormComponentBinder {

  /**
   * logger for this class.
   */
  private static final Logger LOGGER = LoggerFactory.getLogger(DefaultFormComponentBinder.class);

  private final FormContainer form;                                       // the form to bind
  private final TreeMap<String,FormComponent> eligibleComponents;         // all eligible components in given form
  private final Map<FormComponent,FormComponentBinding> boundComponents;  // the bindings mapped by component
  private final Map<String,FormComponentBinding> boundPaths;              // the bindings mapped by binding path
  private final List<Binding> dynamicMandatoryBindings;                   // bindings to be monitored for the mandatory attribute
  private boolean needMandatoryUpdate;                                    // true if we need an update of the mandatory bindings
  private final List<Binding> dynamicChangeableBindings;                  // bindings to be monitored for the changeable attribute
  private boolean needChangeableUpdate;                                   // true if we need an update of the changeable bindings


  /**
   * Creates a binder for a form.
   *
   * @param form the form to bind
   */
  public DefaultFormComponentBinder(FormContainer form) {
    super();
    this.form = form;
    eligibleComponents = new TreeMap<>();
    boundComponents = new HashMap<>();
    boundPaths = new TreeMap<>();
    dynamicMandatoryBindings = new ArrayList<>();
    dynamicChangeableBindings = new ArrayList<>();
  }


  @Override
  public FormContainer getFormContainer() {
    return form;
  }

  @Override
  public void fireToView(Binding binding, Object parent, Object modelValue) throws BindingVetoException {

    super.fireToView(binding, parent, modelValue);

    if (needMandatoryUpdate) {
      for (Binding mandatoryBinding: dynamicMandatoryBindings) {
        boolean mandatory = false;
        for (Validator validator: mandatoryBinding.getValidators()) {
          if (validator instanceof MandatoryBindingEvaluator) {
            Object parentObject = mandatoryBinding.getParentObject();
            if (parentObject != null) { // if parent path is valid, i.e. no null reference
              ValidationContext validationContext = new ValidationContext();
              validationContext.setParentObject(parentObject);
              validationContext.setObject(mandatoryBinding.getModelValue());
              validationContext.setType(mandatoryBinding.getMember().getType());
              // we must check the scope, because it may change due to CompoundValues
              if (ValidationScopeFactory.getInstance().getMandatoryScope().appliesTo(validator.getConfiguredScopes(validationContext)) &&
                  // and the validator still applies:
                  validator.isConditionValid(validationContext)) {
                mandatory = true;
                break;
              }
            }
          }
        }
        mandatoryBinding.setMandatory(mandatory);
      }
      needMandatoryUpdate = false;
    }
    if (needChangeableUpdate) {
      for (Binding changeableBinding: dynamicChangeableBindings) {
        boolean changeable = false;
        for (Validator validator: changeableBinding.getValidators()) {
          if (validator instanceof ChangeableBindingEvaluator) {
            Object parentObject = changeableBinding.getParentObject();
            if (parentObject != null) { // if parent path is valid, i.e. no null reference
              ValidationContext validationContext = new ValidationContext();
              validationContext.setParentObject(parentObject);
              validationContext.setObject(changeableBinding.getModelValue());
              validationContext.setType(changeableBinding.getMember().getType());
              // we must check the scope, because it may change due to CompoundValues
              if (ValidationScopeFactory.getInstance().getChangeableScope().appliesTo(validator.getConfiguredScopes(validationContext)) &&
                  // and the validator still applies:
                  validator.isConditionValid(validationContext)) {
                changeable = true;
                break;
              }
            }
          }
        }
        changeableBinding.setChangeable(changeable);
      }
      needChangeableUpdate = false;
    }
  }


  @Override
  public void fireToModel(Binding binding, Object parent, Object viewValue) throws BindingVetoException {
    super.fireToModel(binding, parent, viewValue);
    requestMandatoryUpdate();
    requestChangeableUpdate();
  }



  @Override
  public void requestMandatoryUpdate() {
    needMandatoryUpdate = true;
  }

  @Override
  public List<? extends Binding> getMandatoryBindings() {
    return dynamicMandatoryBindings;
  }


  @Override
  public void requestChangeableUpdate() {
    needChangeableUpdate = true;
  }

  @Override
  public List<? extends Binding> getChangeableBindings() {
    return dynamicChangeableBindings;
  }


  @Override
  public int bindAllInherited() {
    return doBind(false, false);
  }


  @Override
  public int bindFormInherited() {
    return doBind(false, true);
  }


  @Override
  public int bindBindableInherited() {
    return doBind(true, false);
  }


  @Override
  public int bind() {
    return doBind(true, true);
  }


  @Override
  public void unbind() {
    for (FormComponent comp: boundComponents.keySet()) {
      comp.setBinding(null);
    }
    eligibleComponents.clear();
    boundComponents.clear();
    boundPaths.clear();
  }


  @Override
  public Collection<? extends Binding> getBindings() {
    return boundComponents.values();
  }


  @Override
  public Collection<FormComponent> getBoundComponents() {
    return boundComponents.keySet();
  }


  @Override
  public Collection<FormComponent> getUnboundComponents() {
    List<FormComponent> unboundComponents = new ArrayList<>();
    for (FormComponent component: eligibleComponents.values()) {
      if (component.getBinding() == null) {
        unboundComponents.add(component);
      }
    }
    return unboundComponents;
  }


  @Override
  public void assertAllBound() throws BindingException {
    Collection<FormComponent> unboundComponents = getUnboundComponents();
    if (!unboundComponents.isEmpty()) {
      for (FormComponent comp: unboundComponents) {
        LOGGER.severe("unbound component " + comp.getComponentPath());
      }
      throw new BindingException("unbound components in " + form.getClass().getName());
    }
  }


  @Override
  public void addBinding(Binding binding) {
    if (binding instanceof FormComponentBinding) {
      FormComponentBinding oldBinding = boundPaths.put(binding.getMember().getMemberPath(), (FormComponentBinding) binding);
      if (oldBinding != null) {
        throw new BindingException(binding + ": binding path '" + binding.getMember().getMemberPath() +
                                   "' already bound to " + oldBinding.getFormComponent().getComponentPath());
      }
      oldBinding = boundComponents.put(((FormComponentBinding) binding).getFormComponent(), (FormComponentBinding) binding);
      if (oldBinding != null) {
        throw new BindingException(binding + ": component '" + ((FormComponentBinding) binding).getFormComponent().getComponentPath() +
                                   "' already bound to " + oldBinding.getMember().getMemberPath());
      }
      ((FormComponentBinding) binding).getFormComponent().setBinding((FormComponentBinding) binding);

      // check if binding is dynamically mandatory according to the annotation
      List<Validator> validators = binding.getValidators();
      if (validators != null) {
        for (Validator validator: validators) {
          if (validator instanceof MandatoryBindingEvaluator &&
              ((MandatoryBindingEvaluator)validator).isMandatoryDynamic()) {
            addMandatoryBinding(binding);
            requestMandatoryUpdate();
          }
          if (validator instanceof ChangeableBindingEvaluator &&
              ((ChangeableBindingEvaluator)validator).isChangeableDynamic()) {
            addChangeableBinding(binding);
            requestChangeableUpdate();
          }
        }
      }
    }
  }


  @Override
  public FormComponentBinding getBinding(FormComponent component) {
    return boundComponents.get(component);
  }


  @Override
  public FormComponentBinding getBinding(String bindingPath) {
    return boundPaths.get(bindingPath);
  }


  @Override
  public FormComponentBinding removeBinding(FormComponent component) {
    FormComponentBinding binding = boundComponents.remove(component);
    if (binding != null) {
      if (boundPaths.remove(binding.getMember().getMemberPath()) != binding) {
        throw new BindingException("Binding " + binding + " missing in path map");
      }
      binding.getFormComponent().setBinding(null);
    }
    return binding;
  }


  @Override
  public FormComponentBinding removeBinding(String bindingPath) {
    FormComponentBinding binding = boundPaths.remove(bindingPath);
    if (binding != null) {
      if (boundComponents.remove(binding.getFormComponent()) != binding) {
        throw new BindingException("Binding " + binding + " missing in component map");
      }
      binding.getFormComponent().setBinding(null);
    }
    return binding;
  }



  /**
   * Adds a mandatory binding.
   *
   * @param mandatoryBinding the binding to add
   */
  protected void addMandatoryBinding(Binding mandatoryBinding) {
    dynamicMandatoryBindings.add(mandatoryBinding);
  }


  /**
   * Adds a changeable binding.
   *
   * @param changeableBinding the binding to add
   */
  protected void addChangeableBinding(Binding changeableBinding) {
    dynamicChangeableBindings.add(changeableBinding);
  }





  /**
   * Binds an object to a form.
   * <p>
   * The object will be scanned for members or submembers
   * annotated with the {@link Bindable}-annotation and their
   * corresponding setter- and getter methods.
   * <p>
   * More than one object can be bound to a form.
   * <p>
   * Notice that the object reference is <em>not</em> kept in the binding, i.e.
   * the binding works <em>only</em> by declaration (reflection).
   *
   * @param parents the fields building the declaration chain to this field, null if container
   * @param parentMemberPath the object path prefix, null if root
   * @param parentClass the object's class
   * @param declaredOnly true if search for methods and field which are declared only,
   *        false to include inherited as well
   * @return number of members bound
   */
  @SuppressWarnings("unchecked")
  @Override
  protected int doBind(BindingMember[] parents, String parentMemberPath, Class<?> parentClass, boolean declaredOnly) {

    /**
     * To detect recursion loops:
     * we don't need to recursively walk down the binding path more
     * than the max binding path length of all components in the container.
     */
    if (parentMemberPath != null) {
      boolean found = false;
      for (String bindingPath: toCamelCase(parentMemberPath)) {
        // check if the binding path is leading part of any component
        found = eligibleComponents.containsKey(bindingPath);
        if (!found) {
          String key = eligibleComponents.higherKey(bindingPath);
          if (key != null && key.startsWith(bindingPath)) {
            found = true;
          }
        }
        if (found) {
          break;
        }
      }
      if (!found) {
        return 0;   // no more matches possible
      }
    }


    int count = 0;

    // all fields and/or setter/getters annotated with the @Bindable annotation
    for (BindableElement element: FormUtilities.getInstance().getBindingFactory().getBindableCache().getBindableMap(parentClass, declaredOnly).values()) {

      String fieldMemberName = StringHelper.firstToLower(element.getCamelName());
      String fieldMemberPath = (parentMemberPath == null ? "" : (parentMemberPath + ".")) + fieldMemberName;

      BindingMember[] fieldParents = new BindingMember[parents == null ? 1 : (parents.length + 1)];
      BindingMember   fieldMember  = FormUtilities.getInstance().getBindingFactory().createBindingMember(
              parentClass, (parents == null ? null : parents[parents.length-1]),
              fieldMemberName, fieldMemberPath, element);

      if (parents != null) {
        System.arraycopy(parents, 0, fieldParents, 0, parents.length);
        fieldParents[parents.length] = fieldMember;
      }
      else  {
        fieldParents[0] = fieldMember;
      }

      try {

        if (element.getField() != null && !element.getField().isAccessible()) {
          element.getField().setAccessible(true);  // make this directly accessible
        }

        // try to bind this member
        FormComponent component = findComponent(fieldMemberPath);
        if (component != null) {
          // found
          Binding binding = FormUtilities.getInstance().getBindingFactory().createFormComponentBinding(this, parents, fieldMember,
                                                                      component, element.getBindingOptions());
          addBinding(binding);
          count++;
        }

        // recursively try to bind sub-members
        count += doBind(fieldParents, fieldMemberPath, fieldMember.getType(), declaredOnly);
      }
      catch (Exception ex) {
        throw new BindingException("binding " + fieldMemberPath + " failed", ex);
      }
    }

    return count;
  }






  /**
   * Converts a bindingpath (in dot-notation) into camelcase paths
   * to be matched against eligibleComponents.
   *
   * @param bindingPath the bindingpath in dot-notation
   * @return an array of strings in camelcase
   */
  private String[] toCamelCase(String bindingPath) {
    // translate to camel case
    StringTokenizer stok = new StringTokenizer(bindingPath, ".");
    StringBuilder buf = new StringBuilder();
    StringBuilder omitBuf = new StringBuilder();
    int omitCount = 0;
    String lastCamelName = null;
    while (stok.hasMoreTokens()) {
      String token = stok.nextToken();
      if (buf.length() == 0) {
        buf.append(token);
        if (omitCount >= 0) {
          omitBuf.append(token);
          omitCount++;
        }
      }
      else  {
        token = StringHelper.firstToUpper(token);
        buf.append(token);
        if (omitCount >= 0) {
          if (lastCamelName == null) {
            omitBuf.append(token);
            omitCount++;
          }
          else if (token.toLowerCase().startsWith(lastCamelName)) {
            omitBuf.append(token.substring(lastCamelName.length()));
            omitCount++;
          }
          else {
            // first non-repeating token found: stop omit detection
            omitCount = -1;
          }
        }
      }
      lastCamelName = token.toLowerCase();
    }

    if (omitCount > 1 && omitCount < 4) {
      // don't allow more than 4 omits to detect cases like booking.booking.booking.booking...
      return new String[] { buf.toString(), omitBuf.toString() };
    }
    else  {
      return new String[] { buf.toString() };
    }
  }



  /**
   * Finds the component according to the field's object path.
   * <p>
   * The method scans for components that are named to match the object's path.<br>
   *
   * @param bindingPath the object's binding path
   * @return the component, null if not found
   */
  private FormComponent findComponent(String bindingPath) {

    FormComponent comp = null;

    for (String path: toCamelCase(bindingPath)) {

      LOGGER.finer("checking {0} for matching component", path);

      comp = eligibleComponents.get(path);
      if (comp != null) {
        break;    // match found
      }
    }

    return comp;
  }


  /**
   * Gets the the object (FormField or FormContainer) associated with the declared field
   * in a container.
   *
   * @param container the container declaring the field
   * @param formField the form field declared in the container
   * @return the form component or container, may be null!
   */
  private Object getFormObject(Container container, Field formField) {
    try {
      if (!formField.isAccessible()) {
        formField.setAccessible(true);
      }
      return formField.get(container);
    }
    catch (Exception ex) {
      throw new BindingException("cannot access " + formField + " in container " +
                                 container.getClass().getName(), ex);
    }
  }


  /**
   * Adds an eligible component.
   *
   * @param container the container of the component
   * @param containerPath the path to the component
   * @param checkedContainers set of all containers
   * @param declaredOnly true if only components declared in container, else also inherited
   */
  private void addEligibleComponents(Container container, String containerPath,
                                     Set<Container> checkedContainers, boolean declaredOnly) {

    if (!(container instanceof FormContainer) || ((FormContainer)container).isBindable()) {
      if (checkedContainers.add(container)) {
        LOGGER.finer("checking {0} for eligible components", containerPath);
        // find all members that are FormComponents
        for (Field field:
             (declaredOnly ?
               container.getClass().getDeclaredFields()
                :
               ReflectionHelper.getAllFields(container.getClass(),
                                             new Class<?>[] { FormComponent.class, FormContainer.class },
                                             false, null, true))) {

          if (FormComponent.class.isAssignableFrom(field.getType())) {
            FormComponent component = (FormComponent) getFormObject(container, field);
            if (component != null && component.isBindable()) {
              // only initialized members (usually generated by the GUI builder)
              String componentPath = containerPath + "." + field.getName();
              component.setComponentPath(componentPath);
              String bindingPath = component.getBindingPath();
              if (bindingPath == null) {
                // not set: generate binding path
                bindingPath = field.getName();
                // cut some common trailing text which does not belong to the binding path
                bindingPath = StringHelper.removeTrailingText(bindingPath, "Field");
                bindingPath = StringHelper.removeTrailingText(bindingPath, "Bean");
                bindingPath = StringHelper.removeTrailingText(bindingPath, "Comp");
                bindingPath = StringHelper.removeTrailingText(bindingPath, "Component");
              }
              // else: binding path already set by the application

              // add to component map
              FormComponent oldComponent = eligibleComponents.put(bindingPath, component);
              if (oldComponent != null) {
                throw new BindingException("binding path '" + bindingPath +
                                           "' already provided by " + oldComponent.getComponentPath());
              }
              LOGGER.finer("added {0}:{1} to eligible components", componentPath, bindingPath);
            }
          }
          else if (Container.class.isAssignableFrom(field.getType())) {
            // recursively walk down the container hierarchy
            Container subContainer = (Container) getFormObject(container, field);
            if (subContainer != null) {
              addEligibleComponents(subContainer,
                                    containerPath + "." + field.getName(),
                                    checkedContainers, declaredOnly);
            }
          }
        }
      }
    }
  }



  /**
   * Binds all objects to the form.
   *
   * @param declaredBindablesOnly true if search for methods and field which are declared only,
   *        false to include inherited as well
   * @param declaredComponentsOnly true if search for components which are declared only,
   *        false to include inherited as well
   * @return the number of created bindings
   */
  private int doBind(boolean declaredBindablesOnly, boolean declaredComponentsOnly) {
    addEligibleComponents((Container)form, form.getClass().getName(), new HashSet<>(), declaredComponentsOnly);
    if (LOGGER.isFineLoggable()) {
      StringBuilder buf = new StringBuilder();
      buf.append("--------- eligible components: ----------\n");
      for (Map.Entry<String,FormComponent> entry: eligibleComponents.entrySet()) {
        buf.append(entry.getKey());
        buf.append(" : ");
        buf.append(entry.getValue().getComponentPath());
        buf.append("\n");
      }
      buf.append("-----------------------------------------\n");
      LOGGER.fine(buf.toString());
    }
    return doBind(null, null, form.getClass(), declaredBindablesOnly);
  }

}
