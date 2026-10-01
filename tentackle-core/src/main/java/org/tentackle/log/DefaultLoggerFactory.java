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


package org.tentackle.log;

import org.tentackle.common.Service;
import org.tentackle.common.ServiceFactory;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;


/**
 * Default implementation for the logger factory.
 * <p>
 * The logger class is the implementation of {@link Logger} configured as a service, for example by
 * one of the modules tentackle-log-slf4j or tentackle-log-log4j2v. If there is none, the {@link DefaultLogger}
 * for {@code java.util.logging} is used.
 *
 * @author harald
 */
@Service(LoggerFactory.class)
public class DefaultLoggerFactory implements LoggerFactory {

  private static Class<? extends Logger> getConfiguredLoggerClass() {
    Logger logger = new DefaultLogger(DefaultLoggerFactory.class.getName());
    try {
      Class<Logger> loggerClass = ServiceFactory.createServiceClass(Logger.class);
      logger.fine("configured logger is " + loggerClass.getName());
      return loggerClass;
    }
    catch (RuntimeException ex) {
      logger.info("no logging backend configured -> using default java.util.logging");
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
        Method declaredMethod = loggerClass.getDeclaredMethod("getLogger", String.class);
        if (Modifier.isStatic(declaredMethod.getModifiers())) {
          method = declaredMethod;
          methods.put(loggerClass, declaredMethod);
        }
        else {
          // an instance method is of no use here: fall back to the constructor
          methodException = new NoSuchMethodException("getLogger(String) is not static in " + loggerClass.getName());
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
        catch (NoSuchMethodException e) {
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
    Throwable t = null;
    if (methodException != null) {
      t = methodException;
      msg += " (via static method)";
    }
    if (constructorException != null) {
      t = constructorException;
      msg += " (via constructor)";
    }
    java.util.logging.Logger.getLogger(name).log(Level.WARNING, msg, t);

    // fall back to the default logger
    return DefaultLogger.getLogger(name);
  }

}
