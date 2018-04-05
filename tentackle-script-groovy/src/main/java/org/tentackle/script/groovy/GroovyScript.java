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


package org.tentackle.script.groovy;

import groovy.lang.Binding;
import groovy.lang.GroovyClassLoader;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import org.codehaus.groovy.control.CompilationFailedException;
import org.tentackle.log.Logger;
import org.tentackle.script.AbstractScript;
import org.tentackle.script.ScriptRuntimeException;
import org.tentackle.script.ScriptVariable;


/**
 * A Groovy script.
 *
 * @author harald
 */
public class GroovyScript extends AbstractScript {

  private static final long serialVersionUID = -2854720758013476291L;

  private static final Logger LOGGER = Logger.get(GroovyScript.class);

  /** compiled script classes. */
  private static final GroovyScriptClassCache SCRIPT_CACHE = new GroovyScriptClassCache();



  /**
   * The compiled groovy script.
   */
  public static class CompiledScript {

    private final String effectiveCode;             // the effective source code
    private final groovy.lang.Script groovyScript;  // the compiled script

    /**
     * Creates a compiled script.
     *
     * @param effectiveCode the source code
     * @param groovyScript the groovy script object
     */
    public CompiledScript(String effectiveCode, groovy.lang.Script groovyScript) {
      this.effectiveCode = effectiveCode;
      this.groovyScript = groovyScript;
    }

    /**
     * @return the effectiveCode
     */
    public String getEffectiveCode() {
      return effectiveCode;
    }

    /**
     * @return the groovyScript
     */
    public groovy.lang.Script getGroovyScript() {
      return groovyScript;
    }
  }


  /** the compiled script instance, null if not compiled yet. */
  private volatile CompiledScript compiledScript;


  private boolean executed;                   // true if script has been executed and result is valid
  private Object result;                      // the last execution result

  /**
   * Creates a groovy script.
   *
   * @param language the language instance
   * @param cached true if use caching, if possible
   */
  public GroovyScript(GroovyLanguage language, boolean cached) {
    super(language, cached);
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
  @SuppressWarnings("unchecked")
  public CompiledScript parse() {
    try {
      String effectiveCode = getEffectiveCode();
      Class<groovy.lang.Script> scriptClass = SCRIPT_CACHE.getScriptClass(effectiveCode);
      if (scriptClass == null) {
        LOGGER.fine("compiling script:\n{0}", effectiveCode);
        ClassLoader parent = getClass().getClassLoader();
        try (GroovyClassLoader loader = new GroovyClassLoader(parent)) {
          scriptClass = loader.parseClass(effectiveCode);
        }
        catch (IOException ix) {
          throw new ScriptRuntimeException("closing groovy classloader failed", ix);
        }
        if (isCached()) {
          SCRIPT_CACHE.putScriptClass(effectiveCode, scriptClass);
        }
      }
      else  {
        LOGGER.fine("re-using compiled script:\n{0}", effectiveCode);
      }
      compiledScript = new CompiledScript(effectiveCode, scriptClass.getDeclaredConstructor().newInstance());
      return compiledScript;
    }
    catch (IllegalAccessException | IllegalArgumentException | InstantiationException | NoSuchMethodException |
           SecurityException | InvocationTargetException | CompilationFailedException ex) {
      throw new ScriptRuntimeException("creating script failed: " + this, ex);
    }
  }


  @Override
  public void execute(ScriptVariable... variables) {

    executed = false;
    result = null;

    final CompiledScript script = getCompiledScript();

    if (LOGGER.isFinerLoggable()) {
      LOGGER.finer("execute: \n" + compiledScript.getEffectiveCode() + "\nwith args: " + ScriptVariable.variablesToString(variables));
    }

    try {
      Binding binding = new Binding();
      if (variables != null) {
        for (ScriptVariable variable: variables) {
          binding.setVariable(variable.getName(), variable.getValue());
        }
      }
      if (isThreadSafe()) {
        synchronized (script) {
          result = executeImpl(script, binding);
        }
      }
      else {
        result = executeImpl(script, binding);
      }
    }
    catch (RuntimeException ex) {
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

  private Object executeImpl(CompiledScript script, Binding binding) {
    script.getGroovyScript().setBinding(binding);
    return script.getGroovyScript().run();
  }

}
