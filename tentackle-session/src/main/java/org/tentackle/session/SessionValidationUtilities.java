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

package org.tentackle.session;

import org.tentackle.common.Service;
import org.tentackle.validate.ValidationUtilities;
import org.tentackle.validate.Validator;

import java.util.List;

/**
 * Extended validation utilities.<br>
 * Honors the field- and method-order by using the ordinal of the {@link Persistent}-annotation,
 * which is generated according to the model's definition.<br>
 * As a result, the validators are invoked by priority + position in the source code.
 */
@Service(ValidationUtilities.class)
public class SessionValidationUtilities extends ValidationUtilities {

  /**
   * Creates the validation utilities.
   */
  public SessionValidationUtilities() {
    // see -Xlint:missing-explicit-ctor since Java 16
  }

  @Override
  protected void sortValidators(List<Validator> validators) {
    int maxValidationIndex = 0;
    for (Validator validator : validators) {
      if (validator.getValidationIndex() > maxValidationIndex) {
        maxValidationIndex = validator.getValidationIndex();
      }
    }
    int slotSize = 10;    // times 10 to make it readable when debugging: first digits are the PersistentOrdinal then
    while (slotSize <= maxValidationIndex) {
      slotSize *= 10;
    }
    slotSize *= 10;   // at least one zero between the ordinal and the reflection's index

    // fix validation index if Persistent annotation is present
    for (Validator validator : validators) {
      Persistent persistent = validator.getAnnotatedElement().getAnnotation(Persistent.class);
      if (persistent != null) {
        // offset with ordinal * slotSize (validators are in order within each element)
        validator.setValidationIndex(persistent.ordinal() * slotSize + validator.getValidationIndex());
      }
    }

    super.sortValidators(validators);
  }
}
