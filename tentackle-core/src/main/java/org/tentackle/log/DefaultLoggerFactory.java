/**
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


package org.tentackle.log;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.concurrent.ConcurrentHashMap;
import org.tentackle.common.Service;
import org.tentackle.common.ServiceFactory;



/**
 * Default implementation for the logger factory.
 *
 * @author harald
 */
@Service(LoggerFactory.class)
public class DefaultLoggerFactory implements LoggerFactory {

  private static Class<? extends Logger> getConfiguredLoggerClass() {
    try {
      return ServiceFactory.createServiceClass(Logger.class);
    }
    catch (Exception ex) {
      Logger logger = new DefaultLogger(DefaultLoggerFactory.class.getName());
      String msg = "could not configure logging backend -> using DefaultLogger";
      if (logger.isFineLoggable()) {
        logger.fine(msg, ex);
      }
      else  {
        logger.info(msg);
      }
      return DefaultLogger.class;
    }
  }

  private static final Class<? extends Logger> LOGGER_CLASS = getConfiguredLoggerClass();



  // cached static methods
  private final ConcurrentHashMap<Class<? extends Logger>,Method> methods;

  // cached constructors
  private final ConcurrentHashMap<Class<? extends Logger>,Constructor<Logger>> constructors;


  /**
   * Creates the factory.
   */
  public DefaultLoggerFactory() {
    methods = new ConcurrentHashMap<>();
    constructors = new ConcurrentHashMap<>();
  }


  @SuppressWarnings("unchecked")
  @Override
  public Logger getLogger(String name, Class<? extends Logger> loggerClass) {

    if (loggerClass == null) {
      loggerClass = LOGGER_CLASS;
    }

    Constructor<Logger> constructor = null;
    Method method = methods.get(loggerClass);
    if (method == null) {
      constructor = constructors.get(loggerClass);
    }

    Exception methodException = null;
    Exception constructorException = null;

    // check if cached
    if (method == null && constructor == null) {
      // find method
      try {
        method = loggerClass.getDeclaredMethod("getLogger", String.class);
        if (Modifier.isStatic(method.getModifiers())) {
          methods.put(loggerClass, method);
        }
      }
      catch (NoSuchMethodException e) {
        methodException = e;
      }

      if (method == null) {
        // find constructor
        try {
          constructor = (Constructor<Logger>) loggerClass.getConstructor(String.class);
          constructors.put(loggerClass, constructor);
        }
        catch (NoSuchMethodException | SecurityException e) {
          constructorException = e;
        }
      }
    }


    if (method != null) {
      try {
        return (Logger) method.invoke(null, name);
      }
      catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
        methodException = e;
      }
    }
    else if (constructor != null) {
      try {
        return constructor.newInstance(name);
      }
      catch (IllegalAccessException | IllegalArgumentException | InstantiationException | InvocationTargetException e) {
        constructorException = e;
      }
    }

    // could not load logger
    String msg = "Could not load logger " + loggerClass;
    if (methodException != null) {
      msg += " (via static method: " + methodException + ")";
    }
    if (constructorException != null) {
      msg += " (via constructor: " + constructorException;
    }

    // return the default logger
    java.util.logging.Logger.getLogger(name).warning(msg);
    return DefaultLogger.getLogger(name);
  }

}
