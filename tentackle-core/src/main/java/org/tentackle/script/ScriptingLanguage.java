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
 */

package org.tentackle.script;

import java.util.function.Function;

/**
 * Interface tentackle scripting languages must implement.
 * <p>
 * Scripting languages are dynamically loaded and must be annotated with {@code @Service(ScriptingLanguage.class)}.
 *
 * @author harald
 */
public interface ScriptingLanguage {

  /**
   * Gets the language name.
   * <p>
   * The name must be unique!
   *
   * @return the name
   */
  String getName();

  /**
   * Gets the abbreviations for the she-bang notations.
   * <p>
   * Scripts define their language with the unix-like so-called she-bang notation.
   * Example:
   * <pre>
   * "#!gv{object.mainCarriage != null}"
   * </pre>
   * Describes a groovy script.<br>
   * Abbreviations are case-insensitive.
   *
   * @return the abbreviations
   */
  String[] getAbbreviations();

  /**
   * Creates a script.
   *
   * @param code the scripting code
   * @param cached true to use caching if possible because script code may be used more than once
   * @param threadSafe true if the script may be invoked in parallel from different threads
   * @param converterProvider the optional script converter provider
   * @return the created script object
   * @throws ScriptRuntimeException if the script cannot be created
   */
  Script createScript(String code, boolean cached, boolean threadSafe, Function<String, ScriptConverter> converterProvider);

  /**
   * Creates a local reference for a variable name.<br>
   * Some languages need a special syntax, for example {@code @name} in Ruby.
   *
   * @param name the variable
   * @return the language-specific variable
   */
  String createLocalVariableReference(String name);

}
