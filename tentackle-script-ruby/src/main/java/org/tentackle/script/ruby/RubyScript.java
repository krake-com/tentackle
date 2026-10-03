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
import org.jruby.runtime.builtin.IRubyObject;

import org.tentackle.log.Logger;
import org.tentackle.script.AbstractScript;
import org.tentackle.script.ScriptRuntimeException;
import org.tentackle.script.ScriptVariable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;


/**
 * A Ruby script.
 * <p>
 * The code is compiled lazily on first use into a {@link CompiledRubyScript}, shared by all scripts with the
 * same code if cached. All scripts are executed by the single {@link ScriptingContainer} of the
 * {@link RubyLanguage}, which is not thread-safe. Hence, if the script is thread-safe, execution is synchronized
 * on the container. The variables are reset to {@code nil} after each execution.
 *
 * @author harald
 */
public class RubyScript extends AbstractScript {

  private static final Logger LOGGER = Logger.get(RubyScript.class);

  /** map of code:scripts. */
  private static final Map<String, CompiledRubyScript> SCRIPT_CACHE = new ConcurrentHashMap<>();

  /** the Ruby container. */
  private final ScriptingContainer container;

  /** the compiled script instance, null if not compiled yet. */
  private volatile CompiledRubyScript compiledScript;


  /**
   * Creates a Ruby script.
   *
   * @param language the language
   * @param container the scripting container
   * @param code the scripting code
   * @param cached true if cached
   * @param threadSafe true if thread-safety required
   */
  public RubyScript(RubyLanguage language, ScriptingContainer container, String code, boolean cached, boolean threadSafe) {
    super(language, code, cached, threadSafe);
    this.container = container;
  }

  @Override
  public void validate() {
    getCompiledScript();
  }

  @Override
  public <T> T execute(Set<ScriptVariable> variables) {
    T result;
    CompiledRubyScript script = getCompiledScript();
    LOGGER.finer(() -> "execute: \n" + script.getCode() + "\nwith args: " + ScriptVariable.variablesToString(variables));
    try {
      if (isThreadSafe()) {
        synchronized (script.getContainer()) {
          result = executeImpl(script, variables);
        }
      }
      else {
        result = executeImpl(script, variables);
      }
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
   * @return the compiled script
   */
  protected CompiledRubyScript getCompiledScript() {
    CompiledRubyScript localScript = compiledScript;
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
  protected CompiledRubyScript compileScript(String code) {
    try {
      LOGGER.fine("compiling script:\n{0}", code);
      EmbedEvalUnit rubyScript = container.parse(code);
      return new CompiledRubyScript(container, code, rubyScript);
    }
    catch (RuntimeException ex) {
      String message = ex.getMessage();
      if (message == null) {
        message = ex.getClass().getSimpleName();
      }
      throw new ScriptRuntimeException("compiling script failed (" + message + "): " + this, ex);
    }
  }

  /**
   * Executes the compiled script.<br>
   * The variables are bound to the container as instance variables and reset to {@code nil} after execution,
   * so that subsequent scripts don't see their values and the objects can be garbage collected
   * (as soon as the next script injects the {@code nil}).
   *
   * @param <T> the result type
   * @param script the compiled script
   * @param variables the variables, null if none
   * @return the result converted to Java, null if none
   */
  @SuppressWarnings("unchecked")
  protected <T> T executeImpl(CompiledRubyScript script, Collection<ScriptVariable> variables) {
    ScriptingContainer scriptContainer = script.getContainer();
    List<String> boundNames = new ArrayList<>();
    try {
      if (variables != null) {
        for (ScriptVariable variable: variables) {
          String name = getLanguage().createLocalVariableReference(variable.getName());
          scriptContainer.put(name, variable.getValue());
          boundNames.add(name);
        }
      }
      IRubyObject rubyResult = script.getRubyScript().run();
      return rubyResult == null ? null : (T) rubyResult.toJava(rubyResult.getJavaClass());
    }
    finally {
      // reset to nil: ScriptingContainer.remove() is not sufficient, since the instance variable remains defined
      // in the runtime and would be copied back into the container's map by the next execution
      for (String name : boundNames) {
        scriptContainer.put(name, null);
      }
    }
  }

}
