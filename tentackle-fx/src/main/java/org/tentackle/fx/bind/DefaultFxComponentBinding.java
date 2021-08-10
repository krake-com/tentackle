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

import org.tentackle.bind.AbstractBinding;
import org.tentackle.bind.BindingException;
import org.tentackle.bind.BindingMember;
import org.tentackle.bind.BindingVetoException;
import org.tentackle.fx.FxComponent;
import org.tentackle.fx.FxTextComponent;
import org.tentackle.fx.FxUtilities;
import org.tentackle.log.Logger;
import org.tentackle.validate.ChangeableBindingEvaluator;
import org.tentackle.validate.MandatoryBindingEvaluator;
import org.tentackle.validate.ValidationContext;
import org.tentackle.validate.ValidationScope;
import org.tentackle.validate.ValidationScopeFactory;
import org.tentackle.validate.Validator;

import java.awt.Component;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Common implemenation of a binding.
 *
 * @author harald
 */
public class DefaultFxComponentBinding extends AbstractBinding implements FxComponentBinding {

  private static final Logger LOGGER = Logger.get(DefaultFxComponentBinding.class);

  private final FxComponent component;                  // the bound GUI component (unique constraint)
  private List<Validator> fixedMandatoryValidators;     // fixed mandatory validators, null if none
  private List<Validator> fixedChangeableValidators;    // fixed changeable validators, null if none


  /**
   * Creates a binding.
   *
   * @param binder the binder managing this binding
   * @param component the GUI-component to bind
   * @param componentOptions options to configure the component.<br>
   *        Currently defined are:
   *        <ul>
   *          <li>UC: convert to uppercase</li>
   *          <li>LC: convert to lowercase</li>
   *          <li>COLS=n: width in columns</li>
   *          <li>MAXCOLS=n: limit input to max. columns</li>
   *          <li>LINES=n: number of lines in textareas</li>
   *          <li>[-]AUTOSELECT: turn autoselect on/off</li>
   *          <li>UTC: utc timestamps</li>
   *          <li>SCALE=n: numeric scale</li>
   *          <li>UNSIGNED: unsigned numeric value.</li>
   *        </ul>
   * @param parents the members building the declaration chain to this member, null if this binding's member is in controller
   * @param member the member field to bind
   * @see org.tentackle.common.Constants
   */
  public DefaultFxComponentBinding(FxComponentBinder binder, BindingMember[] parents, BindingMember member,
                                   FxComponent component, String componentOptions) {

    super(binder, parents, member);

    this.component = Objects.requireNonNull(component, "component");

    updateComponentName();
    component.setBinding(this);
    if (component instanceof FxTextComponent) {
      FxUtilities.getInstance().applyBindingOptions((FxTextComponent) component, member, componentOptions);
    }
    determineValidators();
    applyType();
  }


  @Override
  public FxComponentBinder getBinder() {
    return (FxComponentBinder) super.getBinder();
  }


  @Override
  public FxComponent getComponent() {
    return component;
  }


  @Override
  public Object getViewValue() {
    return component.getViewValue();
  }


  @Override
  public void fireToView(Object parent, Object modelValue) throws BindingVetoException {

    super.fireToView(parent, modelValue);

    // set the field's mandatory attribute if there are any fixed mandatory validators.
    // (the dynamic ones are handled by the binder)
    if (fixedMandatoryValidators != null && !fixedMandatoryValidators.isEmpty()) {
      boolean mandatory = false;
      for (Validator validator : fixedMandatoryValidators) {
        if (parent != null) { // if parent path is valid, i.e. no null reference
          ValidationScope scope = ValidationScopeFactory.getInstance().getMandatoryScope();
          ValidationContext validationContext = new ValidationContext(
                  getMember().getMemberPath(),
                  getMember().getType(),
                  modelValue,
                  parent,
                  scope);
          if (scope.appliesTo(validator.getConfiguredScopes(validationContext)) &&
              validator.isConditionValid(validationContext) &&
              ((MandatoryBindingEvaluator) validator).isMandatory(validationContext)) {
            mandatory = true;
            break;
          }
        }
      }
      setMandatory(mandatory);
    }
    // set the field's changeable attribute if there are any fixed changeable validators.
    // (the dynamic ones are handled by the binder)
    if (fixedChangeableValidators != null && !fixedChangeableValidators.isEmpty()) {
      boolean changeable = false;
      for (Validator validator : fixedChangeableValidators) {
        if (parent != null) { // if parent path is valid, i.e. no null reference
          ValidationScope scope = ValidationScopeFactory.getInstance().getChangeableScope();
          ValidationContext validationContext = new ValidationContext(
                  getMember().getMemberPath(),
                  getMember().getType(),
                  modelValue,
                  parent,
                  scope);
          if (scope.appliesTo(validator.getConfiguredScopes(validationContext)) &&
              validator.isConditionValid(validationContext) &&
              ((ChangeableBindingEvaluator) validator).isChangeable(validationContext)) {
            changeable = true;
            break;
          }
        }
      }
      setChangeable(changeable);
    }
  }


