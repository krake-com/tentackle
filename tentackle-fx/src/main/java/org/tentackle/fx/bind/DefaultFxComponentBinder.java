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


package org.tentackle.fx.bind;

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
import javafx.scene.Parent;
import org.tentackle.bind.AbstractBinder;
import org.tentackle.bind.BindableElement;
import org.tentackle.bind.Binding;
import org.tentackle.bind.BindingException;
import org.tentackle.bind.BindingMember;
import org.tentackle.bind.BindingVetoException;
import org.tentackle.fx.FxComponent;
import org.tentackle.fx.FxContainer;
import org.tentackle.fx.FxController;
import org.tentackle.log.Logger;
import org.tentackle.log.LoggerFactory;
import org.tentackle.common.StringHelper;
import org.tentackle.reflect.ReflectionHelper;
import org.tentackle.validate.ChangeableBindingEvaluator;
import org.tentackle.validate.MandatoryBindingEvaluator;
import org.tentackle.validate.ValidationContext;
import org.tentackle.validate.ValidationScope;
import org.tentackle.validate.ValidationScopeFactory;
import org.tentackle.validate.Validator;


/**
 * Binding Workhorse.
 *
 * @author harald
 */
public class DefaultFxComponentBinder extends AbstractBinder implements FxComponentBinder {

  /**
   * logger for this class.
   */
  private static final Logger LOGGER = LoggerFactory.getLogger(DefaultFxComponentBinder.class);

  private final FxController controller;                                // the controller to bind
  private final TreeMap<String,FxComponent> eligibleComponents;         // all eligible components in given form
  private final Map<FxComponent,FxComponentBinding> boundComponents;    // the bindings mapped by component
  private final Map<String,FxComponentBinding> boundPaths;              // the bindings mapped by binding path
  private final List<Binding> dynamicMandatoryBindings;                 // bindings to be monitored for the mandatory attribute
  private boolean needMandatoryUpdate;                                  // true if we need an update of the mandatory bindings
  private final List<Binding> dynamicChangeableBindings;                // bindings to be monitored for the changeable attribute
  private boolean needChangeableUpdate;                                 // true if we need an update of the changeable bindings


  /**
   * Creates a binder for a controller.
   *
   * @param controller the controller to bind
   */
  public DefaultFxComponentBinder(FxController controller) {
    super();
    this.controller = controller;
    eligibleComponents = new TreeMap<>();
    boundComponents = new HashMap<>();
    boundPaths = new TreeMap<>();
    dynamicMandatoryBindings = new ArrayList<>();
    dynamicChangeableBindings = new ArrayList<>();
  }


