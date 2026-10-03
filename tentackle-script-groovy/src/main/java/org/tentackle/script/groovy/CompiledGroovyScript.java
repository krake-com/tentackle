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
import groovy.lang.Script;

import org.tentackle.log.Logger;
import org.tentackle.script.ScriptRuntimeException;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;

/**
 * The compiled Groovy script.
 * <p>
 * Holds the script class parsed from the source code and creates a new instance of it for each execution.
 * A constructor taking a {@link Binding} is preferred. If missing, as it may be for a custom subclass of
 * {@link Script}, the no-arg constructor is used followed by {@link Script#setBinding(Binding)}.
 */
public class CompiledGroovyScript {

  private static final Logger LOGGER = Logger.get(CompiledGroovyScript.class);


  private final String code;                            // the effective source code
  private final Class<Script> scriptClass;              // the compiled script

  private Constructor<Script> bindingConstructor;       // != null to use "new Script(Binding)"
  private Constructor<Script> defaultConstructor;       // else use "new Script()" and "setBinding(Binding)"


  /**
   * Creates a compiled script.
   *
   * @param code the source code
   * @param scriptClass the Groovy script class
   * @throws ScriptRuntimeException if the class provides neither a binding- nor a no-arg constructor
   */
  @SuppressWarnings("unchecked")
  public CompiledGroovyScript(String code, Class<Script> scriptClass) {
    this.code = code;
    this.scriptClass = scriptClass;

    // we scan the constructors in case the script class is an extension of groovy.lang.Script and the developer
    // did not provide a constructor with a Binding parameter
    for (Constructor<?> constructor : scriptClass.getConstructors()) {
      Class<?>[] parameterTypes = constructor.getParameterTypes();
      if (parameterTypes.length == 0) {
        defaultConstructor = (Constructor<Script>) constructor;
      }
      else if (parameterTypes.length == 1 && parameterTypes[0] == Binding.class) {
        bindingConstructor = (Constructor<Script>) constructor;
        break;    // takes precedence
      }
    }
    if (bindingConstructor == null) {
      if (defaultConstructor == null) {
        throw new ScriptRuntimeException("neither default- nor binding-constructor found for " + scriptClass +
                                         ":\n" + code);
      }
      LOGGER.warning("{0} does not provide \"constructor(Binding)\" -> using default constructor instead. Please add the missing constructor!\n{1}",
                     scriptClass, code);
    }
  }

  /**
   * Gets the effective script code.
   *
   * @return the source code
   */
  public String getCode() {
    return code;
  }

  /**
   * Gets the compiled script class.
   *
   * @return the Groovy script class
   */
  public Class<Script> getScriptClass() {
    return scriptClass;
  }

  /**
   * Creates a Groovy script instance.
   *
   * @param binding the Groovy binding
   * @return the script instance
   * @throws ScriptRuntimeException if instantiation failed
   */
  public Script createInstance(Binding binding) {
    try {
      if (bindingConstructor != null) {
        return bindingConstructor.newInstance(binding);
      }
      // fallback to default constructor + setBinding (must exist, see above)
      Script instance = defaultConstructor.newInstance();
      instance.setBinding(binding);
      return instance;
    }
    catch (IllegalAccessException | InstantiationException | InvocationTargetException e) {
      throw new ScriptRuntimeException("creating groovy script instance failed for " + scriptClass + ":\n" + code, e);
    }
  }

}