  @Override
  protected void determineValidators() {

    super.determineValidators();

    List<Validator> validators = getValidators();
    if (validators != null) {
      // determine fixed mandatory and fixed changeable validators
      for (Validator validator : validators) {
        if (validator instanceof MandatoryBindingEvaluator &&
                !((MandatoryBindingEvaluator) validator).isMandatoryDynamic()) {
          if (fixedMandatoryValidators == null) {
            fixedMandatoryValidators = new ArrayList<>();
          }
          fixedMandatoryValidators.add(validator);
        }
        if (validator instanceof ChangeableBindingEvaluator &&
                !((ChangeableBindingEvaluator) validator).isChangeableDynamic()) {
          if (fixedChangeableValidators == null) {
            fixedChangeableValidators = new ArrayList<>();
          }
          fixedChangeableValidators.add(validator);
        }
      }
      // the dynamic validators are managed by the binder
    }
  }


  /**
   * Determines the component's type.<br>
   * This is an estimation derived from the getter and setter methods,
   * usually setViewValue and getViewValue.
   *
   * @return the component's type
   */
  @Override
  protected Class<?> getViewType() {
    return component.getType();
  }


  /**
   * Applies the model's type to the component.
   *
   * @throws BindingException if component does not accept type
   */
  protected void applyType() {
    component.setGenericType(getMember().getGenericType());
    component.setType(getMember().getType());
    if (getMember().isReadOnly()) {
      component.setChangeable(false);
    }
  }


  /**
   * Sets the component name to some meaningful value.<br>
   * Required by some GUI testing tools.
   * <p>
   * The component name will be the <code>memberPath</code> prepended
   * with the names of the parent components, each separated by a slash.<br>
   * Example:
   * <pre>
   * "Shipment/ShipmentFile/Booking/shipment.customer.addressNumber"
   * </pre>
   */
  protected void updateComponentName() {
    if (component instanceof Component && ((Component) component).getName() == null) {
      // if not already set by the application
      StringBuilder buf = new StringBuilder(getMember().getMemberPath());
      // prepend the parent names, if any
      Component parent = ((Component) component).getParent();
      while (parent != null) {
        if (parent.getName() != null) {
          buf.insert(0, parent.getName() + "/");
        }
        parent = parent.getParent();
      }
      ((Component) component).setName(buf.toString());
      LOGGER.fine("{0}.setName(\"{1}\")", component.getComponentPath(), ((Component) component).getName());
    }
  }


  @Override
  public void setMandatory(boolean mandatory) {
    component.setMandatory(mandatory);
  }

  @Override
  public boolean isMandatory() {
    return component.isMandatory();
  }


  @Override
  public void setChangeable(boolean changeable) {
    component.setChangeable(changeable);
  }

  @Override
  public boolean isChangeable() {
    return component.isChangeable();
  }


  @Override
  protected Object getBoundRootObject() {
    return getBinder().getController();
  }

  @Override
  protected boolean isValidationRequired() {
    return true;    // always true
  }

  @Override
  protected String viewComponentToString() {
    return component.getComponentPath();
  }

  @Override
  protected void updateView(Object value) {
    component.setViewValue(value);
  }

}
