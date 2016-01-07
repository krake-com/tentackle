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
import org.tentackle.swing.FormTable;
import org.tentackle.swing.FormTableRowObject;
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
public class DefaultFormTableBinding extends AbstractFormBinding implements FormTableBinding {

  /**
   * logger for this class.
   */
  private static final Logger LOGGER = LoggerFactory.getLogger(DefaultFormTableBinding.class);

  private final int mColumn;                      // the model's column
  private FormTableRowObject rowObject;           // the current form table entry
  private Character convert;                      // UC/LC conversion from annotation, null if not set
  private Boolean autoSelect;                     // autoselect from annotation, null if not set
  private Integer maxCol;                         // maxcol from annotation, null if not set
  private Integer scale;                          // scale from annotation, null if not set
  private boolean mandatory;                      // mandatory attribute
  private boolean changeable;                     // changeable attribute
  private List<Validator> mandatoryValidators;    // fixed and dynamic mandatory validators, null if none
  private List<Validator> changeableValidators;   // fixed and dynamic changeable validators, null if none
  private long lastTablePaintCount;               // last table's paint count


  /**
   * Creates a binding.
   *
   * @param binder the binder managing this binding
   * @param mColumn the model column
   * @param columnOptions the column options
   * @param parents the members building the declaration chain to this member, null if this binding's member is in container
   * @param member the member field to bind
   */
  public DefaultFormTableBinding(FormTableBinder binder, BindingMember[] parents, BindingMember member,
                                 int mColumn, String columnOptions) {

    super(binder, parents, member);

    this.mColumn = mColumn;

    processOptions(columnOptions);
    determineValidators();
    verifyType();
  }


  @Override
  public FormTableBinder getBinder() {
    return (FormTableBinder) super.getBinder();
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
    return getBinder().getModel().getColumnClass(mColumn);
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


  /**
   * Process the component options.
   *
   * @param columnOptions the options from the annotation
   */
  protected void processOptions(String columnOptions) {
    if (columnOptions != null) {
      StringTokenizer stok = new StringTokenizer(columnOptions.toUpperCase(), ",");
      while (stok.hasMoreTokens()) {
        String token = stok.nextToken();
        if (!processOption(token))  {
          throw new BindingException("unsupported @Bindable option \"" +
                                     token + "\") in " + getMember());
        }
      }
    }
  }


  /**
   * Processes an option for a table binding.
   *
   * @param option the option
   * @return true if option known and processed, false if unknown option
   */
  protected boolean processOption(String option) {

    boolean processed = false;


    if      (Constants.BIND_UC.equals(option)) {
      convert = AbstractFormField.CONVERT_UC;
      processed = true;
    }
    else if (Constants.BIND_LC.equals(option)) {
      convert = AbstractFormField.CONVERT_LC;
      processed = true;
    }
    else if (Constants.BIND_AUTOSELECT.equals(option)) {
      autoSelect = true;
      processed = true;
    }
    else if (("-" + Constants.BIND_AUTOSELECT).equals(option)) {
      autoSelect = false;
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
    else if (option.startsWith(Constants.BIND_MAXCOL + "=")) {
      try {
        maxCol = Integer.parseInt(option.substring(Constants.BIND_MAXCOL.length() + 1));
        processed = true;
      }
      catch (NumberFormatException ex) {
        throw new BindingException("invalid " + Constants.BIND_MAXCOL + " @Bindable option \"" +
                                   option + "\") in " + getMember(), ex);
      }
    }
    else if (option.startsWith(Constants.BIND_SCALE + "=")) {
      try {
        scale = Integer.parseInt(option.substring(Constants.BIND_SCALE.length() + 1));
        processed = true;
      }
      catch (NumberFormatException ex) {
        throw new BindingException("invalid " + Constants.BIND_SCALE + " @Bindable option \"" +
                                   option + "\") in " + getMember(), ex);
      }
    }
    else if (Constants.BIND_COL.equals(option)) {
      // COL= not applicable to table cells: just ignore
      processed = true;
    }

    return processed;
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
  public FormTableRowObject getBoundRootObject() {
    return rowObject;
  }


  @Override
  public void fireToView(Object parent, Object modelValue) throws BindingVetoException {

    // set the field's mandatory attribute
    if (mandatoryValidators != null && !mandatoryValidators.isEmpty()) {
      boolean cellIsMandatory = false;
      for (Validator validator: mandatoryValidators) {
        if (parent != null) { // if parent path is valid, i.e. no null reference
          ValidationContext validationContext = new ValidationContext();
          validationContext.setParentObject(parent);
          validationContext.setObject(modelValue);
          validationContext.setType(getMember().getType());
          if (ValidationScopeFactory.getInstance().getMandatoryScope().appliesTo(validator.getConfiguredScopes(validationContext)) &&
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
          ValidationContext validationContext = new ValidationContext();
          validationContext.setParentObject(parent);
          validationContext.setObject(modelValue);
          validationContext.setType(getMember().getType());
          if (ValidationScopeFactory.getInstance().getChangeableScope().appliesTo(validator.getConfiguredScopes(validationContext)) &&
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
  protected void updateView(Object parent, Object value, boolean honourResyncByVetoException) {
    FormTable<?> table = getBinder().getModel().getTable();
    if (table == null) {
      throw new BindingException("table not set in table model");
    }
    long paintCount = table.getPaintCount();
    if (paintCount != lastTablePaintCount) {
      // paint only once per table repaint
      super.updateView(parent, value, honourResyncByVetoException);
      lastTablePaintCount = paintCount;
    }
    // else: ignore excessive fire (which JTable does for some reason)
  }

  @Override
  public void setBoundRootObject(FormTableRowObject rowObject) {
    this.rowObject = rowObject;
  }

  @Override
  protected boolean isValidationRequired() {
    return true;    // always true
  }

  @Override
  protected String viewComponentToString() {
    return getMember() + ":" + mColumn;
  }

  @Override
  public Object getViewValue() {
    return rowObject.getValueAt(mColumn);
  }

  @Override
  public int getColumn() {
    return mColumn;
  }

  @Override
  protected void updateView(Object value) {
    // already done by TableCellRenderer
  }

  @Override
  public Character getConvert() {
    return convert;
  }

  @Override
  public Boolean isAutoSelect() {
    return autoSelect;
  }

  @Override
  public Integer getMaxColumns() {
    return maxCol;
  }

  @Override
  public Integer getScale() {
    return scale;
  }

}
