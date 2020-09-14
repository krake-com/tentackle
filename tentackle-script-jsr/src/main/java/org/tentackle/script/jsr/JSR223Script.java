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

import org.tentackle.log.Logger;
import org.tentackle.script.AbstractScript;
import org.tentackle.script.ScriptRuntimeException;
import org.tentackle.script.ScriptVariable;

import javax.script.Bindings;
import javax.script.Compilable;
import javax.script.CompiledScript;
import javax.script.ScriptEngine;
import javax.script.ScriptException;
import javax.script.SimpleBindings;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A script based on JSR-223.
 *
 * @author harald, bei
 */
public class JSR223Script extends AbstractScript {

  private static final long serialVersionUID = 1L;

  private static final Logger LOGGER = Logger.get(JSR223Script.class);

  /** map of code:scripts. */
  private static final Map<String, CompiledJSR223Script> SCRIPT_CACHE = new ConcurrentHashMap<>();

  private volatile CompiledJSR223Script compiledScript;      // the JSR compiled script

  /**
   * Creates a script.
   *
   * @param language the language instance
   * @param code the scripting code
   * @param cached true if use caching if possible
   * @param threadSafe true if thread safe execution required
   */
  public JSR223Script(JSR223Language language, String code, boolean cached, boolean threadSafe) {
    super(language, code, cached, threadSafe);
  }

  @Override
  public JSR223Language getLanguage() {
    return (JSR223Language) super.getLanguage();
  }


  @Override
  public void validate() {
    getCompiledScript();
  }

  @Override
  @SuppressWarnings("unchecked")
  public <T> T execute(Set<ScriptVariable> variables) {
    T result = null;
    CompiledJSR223Script script = getCompiledScript();
    LOGGER.finer(() -> "execute: \n" + script.getCode() + "\nwith args: " + ScriptVariable.variablesToString(variables));
    try {
      Bindings bindings = new SimpleBindings();
      if (variables != null) {
        for (ScriptVariable variable: variables) {
          bindings.put(getLanguage().createLocalVariableReference(variable.getName()), variable.getValue());
        }
      }
      CompiledScript jsrScript = script.getCompiledScript();
      if (getLanguage().isThreadSafe() || !isThreadSafe()) {
        result = (T) jsrScript.eval(bindings);
      }
      else {
        synchronized (jsrScript.getEngine()) {
          result = (T) jsrScript.eval(bindings);
        }
      }
    }
    catch (RuntimeException | ScriptException ex) {
      throw new ScriptRuntimeException(ex);
    }
    LOGGER.finer("returned: {0}", result);
    return result;
  }


  /**
   * Gets the compiled script.<br>
   * Will be parsed if not done yet.
   *
   * @return the compiled script, never null
   */
  protected CompiledJSR223Script getCompiledScript() {
    CompiledJSR223Script localScript = compiledScript;
    if (localScript == null) {
      synchronized (this) {
        localScript = compiledScript;
        if (localScript == null) {
          localScript = compiledScript = isCached() ?
                                         SCRIPT_CACHE.computeIfAbsent(getCode(), this::compileScript) :
                                         compileScript(getCode());
        }
      }
    }
    return localScript;
  }

  /**
   * Compiles script code.
   *
   * @param code the scripting source code
   * @return the compiled script
   */
  protected CompiledJSR223Script compileScript(String code) {
    try {
      ScriptEngine engine = getLanguage().getEngine();
      LOGGER.fine("compiling script with {0}:\n{1}", engine, code);
      CompiledScript script = ((Compilable) engine).compile(code);
      return new CompiledJSR223Script(code, script);
    }
    catch (RuntimeException | ScriptException ex) {
      throw new ScriptRuntimeException("creating script failed: " + this, ex);
    }
  }

}
