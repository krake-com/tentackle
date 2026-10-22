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

package org.tentackle.fx;

import org.tentackle.validate.Validateable;
import org.tentackle.validate.ValidationFailedException;
import org.tentackle.validate.ValidationMapper;
import org.tentackle.validate.ValidationUtilities;

import java.util.NavigableSet;
import java.util.TreeSet;

/**
 * Base class for controllers that validate themselves.<br>
 * Not applicable to controllers that validate their bindables explicitly.
 */
public abstract class AbstractValidateableFxController extends AbstractFxController implements Validateable {

  /**
   * Parent constructor.
   */
  public AbstractValidateableFxController() {
    // see -Xlint:missing-explicit-ctor since Java 16
  }

  /**
   * Validates the controller.
   *
   * @return true if ok, false if validation errors (not only warnings)
   */
  public boolean validateForm() {
    prepareValidation();
    boolean ok = true;
    try {
      ValidationUtilities.getInstance().validate(this);
    }
    catch (ValidationFailedException vx) {
      ok = !FxUtilities.getInstance().showValidationResults(getView(), vx, getValidationMappers(), getBinder());
    }
    return ok;
  }

  /**
   * Prepares the validation.<br>
   * Invoked from within {@link #validateForm()} before the validation is performed.
   */
  protected void prepareValidation() {
    getContainer().clearErrors();
    getContainer().saveView();    // necessary to clear the errors as soon as the control is changed by the user after validation
  }

  /**
   * Gets the validation mappers for controller-local bindables.
   *
   * @return the validation mappers
   */
  protected NavigableSet<ValidationMapper> getValidationMappers() {
    NavigableSet<ValidationMapper> validationMappers = new TreeSet<>();
    validationMappers.add(new ValidationMapper(ValidationUtilities.getInstance().getDefaultValidationPath(this), getBinder(), null, null));
    return validationMappers;
  }

}
