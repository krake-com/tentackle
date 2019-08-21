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
 * <p>
 * TODO: works but the ruby-part is wacky.
 *
 * @author bei
 */
public class TruffleScript extends AbstractScript {

  private static final long serialVersionUID = 1L;


  private static final Logger LOGGER = LoggerFactory.getLogger(TruffleScript.class);

  private volatile String scriptCode;         // the source code of the compiled script
  private boolean executed;                   // true if script has been executed and result is val
  private Object result;                      // the last execution result
  private Source source;                      // the truffle source
  private ScriptVariable[] variables;         // script variables

  private boolean ruby;                       // true if script language is ruby
  private Object[] vals;                      // for ruby only: no variable binding


  /**
   * Creates a script.
   *
   * @param language the language instance
   * @param cached   true if use caching, if possible
   */
  public TruffleScript(TruffleScriptingLanguage language, boolean cached) {
    super(language, cached);
    ruby = "ruby".equals(getLanguage().getTruffleLanguage().getId());
  }


  @Override
  public TruffleScriptingLanguage getLanguage() {
    return (TruffleScriptingLanguage) super.getLanguage();
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
        LOGGER.fine("parsing script:\n{0}", scriptCode);
        LOGGER.fine("language = {0}", getLanguage());

        vals = new Object[0];

        if (ruby && (variables != null)) {
          vals = new Object[variables.length];

          StringBuffer sb = new StringBuffer();
          sb.append("lambda { |");

          int i = 0;
          for (ScriptVariable v : variables) {
            if (i > 0) {
              sb.append(",");
            }
            sb.append(v.getName());
            vals[i] = v.getValue();
            LOGGER.fine("ScriptVariable[{0}]={1}", i, vals[i]);
            i++;
          }
          sb.append("| ");
          sb.append(scriptCode);
          sb.append("}");
          scriptCode = sb.toString();
          LOGGER.fine("lambda script:\n{0}", scriptCode);

        }

        if (getLanguage() != null) {
          if (getLanguage().getTruffleLanguage() != null) {
            LOGGER.fine("language.language.id = {0}", getLanguage().getTruffleLanguage().getId());
            try {
              source = Source.newBuilder(getLanguage().getTruffleLanguage().getId(), scriptCode, null).cached(isCached()).name(null).interactive(false).buildLiteral();
            }
            catch (Exception e) {
              LOGGER.warning("error creating sozrce", e);
              e.printStackTrace();
            }
          }
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

  public Source getParsedSource(ScriptVariable... variables) {
    this.variables = variables;

    return source == null ? parse() : source;
  }

  @Override
  public void execute(ScriptVariable... variables) {

    Source source = getParsedSource(variables);

    executed = false;
    result = null;

    if (LOGGER.isFinerLoggable()) {
      LOGGER.finer("execute: \n" + scriptCode + "\nwith args: " + ScriptVariable.variablesToString(variables));
    }

    try {

      Value bindings = TruffleScriptFactory.getContext().getBindings(getLanguage().getTruffleLanguage().getId());

      if (variables != null) {
        for (ScriptVariable variable : variables) {
          if (usesMemberBindings()) {
            try {
              bindings.putMember(variable.getName(), variable.getValue());
            }
            catch (Exception e) {
              throw new ScriptRuntimeException("putMember not supported for language " + getLanguage());
            }
          }
        }
      }
      if (isThreadSafe()) {
        synchronized (source) {
          result = executeImpl(source, vals);
        }
      }
      else {
        result = executeImpl(source, vals);
      }
    }
    catch (Exception ex) {
      throw new ScriptRuntimeException(ex);
    }

    LOGGER.finer("returned: {0}", result.toString());

    executed = true;
  }

  public boolean usesMemberBindings() {
    return !ruby;
  }

  @Override
  public Object getResult() {
    if (!executed) {
      throw new ScriptRuntimeException("script not executed");
    }
    return result;
  }


  protected Object toObject(Value val) {
    Object obj = null;
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
   * @param arguments the script arguments, null if via binding
   * @return the script result
   */
  protected Object executeImpl(Source source, Object arguments[]) {
    Value val = null;
    try {
      if (source != null) {
        if (usesMemberBindings()) {
          val = TruffleScriptFactory.getContext().eval(source);
        }
        else if (ruby) {
          val = TruffleScriptFactory.getContext().eval(source);
          val = val.execute(arguments);
        }
      }
    }
    catch (Exception e) {
      LOGGER.warning("error executing script", e);
      e.printStackTrace();
    }

    return toObject(val);
  }

}
