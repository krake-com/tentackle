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

import javax.script.Bindings;
import javax.script.Compilable;
import javax.script.CompiledScript;
import javax.script.ScriptException;
import javax.script.SimpleBindings;
import org.tentackle.log.Logger;

/**
 * A script based on JSR-223.
 *
 * @author harald
 */
public class AbstractJSRScript extends AbstractScript {

  private static final long serialVersionUID = 1L;


  private static final Logger LOGGER = Logger.get(AbstractJSRScript.class);


  /** the JSR compiled script. */
  private CompiledScript compiledScript;

 /** the source code of the compiled script. */
  private volatile String scriptCode;


  private boolean executed;                   // true if script has been executed and result is valid
  private Object result;                      // the last execution result

  /**
   * Creates a script.
   *
   * @param language the language instance
   */
  public AbstractJSRScript(AbstractJSRScriptingLanguage language) {
    super(language, false);   // JSR does not provide the concept of caching script classes
  }

  @Override
  public AbstractJSRScriptingLanguage getLanguage() {
    return (AbstractJSRScriptingLanguage) super.getLanguage();
  }

  /**
   * Gets the compiled script.<br>
   * Will be parsed if not done yet.
   *
   * @return the compiled script
   */
  public CompiledScript getCompiledScript() {
    return compiledScript == null ? parse() : compiledScript;
  }


  @Override
  public boolean isParsed() {
    return compiledScript != null;
  }

  @Override
  public void clearParsed() {
    compiledScript = null;
  }


  @Override
  public CompiledScript parse() {
    try {
      scriptCode = getEffectiveCode();
      if (compiledScript == null) {
        LOGGER.fine("compiling script:\n{0}", scriptCode);
        compiledScript = ((Compilable) getLanguage().getEngine()).compile(scriptCode);
      }
      else  {
        LOGGER.fine("re-using compiled script:\n{0}", scriptCode);
      }
      return compiledScript;
    }
    catch (ScriptException ex) {
      throw new ScriptRuntimeException("creating script failed: " + this, ex);
    }
  }


  @Override
  public void execute(ScriptVariable... variables) {

    executed = false;
    result = null;

    final CompiledScript script = getCompiledScript();

    LOGGER.finer(() -> "execute: \n" + scriptCode + "\nwith args: " + ScriptVariable.variablesToString(variables));

    try {
      Bindings bindings = new SimpleBindings();
      if (variables != null) {
        for (ScriptVariable variable: variables) {
          bindings.put(variable.getName(), variable.getValue());
        }
      }
      if (isThreadSafe()) {
        synchronized (script) {
          result = executeImpl(script, bindings);
        }
      }
      else {
        result = executeImpl(script, bindings);
      }
    }
    catch (ScriptException ex) {
      throw new ScriptRuntimeException(ex);
    }

    LOGGER.finer("returned: {0}", result);

    executed = true;
  }

  @Override
  public Object getResult() {
    if (!executed) {
      throw new ScriptRuntimeException("script not executed");
    }
    return result;
  }

  /**
   * Executes the given script.
   *
   * @param script the compiled script
   * @param bindings the bindings
   * @return the evaluated value
   * @throws ScriptException if execution failed
   */
  protected Object executeImpl(CompiledScript script, Bindings bindings) throws ScriptException {
    return compiledScript.eval(bindings);
  }

}
