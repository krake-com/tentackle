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

import javax.script.CompiledScript;

/**
 * The compiled JSR-223 script.
 * <p>
 * Holds the {@link CompiledScript} together with its source code.
 */
public class CompiledJSR223Script {

  private final String code;                      // the effective source code
  private final CompiledScript compiledScript;    // the compiled script

  /**
   * Creates a compiled script.
   *
   * @param code the source code
   * @param compiledScript the JSR script
   */
  public CompiledJSR223Script(String code, CompiledScript compiledScript) {
    this.code = code;
    this.compiledScript = compiledScript;
  }

  /**
   * Gets the effective script code.
   *
   * @return the source code
   */
  public String getCode() {
    return code;
  }

  /**
   * Gets the compiled script.
   *
   * @return the JSR script
   */
  public CompiledScript getCompiledScript() {
    return compiledScript;
  }
}
