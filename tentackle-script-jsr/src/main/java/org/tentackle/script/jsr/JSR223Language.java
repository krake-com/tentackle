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
 * A scripting language based on JSR-223.
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
   * Returns whether engine is threadsafe.
   *
   * @return true if threadsafe, else needs synchronization
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
   * Determines whether the execution of scripts is threadsafe.
   *
   * @param engineFactory the factory
   * @return true if threadsafe, false if synchronization is required
   */
  protected boolean isEngineThreadSafe(ScriptEngineFactory engineFactory) {
    if (engineFactory.getEngineName().contains(JRUBY_ENGINE_NAME_PART)) {
      // Claims to be THREAD_ISOLATED, but it isn't.
      // Executing more than one script in parallel doesn't work reliably.
      return false;
    }
    if (engineFactory.getEngineName().contains(JAVASCRIPT_ENGINE_NAME_PART)) {
      // is threadsafe although getParameter returns null (since we pass bindings as new to eval)
      return true;
    }
    return engineFactory.getParameter(THREADING_PARAMETER) != null;
  }

  /**
   * Determines the optional variable name translator.
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
