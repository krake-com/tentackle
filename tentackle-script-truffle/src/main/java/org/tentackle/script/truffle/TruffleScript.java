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

/**
 * A script based on GraalVM Truffle Scripting.
 *
 * @author bei, harald
 */
public class TruffleScript extends AbstractScript {

  private static final long serialVersionUID = 1L;

  private static final Logger LOGGER = LoggerFactory.getLogger(TruffleScript.class);


  private volatile String scriptCode;         // the source code of the compiled script
  private boolean executed;                   // true if script has been executed and result is val
  private Object result;                      // the last execution result
  private Source source;                      // the truffle source


  /**
   * Creates a script.
   *
   * @param language the language instance
   * @param cached   true if use caching, if possible
   */
  public TruffleScript(TruffleLanguage language, boolean cached) {
    super(language, cached);
  }


  @Override
  public TruffleLanguage getLanguage() {
    return (TruffleLanguage) super.getLanguage();
  }

  @Override
  public boolean isParsed() {
    return source != null;
  }

  @Override
  public void clearParsed() {
    source = null;
  }

  public Source parse() {
    try {
      scriptCode = getEffectiveCode();
      if (source == null) {
        LOGGER.fine("parsing language {0}, script:\n{1}", getLanguage().getName(), scriptCode);
        try {
          source = Source.newBuilder(getLanguage().getGraalVmLanguage().getId(), scriptCode, null).cached(isCached()).name(null).interactive(false).buildLiteral();
        }
        catch (Exception e) {
          LOGGER.warning("error creating sozrce", e);
          e.printStackTrace();
        }
      }
      else {
        LOGGER.fine("re-using parse script:\n{0}", scriptCode);
      }
      return source;
    }
    catch (Exception ex) {
      throw new ScriptRuntimeException("creating script failed: " + this, ex);
    }
  }


  @Override
  public void execute(ScriptVariable... variables) {

    Source source = parse();

    executed = false;
    result = null;

    if (LOGGER.isFinerLoggable()) {
      LOGGER.finer("execute: \n" + scriptCode + "\nwith args: " + ScriptVariable.variablesToString(variables));
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

    LOGGER.finer("returned: {0}", result.toString());

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
  protected Object executeImpl(Source source) {
    return toObject(TruffleScriptFactory.getContext().eval(source));
  }

}
