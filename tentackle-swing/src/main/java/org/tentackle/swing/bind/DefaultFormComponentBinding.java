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

import java.awt.Component;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.StringTokenizer;
import org.tentackle.bind.BindingException;
import org.tentackle.bind.BindingMember;
import org.tentackle.bind.BindingVetoException;
import org.tentackle.common.Constants;
import org.tentackle.log.Logger;
import org.tentackle.log.LoggerFactory;
import org.tentackle.swing.AbstractFormField;
import org.tentackle.swing.AbstractFractionNumberFormField;
import org.tentackle.swing.FormComponent;
import org.tentackle.swing.FormFieldComponent;
import org.tentackle.validate.ChangeableBindingEvaluator;
import org.tentackle.validate.MandatoryBindingEvaluator;
import org.tentackle.validate.ValidationContext;
import org.tentackle.validate.ValidationScopeFactory;
import org.tentackle.validate.Validator;

/**
 * Common implemenation of a binding.
 *
 * @author harald
 */
public class DefaultFormComponentBinding extends AbstractFormBinding implements FormComponentBinding {

  /**
   * logger for this class.
   */
  private static final Logger LOGGER = LoggerFactory.getLogger(DefaultFormComponentBinding.class);

  private List<Validator> fixedMandatoryValidators;     // fixed mandatory validators, null if none
  private List<Validator> fixedChangeableValidators;    // fixed changeable validators, null if none
  private FormComponent component;                      // the bound GUI component (unique constraint)


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
   *          <li>COL=n: set columns.</li>
   *          <li>MAXCOL=n: set maxcolumns.</li>
   *          <li>[-]AUTOSELECT: turn autoselect on/off</li>
   *        </ul>
   * @param parents the members building the declaration chain to this member, null if this binding's member is in container
   * @param member the member field to bind
   */
  public DefaultFormComponentBinding(FormComponentBinder binder, BindingMember[] parents, BindingMember member,
                                     FormComponent component, String componentOptions) {

    super(binder, parents, member);

    if (component == null) {
      throw new NullPointerException("formComponent must not be null");
    }
    this.component = component;

    updateComponentName();
    processOptions(componentOptions);
    determineValidators();
    verifyType();
  }


  @Override
  public FormComponentBinder getBinder() {
    return (FormComponentBinder) super.getBinder();
  }


  @Override
  public FormComponent getFormComponent() {
    return component;
  }


  @Override
  public Object getViewValue() {
    return component.getFormValue();
  }


