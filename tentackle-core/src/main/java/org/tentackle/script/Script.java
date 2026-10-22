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

package org.tentackle.script;


import java.util.Set;

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
   * Returns whether the script may be invoked from more than one thread in parallel.
   *
   * @return true if thread-safe execution required, false if the script is used by one thread at a time
   */
  boolean isThreadSafe();


  /**
   * Returns whether there is only one compiled script for the same code.
   *
   * @return true if cached
   */
  boolean isCached();


  /**
   * Gets the scripting source code.
   *
   * @return the code
   */
  String getCode();


  /**
   * Validates the script.<br>
   * The script will be compiled, if not already done, but not executed.
   *
   * @throws ScriptRuntimeException if compiling failed
   */
  void validate();


  /**
   * Executes a script.
   *
   * @param <T> the result type
   * @param variables the script variables, null if none
   * @return the evaluated value, null if none
   * @throws ScriptRuntimeException if execution failed
   */
  <T> T execute(Set<ScriptVariable> variables);


  /**
   * Executes a script.
   *
   * @param <T> the result type
   * @param variables the optional script variables
   * @return the evaluated value, null if none
   * @throws ScriptRuntimeException if execution failed
   * @throws IllegalArgumentException if there are any duplicate variable names
   */
  <T> T execute(ScriptVariable... variables);

}
