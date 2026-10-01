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

package org.tentackle.validate.validator;

import org.tentackle.script.ScriptConverter;
import org.tentackle.script.ScriptingLanguage;
import org.tentackle.validate.ValidationContext;
import org.tentackle.validate.ValidationUtilities;

/**
 * Converts a validator message script.
 * <p>
 * Translates the first occurrence of the shortcut {@code @(...)} to an invocation of
 * {@link ValidationUtilities#format(Object, String, Object...)} for the {@code object} variable, for example
 * <pre>
 *   &#64;('{0}_is_wrong', value)
 * </pre>
 * becomes
 * <pre>
 *   org.tentackle.validate.ValidationUtilities.getInstance().format(object, '{0}_is_wrong', value)
 * </pre>
 * Concrete implementations are annotated with &#64;{@link MessageScriptConverter} for their scripting language.
 *
 * @author harald
 */
public abstract class AbstractScriptValidationMessageConverter implements ScriptConverter {

  /** start marker for I18N strings. */
  public static final String I18N_INTRO = "@(";
  private static final int I18N_INTRO_LEN = I18N_INTRO.length();

  private static final String VALIDATION_UTILITIES_NAME = ValidationUtilities.class.getName();


  /**
   * Creates a message script converter.
   */
  public AbstractScriptValidationMessageConverter() {
    // see -Xlint:missing-explicit-ctor since Java 16
  }

  @Override
  public String convert(String code, ScriptingLanguage language) {
    int ndx = code.indexOf(I18N_INTRO);
    if (ndx >= 0) {
      return code.substring(0, ndx) + VALIDATION_UTILITIES_NAME + ".getInstance().format(" +
             language.createLocalVariableReference(ValidationContext.VAR_OBJECT) + "," +
             code.substring(ndx + I18N_INTRO_LEN);
    }
    return code;
  }

}
