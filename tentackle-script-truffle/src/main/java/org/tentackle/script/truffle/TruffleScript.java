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

package org.tentackle.script.truffle;

import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.Value;

import org.tentackle.log.Logger;
import org.tentackle.log.LoggerFactory;
import org.tentackle.script.AbstractScript;
import org.tentackle.script.ScriptRuntimeException;
import org.tentackle.script.ScriptVariable;

import java.util.Set;

/**
 * A script based on GraalVM Truffle Scripting.
 *
 * @author bei, harald
 */
public class TruffleScript extends AbstractScript {

  private static final long serialVersionUID = 1L;

  private static final Logger LOGGER = LoggerFactory.getLogger(TruffleScript.class);

  private volatile Source compiledScript;                      // the truffle source


  /**
   * Creates a script.
   *
   * @param language the language instance
   * @param code the scripting code
   * @param cached true if use caching if possible
   * @param threadSafe true if thread safe execution required
   */
  public TruffleScript(TruffleLanguage language, String code, boolean cached, boolean threadSafe) {
    super(language, code, cached, threadSafe);
  }

  @Override
  public TruffleLanguage getLanguage() {
    return (TruffleLanguage) super.getLanguage();
  }

  /**
   * Transforms the return value of a script to a java object.
   *
   * @param val the graal value
   * @return the java object
   */
  protected Object toObject(Value val) {
    Object obj;

    if (val == null || val.isNull()) {
      obj = null;
    }
    else if (val.isBoolean()) {
      obj = Boolean.valueOf(val.asBoolean());
    }
    else if (val.isNumber()) {
      if (val.fitsInByte()) {
        obj = Byte.valueOf(val.asByte());
      }
      else if (val.fitsInShort()) {
        obj = Integer.valueOf(val.asInt());
      }
      else if (val.fitsInInt()) {
        obj = Integer.valueOf(val.asInt());
      }
      else if (val.fitsInLong()) {
        obj = Long.valueOf(val.asLong());
      }
      else if (val.fitsInFloat()) {
        obj = Float.valueOf(val.asFloat());
      }
      else if (val.fitsInDouble()) {
        obj = Double.valueOf(val.asDouble());
        obj = Double.valueOf(val.asDouble());
      }
      else {
        obj = val.asString();
      }
    }
    else if (val.isString()) {
      obj = val.asString();
    }
    else if (val.isProxyObject()) {
      obj = val.asProxyObject();
    }
    else if (val.isHostObject()) {
      obj = val.asHostObject();
    }
    else if (val.isNativePointer()) {
      obj = val.asNativePointer();
    }
    else {
      obj = val;
    }

    return obj;
  }

  /**
   * Executes the given script.
   *
   * @param source the source code unit
   * @return the script result
   */
  @SuppressWarnings("unchecked")
  protected <T> T  executeImpl(Source source) {
    return (T) toObject(TruffleScriptFactory.getContext().eval(source));
  }

  @Override
  public void validate() {
    getCompiledScript();
  }

  @Override
  @SuppressWarnings("unchecked")
  public <T> T execute(Set<ScriptVariable> variables) {
    T result = null;
    Source source = getCompiledScript();
    if (LOGGER.isFinerLoggable()) {
      LOGGER.finer("execute: \n" + getCode() + "\nwith args: " + ScriptVariable.variablesToString(variables));
    }
    try {
      if (variables != null) {
        Value bindings = TruffleScriptFactory.getContext().getBindings(getLanguage().getGraalVmLanguage().getId());
        for (ScriptVariable variable : variables) {
          bindings.putMember(variable.getName(), variable.getValue());
        }
      }
      if (isThreadSafe()) {
        synchronized (source) {
          result = executeImpl(source);
        }
      }
      else {
        result = executeImpl(source);
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
   * Will be parsed if not done yet.
   *
   * @return the compiled script, never null
   */
  protected Source getCompiledScript() {
    Source localScript = compiledScript;
    if (localScript == null) {
      synchronized (this) {
        localScript = compiledScript;
        if (localScript == null) {
          localScript = compiledScript = compileScript(getCode());
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
  protected Source compileScript(String code) {
    try {
      String langId = getLanguage().getGraalVmLanguage().getId();
      LOGGER.fine("compiling script with {0}:\n{1}", langId, code);
      return Source.newBuilder(langId, getCode(), null).cached(isCached()).name(null).interactive(false).buildLiteral();
    }
    catch (RuntimeException ex) {
      throw new ScriptRuntimeException("creating script failed: " + this, ex);
    }
  }

}