  @Override
  public void fireToView(Object parent, Object modelValue) throws BindingVetoException {

    super.fireToView(parent, modelValue);

    // set the field's mandatory attribute if there are any fixed mandatory validators.
    // (the dynamic ones are handled by the binder)
    if (fixedMandatoryValidators != null && !fixedMandatoryValidators.isEmpty()) {
      boolean mandatory = false;
      for (Validator validator: fixedMandatoryValidators) {
        if (parent != null) { // if parent path is valid, i.e. no null reference
          ValidationContext validationContext = new ValidationContext();
          validationContext.setParentObject(parent);
          validationContext.setObject(modelValue);
          validationContext.setType(getMember().getType());
          if (ValidationScopeFactory.getInstance().getMandatoryScope().appliesTo(validator.getConfiguredScopes(validationContext)) &&
              validator.isConditionValid(validationContext) &&
              ((MandatoryBindingEvaluator)validator).isMandatory(validationContext)) {
            mandatory = true;   // should only be once, but one never knows
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
      for (Validator validator: fixedChangeableValidators) {
        if (parent != null) { // if parent path is valid, i.e. no null reference
          ValidationContext validationContext = new ValidationContext();
          validationContext.setParentObject(parent);
          validationContext.setObject(modelValue);
          validationContext.setType(getMember().getType());
          if (ValidationScopeFactory.getInstance().getChangeableScope().appliesTo(validator.getConfiguredScopes(validationContext)) &&
              validator.isConditionValid(validationContext) &&
              ((ChangeableBindingEvaluator)validator).isChangeable(validationContext)) {
            changeable = true;   // should only be once, but one never knows
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
      for (Validator validator: validators) {
        if (validator instanceof MandatoryBindingEvaluator &&
            !((MandatoryBindingEvaluator)validator).isMandatoryDynamic()) {
          if (fixedMandatoryValidators == null) {
            fixedMandatoryValidators = new ArrayList<>();
          }
          fixedMandatoryValidators.add(validator);
        }
        if (validator instanceof ChangeableBindingEvaluator &&
            !((ChangeableBindingEvaluator)validator).isChangeableDynamic()) {
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
 This is an estimation derived from the getter and setter methods,
 usually setViewValue and getViewValue.
   *
   * @return the component's type
   */
  @Override
  protected Class<?> getViewType() {
    try {
      // getViewValue hopefully is covariant (and not returning Object)
      Class<?> type = component.getClass().getMethod("getFormValue").getReturnType(); // must exist
      // the setter may be separate method, named setViewValue, but with a more specific type.
      for (Method method: component.getClass().getMethods()) {
        if (method.getName().equals("setFormValue")) {
          Class<?>[] types = method.getParameterTypes();
          if (types.length == 1) {
            if (type.isAssignableFrom(types[0])) {
              // more specific type
              type = types[0];
            }
          }
        }
      }
      // return the most specific type
      return type;
    }
    catch (NoSuchMethodException ex) {
      throw new BindingException("cannot determine component's type", ex);
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
    if (component instanceof Component) {
      if (((Component) component).getName() == null) {
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
  }



  /**
   * Process the component options.
   *
   * @param componentOptions the options from the annotation
   */
  protected void processOptions(String componentOptions) {
    if (componentOptions != null) {
      if (getFormComponent() instanceof FormFieldComponent) {
        FormFieldComponent comp = (FormFieldComponent) getFormComponent();
        StringTokenizer stok = new StringTokenizer(componentOptions.toUpperCase(), ",");
        while (stok.hasMoreTokens()) {
          String token = stok.nextToken();
          if (!processOption(comp, token))  {
            throw new BindingException("unsupported @Bindable option \"" +
                                       token + "\") in " + getMember());
          }
        }
      }
      // else: does not apply to this component.
      // ignore silently because it may intentionally not make sense in the current form.
    }
  }


  /**
   * Processes an option for a component.
   *
   * @param comp the component
   * @param option the option
   * @return true if option known and processed, false if unknown option
   */
  protected boolean processOption(FormFieldComponent comp, String option) {

    boolean processed = false;

    if      (Constants.BIND_UC.equals(option)) {
      comp.setConvert(AbstractFormField.CONVERT_UC);
      processed = true;
    }
    else if (Constants.BIND_LC.equals(option)) {
      comp.setConvert(AbstractFormField.CONVERT_LC);
      processed = true;
    }
    else if (Constants.BIND_AUTOSELECT.equals(option)) {
      comp.setAutoSelect(true);
      processed = true;
    }
    else if (("-" + Constants.BIND_AUTOSELECT).equals(option)) {
      comp.setAutoSelect(false);
      processed = true;
    }
    else if (Constants.BIND_UNSIGNED.equals(option)) {
      // not supported in tt-swing
      processed = true;
    }
    else if (("-" + Constants.BIND_UNSIGNED).equals(option)) {
      // not supported in tt-swing
      processed = true;
    }
    else if (Constants.BIND_UTC.equals(option)) {
      processed = true;
      // nothing to do any further
    }
    else if (option.startsWith(Constants.BIND_MAXCOL + "=")) {
      try {
        comp.setMaxColumns(Integer.parseInt(option.substring(Constants.BIND_MAXCOL.length() + 1)));
        processed = true;
      }
      catch (NumberFormatException ex) {
        throw new BindingException("invalid " + Constants.BIND_MAXCOL + " @Bindable option \"" +
                                   option + "\") in " + getMember(), ex);
      }
    }
    else if (option.startsWith(Constants.BIND_COL + "=")) {
      try {
        comp.setColumns(Integer.parseInt(option.substring(Constants.BIND_COL.length() + 1)));
        processed = true;
      }
      catch (NumberFormatException ex) {
        throw new BindingException("invalid " + Constants.BIND_COL + " @Bindable option \"" +
                                   option + "\") in " + getMember(), ex);
      }
    }
    else if (option.startsWith(Constants.BIND_SCALE + "=")) {
      try {
        if (comp instanceof AbstractFractionNumberFormField) {
          ((AbstractFractionNumberFormField) comp).setScale(Integer.parseInt(option.substring(Constants.BIND_SCALE.length() + 1)));
          processed = true;
        }
        else  {
          throw new BindingException("cannot apply " + Constants.BIND_SCALE + " @Bindable option \"" +
                                     option + "\") in " + getMember() + ": not an AbstractFractionNumberFormField");
        }
      }
      catch (NumberFormatException ex) {
        throw new BindingException("invalid " + Constants.BIND_SCALE + " @Bindable option \"" +
                                   option + "\") in " + getMember(), ex);
      }
    }

    return processed;
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
    return getBinder().getFormContainer();
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
    component.setFormValue(value);
  }

}
