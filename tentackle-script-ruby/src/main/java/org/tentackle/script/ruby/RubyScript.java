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


package org.tentackle.script.ruby;

import org.jruby.embed.EmbedEvalUnit;
import org.jruby.embed.ScriptingContainer;
import org.jruby.runtime.builtin.IRubyObject;
import org.tentackle.log.Logger;
import org.tentackle.script.AbstractScript;
import org.tentackle.script.ScriptRuntimeException;
import org.tentackle.script.ScriptVariable;


/**
 * A Ruby script.
 *
 * @author harald
 */
public class RubyScript extends AbstractScript {

  private static final long serialVersionUID = -7188401350915879268L;

  private static final Logger LOGGER = Logger.get(RubyScript.class);


  /**
   * The compiled ruby script.
   */
  public static class CompiledScript {

    private final ScriptingContainer container;     // the scripting container
    private final String effectiveCode;             // the effective source code
    private final EmbedEvalUnit rubyScript;         // the compiled script

    /**
     * Creates a compiled ruby script.
     *
     * @param container the scripting container
     * @param effectiveCode the source code
     * @param rubyScript the ruby script object
     */
    public CompiledScript(ScriptingContainer container, String effectiveCode, EmbedEvalUnit rubyScript) {
      this.container = container;
      this.effectiveCode = effectiveCode;
      this.rubyScript = rubyScript;
    }

    /**
     * @return the container
     */
    public ScriptingContainer getContainer() {
      return container;
    }

    /**
     * @return the effectiveCode
     */
    public String getEffectiveCode() {
      return effectiveCode;
    }

    /**
     * @return the rubyScript
     */
    public EmbedEvalUnit getRubyScript() {
      return rubyScript;
    }
  }

  /** the compiled script instance, null if not compiled yet. */
  private volatile CompiledScript compiledScript;


  private boolean executed;     // true if script has been executed and result is valid
  private Object result;        // the last execution result


  /**
   * Creates a ruby script.
   *
   * @param language the language
   */
  public RubyScript(RubyLanguage language) {
    super(language, false);
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
      String effectiveCode = getEffectiveCode();
      LOGGER.fine("compiling script:\n{0}", effectiveCode);
      ScriptingContainer container = new ScriptingContainer();
      EmbedEvalUnit script = container.parse(effectiveCode);
      compiledScript = new CompiledScript(container, effectiveCode, script);
      return compiledScript;
    }
    catch (RuntimeException ex) {
      throw new ScriptRuntimeException("parsing failed: " + this, ex);
    }
  }


  @Override
  public void execute(ScriptVariable... variables) {

    executed = false;
    result = null;

    final CompiledScript script = getCompiledScript();

    if (LOGGER.isFinerLoggable()) {
      LOGGER.finer("execute: \n" + script.getEffectiveCode() + "\nwith args: " + ScriptVariable.variablesToString(variables));
    }

    try {
      if (isThreadSafe()) {
        synchronized (script) {
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
    executed = true;
  }

  @Override
  public Object getResult() {
    if (!executed) {
      throw new ScriptRuntimeException("script not executed");
    }
    return result;
  }



  @SuppressWarnings("unchecked")
  private Object executeImpl(CompiledScript script, ScriptVariable... variables) {
    if (variables != null) {
      for (ScriptVariable variable: variables) {
        script.getContainer().put(getLanguage().createLocalVariableReference(variable.getName()), variable.getValue());
      }
    }
    IRubyObject rubyResult = script.getRubyScript().run();
    return rubyResult == null ? null : rubyResult.toJava(rubyResult.getJavaClass());
  }

}
