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

package org.tentackle.script.groovy;

/**
 * The compiled groovy script.
 */
public class CompiledGroovyScript {

  private final String code;                            // the effective source code
  private final Class<groovy.lang.Script> scriptClass;  // the compiled script

  /**
   * Creates a compiled script.
   *
   * @param code the source code
   * @param scriptClass   the groovy script class
   */
  public CompiledGroovyScript(String code, Class<groovy.lang.Script> scriptClass) {
    this.code = code;
    this.scriptClass = scriptClass;
  }

  /**
   * Gets the effective script code.
   *
   * @return the effectiveCode the source code
   */
  public String getCode() {
    return code;
  }

  /**
   * Gets the compiled script class.
   *
   * @return the groovy script class
   */
  public Class<groovy.lang.Script> getScriptClass() {
    return scriptClass;
  }
}
