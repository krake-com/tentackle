/*
 * Tentackle - http://www.tentackle.org
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
 * A generic script.
 *
 * @author harald
 */
public interface Script {

  /**
   * Gets the scripting language.
   *
   * @return the language
   */
  ScriptingLanguage getLanguage();


  /**
   * Sets whether the script will execute in a threadsafe manner.<br>
   * The default is not threadsafe.
   *
   * @param threadSafe true if threadsafe
   */
  void setThreadSafe(boolean threadSafe);

  /**
   * Returns whether the script will execute in a threadsafe manner.
   *
   * @return true if threadsafe
   */
  boolean isThreadSafe();


  /**
   * Sets the source code.
   *
   * @param code the main code
   */
  void setCode(String code);

  /**
   * Gets the scripting source code.
   *
   * @return the code
   */
  String getCode();


  /**
   * Gets the effective scripting code.
   *
   * @return the code to be compiled/executed
   */
  String getEffectiveCode();


  /**
   * Parses the script.
   *
   * @return the compilation result (usually a script object)
   * @throws ScriptRuntimeException if parsing failed
   */
  Object parse();


  /**
   * Clears the parsed script.
   */
  void clearParsed();

  /**
   * Checks whether the script is already parsed.
   *
   * @return true if parsed
   */
  boolean isParsed();


  /**
   * Sets a converter that will translate the script before being parsed.
   *
   * @param converter the converter, null if none (default)
   */
  void setConverter(ScriptConverter converter);


  /**
   * Gets the converter that will translate the script before being parsed.
   *
   * @return the converter
   */
  ScriptConverter getConverter();


  /**
   * Executes a script.<br>
   * If the script hasn't been compiled yet, it will be compiled.
   *
   * @param variables the script variables
   * @throws ScriptRuntimeException if execution failed
   */
  void execute(ScriptVariable... variables);


  /**
   * Gets the execution result.
   *
   * @return the evaluated value
   * @throws ScriptRuntimeException if never executed before or previous execution failed
   */
  Object getResult();

}
