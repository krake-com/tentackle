/*
 * Tentackle - https://tentackle.org.
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

package org.tentackle.fx.rdc;

import org.tentackle.bind.Binder;
import org.tentackle.bind.Binding;
import org.tentackle.common.Service;
import org.tentackle.fx.FxControl;
import org.tentackle.fx.bind.FxComponentBinding;
import org.tentackle.validate.ValidationMapper;
import org.tentackle.validate.ValidationResult;
import org.tentackle.validate.ValidationUtilities;

import java.util.TreeSet;

/**
 * Default interactive error factory.
 *
 * @author harald
 */
@Service(InteractiveErrorFactory.class)
public class DefaultInteractiveErrorFactory implements InteractiveErrorFactory {

  @Override
  public InteractiveError createInteractiveError(TreeSet<ValidationMapper> mappers, Binder binder, ValidationResult validationResult) {

    // translate to the correct binder and binding path
    ValidationUtilities.MapResult mapResult =
              ValidationUtilities.getInstance().mapValidationPath(mappers, binder, validationResult.getValidationPath());

    FxControl control = null;

    // map the binding path to a control
    Binding binding = mapResult.getBinder().getBinding(mapResult.getBindingPath());
    if (binding instanceof FxComponentBinding) {
      control = ((FxComponentBinding) binding).getComponent();
    }

    return new DefaultInteractiveError(validationResult, control);
  }

  @Override
  public InteractiveError createInteractiveError(boolean warning, String text, String errorCode,
          ValidationResult validationResult, FxControl control) {
    return new DefaultInteractiveError(warning, text, errorCode, validationResult, control);
  }

}
