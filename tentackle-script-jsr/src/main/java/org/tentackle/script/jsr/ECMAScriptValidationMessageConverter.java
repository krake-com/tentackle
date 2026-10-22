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

package org.tentackle.script.jsr;

import org.tentackle.validate.validator.AbstractScriptValidationMessageConverter;
import org.tentackle.validate.validator.MessageScriptConverter;

/**
 * Converts a validator message script for ECMAScript (JavaScript) engines.
 * <p>
 * Applies to all JSR-223 engines reporting the language name {@code ECMAScript}, such as the standalone OpenJDK
 * Nashorn or GraalJS.
 *
 * @author harald
 */
@MessageScriptConverter(ECMAScriptValidationMessageConverter.NAME)
public class ECMAScriptValidationMessageConverter extends AbstractScriptValidationMessageConverter {

  /** the language name reported by the ECMAScript engines. */
  public static final String NAME = "ECMAScript";

  /**
   * Creates the converter.
   */
  public ECMAScriptValidationMessageConverter() {
    // see -Xlint:missing-explicit-ctor since Java 16
  }

}
