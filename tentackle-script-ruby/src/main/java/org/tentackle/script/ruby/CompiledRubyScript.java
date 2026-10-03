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

package org.tentackle.script.ruby;

import org.jruby.embed.EmbedEvalUnit;
import org.jruby.embed.ScriptingContainer;

/**
 * The compiled Ruby script.
 * <p>
 * Holds the parsed {@link EmbedEvalUnit} together with the container it was parsed by.
 */
public class CompiledRubyScript {

  private final ScriptingContainer container;     // the scripting container
  private final String code;                      // the effective source code
  private final EmbedEvalUnit rubyScript;         // the compiled script

  /**
   * Creates a compiled Ruby script.
   *
   * @param container the scripting container
   * @param code the source code
   * @param rubyScript the parsed Ruby script
   */
  public CompiledRubyScript(ScriptingContainer container, String code, EmbedEvalUnit rubyScript) {
    this.container = container;
    this.code = code;
    this.rubyScript = rubyScript;
  }

  /**
   * Gets the scripting container.
   *
   * @return the container
   */
  public ScriptingContainer getContainer() {
    return container;
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
   * Gets the parsed Ruby script.
   *
   * @return the evaluation unit
   */
  public EmbedEvalUnit getRubyScript() {
    return rubyScript;
  }
}
