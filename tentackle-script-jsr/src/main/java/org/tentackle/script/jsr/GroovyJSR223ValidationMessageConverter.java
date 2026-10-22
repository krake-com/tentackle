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
 * Converts a validator message script for the Groovy JSR-223 engine.
 * <p>
 * The converters are selected by the language name, and the {@code groovy-jsr223} engine reports {@code Groovy},
 * the same name as the native provider in tentackle-script-groovy. Hence, this converter is only needed if the
 * native provider is not on the path. Otherwise, the first converter found wins, and both behave the same.
 *
 * @author harald
 */
@MessageScriptConverter(GroovyJSR223ValidationMessageConverter.NAME)
public class GroovyJSR223ValidationMessageConverter extends AbstractScriptValidationMessageConverter {

  /** the language name reported by the Groovy engine. */
  public static final String NAME = "Groovy";

  /**
   * Creates the converter.
   */
  public GroovyJSR223ValidationMessageConverter() {
    // see -Xlint:missing-explicit-ctor since Java 16
  }

}
