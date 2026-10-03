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
import org.tentackle.script.ScriptConverter;

import javax.script.ScriptEngine;
import javax.script.ScriptEngineFactory;
import java.util.function.Function;

/**
 * A scripting language based on a JSR-223 engine.
 * <p>
 * Name and abbreviations are taken from the {@link ScriptEngineFactory}. One {@link ScriptEngine} is created
 * along with the language and used by all its scripts. Unlike the native languages, the JSR-223 languages
 * are no services, but are created by the {@link JSR223ScriptFactory} for each engine found.
 *
 * @author harald, bei
 */
public class JSR223Language extends AbstractScriptingLanguage {

  private static final String THREADING_PARAMETER = "THREADING";
  private static final String JRUBY_ENGINE_NAME_PART = "JRuby";
  private static final String JAVASCRIPT_ENGINE_NAME_PART = "Nashorn";

  private final ScriptEngineFactory engineFactory;
  private final ScriptEngine engine;
  private final boolean threadSafe;
  private final Function<String, String> variableTranslator;


  /**
   * Creates a JSR 223 scripting language.
   *
   * @param engineFactory the language's engine factory
   */
  public JSR223Language(ScriptEngineFactory engineFactory) {
    this.engineFactory = engineFactory;
    engine = engineFactory.getScriptEngine();
    threadSafe = isEngineThreadSafe(engineFactory);
    variableTranslator = getVariableTranslator(engineFactory);
  }

  /**
   * Gets the scripting engine factory.
   *
   * @return the engine factory
   */
  public ScriptEngineFactory getEngineFactory() {
    return engineFactory;
  }

  /**
   * Gets the script engine.
   *
   * @return the engine
   */
  public ScriptEngine getEngine() {
    return engine;
  }

  /**
   * Returns whether the engine is thread-safe.
   *
   * @return true if thread-safe, false if parallel executions must be synchronized
   * @see #isEngineThreadSafe(ScriptEngineFactory)
   */
  public boolean isThreadSafe() {
    return threadSafe;
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
  public Script createScript(String code, boolean cached, boolean threadSafe, Function<String, ScriptConverter> converterProvider) {
    return new JSR223Script(this, getEffectiveCode(code, converterProvider), cached, threadSafe);
  }

  @Override
  public String createLocalVariableReference(String name) {
    return variableTranslator == null ? name : variableTranslator.apply(name);
  }

  /**
   * Determines whether the execution of scripts is thread-safe.
   * <p>
   * An engine is considered thread-safe if it provides a {@code THREADING} parameter, which is
   * {@code null} for engines that are not thread-safe. Exceptions are JRuby, which claims to be
   * {@code THREAD_ISOLATED} but isn't, and Nashorn, which returns {@code null} but is thread-safe
   * because each execution gets its own bindings.
   *
   * @param engineFactory the factory
   * @return true if thread-safe, false if synchronization is required
   */
  protected boolean isEngineThreadSafe(ScriptEngineFactory engineFactory) {
    if (engineFactory.getEngineName().contains(JRUBY_ENGINE_NAME_PART)) {
      // Claims to be THREAD_ISOLATED, but it isn't.
      // Executing more than one script in parallel doesn't work reliably.
      return false;
    }
    if (engineFactory.getEngineName().contains(JAVASCRIPT_ENGINE_NAME_PART)) {
      // the standalone OpenJDK Nashorn is thread-safe although getParameter returns null (since we pass new bindings to eval)
      return true;
    }
    return engineFactory.getParameter(THREADING_PARAMETER) != null;
  }

  /**
   * Determines the optional variable name translator.<br>
   * JRuby references variables as instance variables, i.e. {@code @name}.
   *
   * @param engineFactory the factory
   * @return the translator, null if none
   */
  protected Function<String, String> getVariableTranslator(ScriptEngineFactory engineFactory) {
    if (engineFactory.getEngineName().contains(JRUBY_ENGINE_NAME_PART)) {
      return this::translateRuby;
    }
    return null;
  }

  private String translateRuby(String name) {
    return "@" + name;
  }

}