  @Override
  public FxController getController() {
    return controller;
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
              ValidationScope scope = ValidationScopeFactory.getInstance().getMandatoryScope();
              ValidationContext validationContext = new ValidationContext(
                      mandatoryBinding.getMember().getMemberPath(),
                      mandatoryBinding.getMember().getType(),
                      mandatoryBinding.getModelValue(),
                      parentObject,
                      scope);
              // we must check the scope, because it may change due to CompoundValues
              if (scope.appliesTo(validator.getConfiguredScopes(validationContext)) &&
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
              ValidationScope scope = ValidationScopeFactory.getInstance().getChangeableScope();
              ValidationContext validationContext = new ValidationContext(
                      changeableBinding.getMember().getMemberPath(),
                      changeableBinding.getMember().getType(),
                      changeableBinding.getModelValue(),
                      parentObject,
                      scope);
              // we must check the scope, because it may change due to CompoundValues
              if (scope.appliesTo(validator.getConfiguredScopes(validationContext)) &&
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
  public int bindWithInheritedBindables() {
    return doBind(false, true);
  }


  @Override
  public int bindWithInheritedComponents() {
    return doBind(true, false);
  }


  @Override
  public int bind() {
    return doBind(true, true);
  }


  @Override
  public void unbind() {
    for (FxComponent comp: boundComponents.keySet()) {
      comp.setBinding(null);
    }
    eligibleComponents.clear();
    boundComponents.clear();
    boundPaths.clear();
  }


  @Override
  public Collection<FxComponentBinding> getBindings() {
    return boundComponents.values();
  }


  @Override
  public Collection<FxComponent> getBoundComponents() {
    return boundComponents.keySet();
  }


  @Override
  public Collection<FxComponent> getUnboundComponents() {
    List<FxComponent> unboundComponents = new ArrayList<>();
    for (FxComponent component: eligibleComponents.values()) {
      if (component.getBinding() == null) {
        unboundComponents.add(component);
      }
    }
    return unboundComponents;
  }


  @Override
  public void assertAllBound() throws BindingException {
    Collection<FxComponent> unboundComponents = getUnboundComponents();
    if (!unboundComponents.isEmpty()) {
      for (FxComponent comp: unboundComponents) {
        LOGGER.severe("unbound component " + comp.getComponentPath());
      }
      throw new BindingException("unbound components in " + controller.getClass().getName());
    }
  }


  @Override
  public void addBinding(Binding binding) {
    if (binding instanceof FxComponentBinding) {
      FxComponentBinding oldBinding = boundPaths.put(binding.getMember().getMemberPath(), (FxComponentBinding) binding);
      if (oldBinding != null) {
        throw new BindingException(binding + ": binding path '" + binding.getMember().getMemberPath() +
                                   "' already bound to " + oldBinding.getComponent().getComponentPath());
      }
      oldBinding = boundComponents.put(((FxComponentBinding) binding).getComponent(), (FxComponentBinding) binding);
      if (oldBinding != null) {
        throw new BindingException(binding + ": component '" + ((FxComponentBinding) binding).getComponent().getComponentPath() +
                                   "' already bound to " + oldBinding.getMember().getMemberPath());
      }

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
  public FxComponentBinding getBinding(FxComponent component) {
    return boundComponents.get(component);
  }


  @Override
  public FxComponentBinding getBinding(String bindingPath) {
    return boundPaths.get(bindingPath);
  }


  @Override
  public FxComponentBinding removeBinding(FxComponent component) {
    FxComponentBinding binding = boundComponents.remove(component);
    if (binding != null) {
      if (boundPaths.remove(binding.getMember().getMemberPath()) != binding) {
        throw new BindingException("Binding " + binding + " missing in path map");
      }
      binding.getComponent().setBinding(null);
    }
    return binding;
  }


  @Override
  public FxComponentBinding removeBinding(String bindingPath) {
    FxComponentBinding binding = boundPaths.remove(bindingPath);
    if (binding != null) {
      if (boundComponents.remove(binding.getComponent()) != binding) {
        throw new BindingException("Binding " + binding + " missing in component map");
      }
      binding.getComponent().setBinding(null);
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



  @Override
  protected int doBind(BindingMember[] parents, String parentMemberPath, Class<?> parentClass, boolean declaredOnly) {

    /**
     * To detect recursion loops:
     * we don't need to recursively walk down the binding path more
     * than the max binding path length of all components in the controller.
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
    for (BindableElement element: FxBindingFactory.getInstance().getBindableCache().getBindableMap(parentClass, declaredOnly).values()) {

      String fieldMemberName = StringHelper.firstToLower(element.getCamelName());
      String fieldMemberPath = (parentMemberPath == null ? "" : (parentMemberPath + ".")) + fieldMemberName;

      BindingMember[] fieldParents = new BindingMember[parents == null ? 1 : (parents.length + 1)];
      BindingMember   fieldMember  = FxBindingFactory.getInstance().createBindingMember(
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
        FxComponent component = findComponent(fieldMemberPath);
        if (component != null) {
          // found
          Binding binding = FxBindingFactory.getInstance().createComponentBinding(
                                    this, parents, fieldMember, component, element.getBindingOptions());
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
  private FxComponent findComponent(String bindingPath) {

    FxComponent comp = null;

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
   * Gets the the component associated with the declared field in a controller.
   *
   * @param controller the controller declaring the field
   * @param field the form field declared in the controller
   * @return the component, null if field is null or not an FxComponent
   */
  private FxComponent getFxComponent(FxController controller, Field field) {
    FxComponent component = null;
    try {
      if (FxComponent.class.isAssignableFrom(field.getType())) {
        if (!field.isAccessible()) {
          field.setAccessible(true);
        }
        component = (FxComponent) field.get(controller);
      }
    }
    catch (Exception ex) {
      throw new BindingException("cannot access " + field + " in controller " +
                                 controller.getClass().getName(), ex);
    }
    return component;
  }


  /**
   * Adds all eligible components of given controller.
   *
   * @param controller the controller of the component
   * @param controllerPath the path to the component
   * @param checkedControllers set of all controllers
   * @param declaredOnly true if only components declared in controller, else also inherited
   */
  private void addEligibleComponents(FxController controller, String controllerPath,
                                     Set<FxController> checkedControllers, boolean declaredOnly) {

    Parent view = controller.getView();
    if (view instanceof FxContainer && ((FxContainer) view).isBindable()) {
      if (checkedControllers.add(controller)) {
        LOGGER.finer("checking {0} for eligible components", controllerPath);
        // find all members that are FxComponents
        for (Field field:
             (declaredOnly ?
               controller.getClass().getDeclaredFields()
                :
               ReflectionHelper.getAllFields(controller.getClass(),
                                             new Class<?>[] { FxController.class },
                                             false, null, true))) {

          FxComponent component = getFxComponent(controller, field);
          if (component != null && component.isBindable()) {
            String componentPath = controllerPath + "." + field.getName();
            component.setComponentPath(componentPath);
            String bindingPath = component.getBindingPath();
            if (bindingPath == null) {
              // not set: generate binding path
              bindingPath = field.getName();
              // cut some common trailing text which does not belong to the binding path
              bindingPath = StringHelper.removeTrailingText(bindingPath, "Field");
              bindingPath = StringHelper.removeTrailingText(bindingPath, "Node");
              bindingPath = StringHelper.removeTrailingText(bindingPath, "Comp");
              bindingPath = StringHelper.removeTrailingText(bindingPath, "Component");
            }
            // else: binding path already set by the application

            // add to component map
            FxComponent oldComponent = eligibleComponents.put(bindingPath, component);
            if (oldComponent != null) {
              throw new BindingException("binding path '" + bindingPath +
                                         "' already provided by " + oldComponent.getComponentPath());
            }
            LOGGER.finer("added {0}:{1} to eligible components", componentPath, bindingPath);
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
    addEligibleComponents(controller, controller.getClass().getName(), new HashSet<>(), declaredComponentsOnly);
    if (LOGGER.isFineLoggable()) {
      StringBuilder buf = new StringBuilder();
      buf.append("--------- eligible components: ----------\n");
      for (Map.Entry<String,FxComponent> entry: eligibleComponents.entrySet()) {
        buf.append(entry.getKey());
        buf.append(" : ");
        buf.append(entry.getValue().getComponentPath());
        buf.append("\n");
      }
      buf.append("-----------------------------------------\n");
      LOGGER.fine(buf.toString());
    }
    return doBind(null, null, controller.getClass(), declaredBindablesOnly);
  }



}
