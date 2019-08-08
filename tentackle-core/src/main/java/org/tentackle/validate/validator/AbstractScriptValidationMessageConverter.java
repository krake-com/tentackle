/*
 * Tentackle - http://www.tentackle.org.
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

package org.tentackle.validate.validator;

import org.tentackle.misc.CompoundValue;
import org.tentackle.script.ScriptConverter;
import org.tentackle.script.ScriptingLanguage;
import org.tentackle.validate.ValidationUtilities;

/**
 * Converts a validator message script.
 *
 * @author harald
 */
public abstract class AbstractScriptValidationMessageConverter implements ScriptConverter {

  private static final String VALIDATION_UTILITIES_NAME = ValidationUtilities.class.getName();

  @Override
  public String convert(String code, ScriptingLanguage language) {
    int ndx = code.indexOf("@(");
    if (ndx > 0) {
      return code.substring(0, ndx) + VALIDATION_UTILITIES_NAME + ".getInstance().format(" +
              language.createLocalVariableReference(CompoundValue.VAR_OBJECT) +
              "," +
              code.substring(ndx + 2);
    }
    return code;
  }

}
