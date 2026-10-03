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
 * Converts a validator message script for the JRuby JSR-223 engine.
 * <p>
 * The converters are selected by the language name, and the JRuby engine reports {@code ruby}, which differs
 * from the name {@code Ruby} of the native provider in tentackle-script-ruby.
 * The validated object is referenced as {@code @object}, since {@link JSR223Language} translates the variable
 * names for JRuby.
 *
 * @author harald
 */
@MessageScriptConverter(JRubyValidationMessageConverter.NAME)
public class JRubyValidationMessageConverter extends AbstractScriptValidationMessageConverter {

  /** the language name reported by the JRuby engine. */
  public static final String NAME = "ruby";

  /**
   * Creates the converter.
   */
  public JRubyValidationMessageConverter() {
    // see -Xlint:missing-explicit-ctor since Java 16
  }

}
