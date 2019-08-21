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

import org.tentackle.script.AbstractScriptingLanguage;
import org.tentackle.script.Script;

import javax.script.ScriptEngineFactory;

/**
 * A scripting language based on JSR-223.
 *
 * @author harald, bei
 */
public class JSR223ScriptingLanguage extends AbstractScriptingLanguage {

  private final ScriptEngineFactory engineFactory;

  /**
   * Creates a JSR 223 scripting language.
   *
   * @param engineFactory the language's engine factory
   */
  public JSR223ScriptingLanguage(ScriptEngineFactory engineFactory) {
    this.engineFactory = engineFactory;
  }

  /**
   * Gets the scripting engine factory.
   *
   * @return the engine factory
   */
  public ScriptEngineFactory getEngineFactory() {
    return engineFactory;
  }

  @Override
  public String getName() {
    return engineFactory.getLanguageName();
  }

  @Override
  public String[] getAbbreviations() {
    return engineFactory.getNames().toArray(new String[0]);
  }

  @Override
  public Script createScript(boolean cached) {
    return new JSR223Script(this);
  }

}
