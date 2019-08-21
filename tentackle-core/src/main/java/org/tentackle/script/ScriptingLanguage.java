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

/**
 * Interface tentackle scripting languages must implement.
 * <br>
 * Scripting languages are dynamically loaded and must be annotated with @Service(ScriptingLanguage.class).
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
   * Describes a groovy script.
   *
   * @return the abbreviations
   */
  String[] getAbbreviations();

  /**
   * Creates a script.
   * <p>
   * Throws {@link ScriptRuntimeException} if script cannot be created.
   *
   * @param cached true if use caching if possible because script code may used more than once
   * @return the created script object
   */
  Script createScript(boolean cached);

  /**
   * Creates a local reference for a variable name.<br>
   * Some languages need a special syntax.
   *
   * @param name the variable
   * @return the language-specific variable
   */
  String createLocalVariableReference(String name);

}
