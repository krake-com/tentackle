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

import groovy.lang.Binding;
import groovy.lang.GroovyClassLoader;

import org.tentackle.log.Logger;
import org.tentackle.script.AbstractScript;
import org.tentackle.script.ScriptRuntimeException;
import org.tentackle.script.ScriptVariable;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A Groovy script.
 * <p>
 * The code is compiled lazily on first use into a {@link CompiledGroovyScript}, shared by all scripts with the
 * same code if cached. Each execution creates a new script instance with its own {@link Binding}, hence
 * Groovy scripts are thread-safe without any extra synchronization.
 *
 * @author harald
 */
public class GroovyScript extends AbstractScript {

  private static final Logger LOGGER = Logger.get(GroovyScript.class);

  private static final Map<String, CompiledGroovyScript> SCRIPT_CACHE = new ConcurrentHashMap<>();    // <code>:<script>


  /** the compiled script instance, null if not compiled yet. */
  private volatile CompiledGroovyScript compiledScript;

  private final GroovyClassLoader groovyClassLoader;

  /**
   * Creates a Groovy script.
   *
   * @param language the language instance
   * @param groovyClassLoader the Groovy classloader
   * @param code the scripting code
   * @param cached true to use code caching
   * @param threadSafe true if thread safety required
   */
  public GroovyScript(GroovyLanguage language, GroovyClassLoader groovyClassLoader, String code, boolean cached, boolean threadSafe) {
    super(language, code, cached, threadSafe);
    this.groovyClassLoader = groovyClassLoader;
  }

  @Override
  public void validate() {
    getCompiledScript();
  }

  @Override
  @SuppressWarnings("unchecked")
  public <T> T execute(Set<ScriptVariable> variables) {
    T result;
    CompiledGroovyScript script = getCompiledScript();
    LOGGER.finer(() -> "execute: \n" + script.getCode() + "\nwith args: " + ScriptVariable.variablesToString(variables));
    Binding binding = new Binding();
    if (variables != null) {
      for (ScriptVariable variable: variables) {
        binding.setVariable(variable.getName(), variable.getValue());
      }
    }
    // groovy scripting is threadsafe (each execution gets its own instance and binding)
    try {
      result = (T) script.createInstance(binding).run();
    }
    catch (ScriptRuntimeException sx) {
      throw sx;
    }
    catch (RuntimeException ex) {
      throw new ScriptRuntimeException(ex);
    }
    LOGGER.finer("returned: {0}", result);
    return result;
  }


  /**
   * Gets the compiled script.<br>
   * Will be compiled if not done yet.
   *
   * @return the compiled script, never null
   */
  protected CompiledGroovyScript getCompiledScript() {
    CompiledGroovyScript localScript = compiledScript;
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
   * @throws ScriptRuntimeException if compilation failed
   */
  @SuppressWarnings("unchecked")
  protected CompiledGroovyScript compileScript(String code) {
    LOGGER.fine("compiling script:\n{0}", code);
    try {
      Class<groovy.lang.Script> scriptClass = groovyClassLoader.parseClass(code);
      return new CompiledGroovyScript(code, scriptClass);
    }
    catch (RuntimeException ex) {
      String message = ex.getMessage();
      if (message == null) {
        message = ex.getClass().getSimpleName();
      }
      throw new ScriptRuntimeException("compiling script failed (" + message + "): " + this, ex);
    }
  }

}
