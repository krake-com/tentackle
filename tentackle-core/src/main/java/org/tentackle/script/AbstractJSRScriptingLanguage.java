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

import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;

/**
 * A scripting language based on JSR-223.
 *
 * @author harald
 */
public abstract class AbstractJSRScriptingLanguage extends AbstractScriptingLanguage {

  /**
   * Describes the naming of a scripting language.
   */
  public static enum Naming {

    /** name is a mimetype. */
    MIMETYPE,

    /** name is a shortname. */
    SHORTNAME,

    /** name is an extension. */
    EXTENSION

  }

  /** the scripting engine. */
  private final ScriptEngine engine;

  /**
   * Creates a scripting language by mime type.
   *
   * @param name the mimetype, shortname or extension
   * @param naming one of Naming
   */
  public AbstractJSRScriptingLanguage(String name, Naming naming) {
    switch (naming) {
      case MIMETYPE:
        this.engine = new ScriptEngineManager().getEngineByMimeType(name);
        break;
      case EXTENSION:
        this.engine = new ScriptEngineManager().getEngineByMimeType(name);
        break;
      default:
        this.engine = new ScriptEngineManager().getEngineByMimeType(name);
    }
  }

  /**
   * Gets the scripting engine.
   *
   * @return the engine
   */
  public ScriptEngine getEngine() {
    return engine;
  }

}
