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

package org.tentackle.script.jsr223;

import org.tentackle.script.AbstractScriptingLanguage;
import org.tentackle.script.Script;

import javax.script.ScriptEngine;
import javax.script.ScriptEngineFactory;
import javax.script.ScriptEngineManager;
import javax.script.ScriptContext;
import javax.script.Bindings;
import java.util.ArrayList;
import java.util.List;

/**
 * A scripting language based on JSR-223.
 *
 * @author harald, bei
 */
public class JSR223ScriptingLanguage extends AbstractScriptingLanguage {

  @Override
  public String getName() {

    ScriptEngineFactory f = getEngineFactory();
    return f == null ? null : f.getLanguageName();
  }

  @Override
  public String[] getAbbreviations() {

    ScriptEngineFactory f = getEngineFactory();

    List<String> shortNames = f == null ? null : f.getNames();
    return shortNames == null ? new String[0] : shortNames.toArray(new String[0]);
  }

  @Override
  public Script createScript(boolean cached) {
    return new JSR223Script(this);
  }

  /**
   * Describes the naming of a scripting language.
   */
  public enum Naming {

    /** name is a mimetype. */
    MIMETYPE,

    /** name is a shortname. */
    SHORTNAME,

    /** name is an extension. */
    EXTENSION

  }

  /** the scripting engine. */
  private ScriptEngine engine = null;

  /** the scripting engine. */
  private ScriptEngineFactory engineFactory = null;

  /**
   * Creates a scripting language by mime type.
   *
   * @param name the mimetype, shortname or extension
   * @param naming one of NamingAbstractJSRScriptingLanguage
   */
  public JSR223ScriptingLanguage(String name, Naming naming) {
    switch (naming) {
      case MIMETYPE:
        this.engine = new ScriptEngineManager().getEngineByMimeType(name);
        break;
      case EXTENSION:
        this.engine = new ScriptEngineManager().getEngineByExtension(name);
        break;
      default:
        this.engine = new ScriptEngineManager().getEngineByName(name);
    }
    setUpOptions();

  }

  public JSR223ScriptingLanguage(ScriptEngineFactory engineFactory) {

    this.engineFactory = engineFactory;
    setUpOptions();
  }

  private void setUpOptions() {
    if ( getEngine() != null ) {
      Bindings bindings = getEngine().getBindings(ScriptContext.ENGINE_SCOPE);
      bindings.put("polyglot.js.allowHostAccess", true);
      bindings.put("polyglot.js.allowAllAccess", true);
    }
  }

  /**
   * Gets the scripting engine factory.
   *
   * @return the engine factory
   */
    public ScriptEngineFactory getEngineFactory() {
    if ( engineFactory == null ) {
      if (engine != null) {
        engineFactory = engine.getFactory();
      }
    }
    return engineFactory;
  }

    /**
     * Gets the scripting engine.
     *
     * @return the engine
     */
  public ScriptEngine getEngine() {
    if ( engine == null ) {
      if (engineFactory != null) {
        engine = engineFactory.getScriptEngine();
      }
    }
    return engine;
  }

}
