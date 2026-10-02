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
import org.tentackle.fx.FxUtilities;
import org.tentackle.fx.table.TableColumnConfiguration;
import org.tentackle.validate.ChangeableBindingEvaluator;
import org.tentackle.validate.MandatoryBindingEvaluator;
import org.tentackle.validate.ValidationContext;
import org.tentackle.validate.ValidationContextFactory;
import org.tentackle.validate.ValidationScope;
import org.tentackle.validate.ValidationScopeFactory;
import org.tentackle.validate.Validator;

import java.util.ArrayList;
import java.util.List;

/**
 * Implementation of a table binding.
 *
 * @param <S> type of the objects contained within the table's items list
 * @param <T> type of the content in all cells in this column
 * @author harald
 */
public class DefaultFxTableBinding<S,T> extends AbstractBinding implements FxTableBinding<S,T> {

  private final TableColumnConfiguration<S,T> columnConfiguration;    // the column config
  private boolean mandatory;                      // mandatory attribute
  private boolean changeable;                     // changeable attribute
  private List<Validator> mandatoryValidators;    // fixed and dynamic mandatory validators, null if none
  private List<Validator> changeableValidators;   // fixed and dynamic changeable validators, null if none
  private S rowObject;                            // the row object


  /**
   * Creates a binding.
   *
   * @param binder the binder managing this binding
   * @param columnOptions the column options
   * @param parents the members building the declaration chain to this member, null if this binding's member is in container
   * @param columnConfiguration the column configuration
   * @param member the member field to bind
   */
  public DefaultFxTableBinding(FxTableBinder<S> binder, BindingMember[] parents, BindingMember member,
                               TableColumnConfiguration<S,T> columnConfiguration, String columnOptions) {

    super(binder, parents, member);

    this.columnConfiguration = columnConfiguration;

    FxUtilities.getInstance().applyBindingOptions(columnConfiguration, member, columnOptions);
    applyType();
    determineValidators();
  }


  @Override
  @SuppressWarnings("unchecked")
  public FxTableBinder<S> getBinder() {
    return (FxTableBinder<S>) super.getBinder();
  }


  @Override
  @SuppressWarnings("unchecked")
  public T getModelValue() {
    return (T) super.getModelValue();
  }

  /**
   * Applies the model's type to the column configuration.
   */
  @SuppressWarnings("unchecked")
  protected void applyType() {
    columnConfiguration.setGenericType(getMember().getGenericType());
    columnConfiguration.setType((Class<T>) getMember().getType());
  }


  @Override
  protected Class<?> getViewType() {
    return columnConfiguration.getType();
  }


  @Override
  protected void determineValidators() {

    super.determineValidators();

    List<Validator> validators = getValidators();
    if (validators != null) {
      // determine mandatory/changeable fixed and dynamic validators
      for (Validator validator: validators) {
        if (validator instanceof MandatoryBindingEvaluator) {
          if (mandatoryValidators == null) {
            mandatoryValidators = new ArrayList<>();
          }
          mandatoryValidators.add(validator);
        }
        if (validator instanceof ChangeableBindingEvaluator) {
          if (changeableValidators == null) {
            changeableValidators = new ArrayList<>();
          }
          changeableValidators.add(validator);
        }
      }
    }
  }


  @Override
  public void setMandatory(boolean mandatory) {
    this.mandatory = mandatory;
  }

  @Override
  public boolean isMandatory() {
    return mandatory;
  }

  @Override
  public void setChangeable(boolean changeable) {
    this.changeable = changeable;
  }

  @Override
  public boolean isChangeable() {
    return changeable;
  }


  @Override
  public void fireToView(Object parent, Object modelValue) throws BindingVetoException {

    // set the field's mandatory attribute
    if (mandatoryValidators != null && !mandatoryValidators.isEmpty()) {
      boolean cellIsMandatory = false;
      for (Validator validator: mandatoryValidators) {
        if (parent != null) { // if parent path is valid, i.e. no null reference
          ValidationScope scope = ValidationScopeFactory.getInstance().getMandatoryScope();
          ValidationContext validationContext = ValidationContextFactory.getInstance().create(
              getMember().getMemberPath(),
              getMember().getType(),
              modelValue,
              parent,
              scope,
              null);
          if (scope.appliesTo(validator.getConfiguredScopes(validationContext)) &&
              validator.isConditionValid(validationContext) &&
              ((MandatoryBindingEvaluator)validator).isMandatory(validationContext)) {
            cellIsMandatory = true;   // should only be once, but one never knows
            break;
          }
        }
      }
      setMandatory(cellIsMandatory);
    }
    // set the field's changeable attribute
    if (changeableValidators != null && !changeableValidators.isEmpty()) {
      boolean cellIsChangeable = false;
      for (Validator validator: changeableValidators) {
        if (parent != null) { // if parent path is valid, i.e. no null reference
          ValidationScope scope = ValidationScopeFactory.getInstance().getChangeableScope();
          ValidationContext validationContext = ValidationContextFactory.getInstance().create(
              getMember().getMemberPath(),
              getMember().getType(),
              modelValue,
              parent,
              scope,
              null);
          if (scope.appliesTo(validator.getConfiguredScopes(validationContext)) &&
              validator.isConditionValid(validationContext) &&
              ((ChangeableBindingEvaluator)validator).isChangeable(validationContext)) {
            cellIsChangeable = true;   // should only be once, but one never knows
            break;
          }
        }
      }
      setChangeable(cellIsChangeable);
    }

    super.fireToView(parent, modelValue);
  }


  @Override
  protected boolean isValidationRequired() {
    return true;    // always true
  }

  @Override
  protected String viewComponentToString() {
    return getMember() + ":" + columnConfiguration.getName();
  }

  @Override
  public Object getViewValue() {
    // not used, implemented in FxTableCell
    return null;
  }

  @Override
  protected void updateView(Object value) {
    // not used, implemented in FxTableCell
  }

  @Override
  public void setBoundRootObject(S rowObject) {
    this.rowObject = rowObject;
  }

  @Override
  public S getBoundRootObject() {
    return rowObject;
  }

  @Override
  public TableColumnConfiguration<S, T> getConfiguration() {
    return columnConfiguration;
  }

}
