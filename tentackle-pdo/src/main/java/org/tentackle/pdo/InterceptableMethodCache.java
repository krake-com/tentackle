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


package org.tentackle.pdo;

import java.lang.reflect.Method;
import java.util.Collection;
import java.util.concurrent.ConcurrentHashMap;
import org.tentackle.log.Logger;
import org.tentackle.log.LoggerFactory;
import org.tentackle.log.MethodStatistics;
import org.tentackle.misc.Duration;
import org.tentackle.common.StringHelper;
import org.tentackle.reflect.Interceptable;
import org.tentackle.reflect.InterceptableMethod;
import org.tentackle.reflect.InterceptionUtilities;
import org.tentackle.reflect.Interceptor;
import org.tentackle.reflect.ReflectionHelper;

/**
 * A cache for interceptable methods.<br>
 *
 * @author harald
 */
public class InterceptableMethodCache {

  /**
   * If true method invocations are counted and measured per method.
   * They can be logged via RemoteDbSession#logStatistics by the client request.
   * For debugging and profiling purposes only.
   */
  public boolean collectStatistics;

  /**
   * If true, all invocations are logged.
   */
  public boolean logInvocations;

  /**
   * If true, args and return values will be logged as well.
   */
  public boolean logInvocationDetails;

  /**
   * Minimum milliseconds a method needs to execute before being logged.<br>
   * 0 to disable.<p>
   * For analysis and debugging purposes only.
   */
  public long logMinDurationMillis;

  /**
   * Minimum size of returned collection to get logged.<br>
   * 0 to disable.<p>
   * For analysis and debugging purposes only.
   */
  public int logMinReturnedCollectionSize;


  /**
   * The logger for this class.
   */
  private static final Logger LOGGER = LoggerFactory.getLogger(InterceptableMethodCache.class);


  /**
   * The method cache name.
   */
  private final String name;

  /**
   * The concurrent thread safe method cache.
   */
  private final ConcurrentHashMap<Method,InterceptableMethod> methodCache;

  /**
   * Method statistics.
   */
  private final MethodStatistics methodStats;


  /**
   * Creates a method cache.
   *
   * @param name the cache name (only for logging)
   */
  public InterceptableMethodCache(String name) {
    this.name = name;
    methodCache = new ConcurrentHashMap<>();
    methodStats = new MethodStatistics();
  }


  /**
   * Performs the method invocation.<br>
   * The methods are converted to an {@link InterceptableMethod} and cached.
   *
   * @param effectiveClass the effective class (e.g. the PDO)
   * @param delegate the delegate to invoke the method on
   * @param method the method to invoke
   * @param args the method's arguments
   * @return the method's return value
   * @throws Throwable if invocation failed
   */
  public Object invoke(Class<?> effectiveClass, Interceptable delegate, Method method, Object[] args) throws Throwable {
    InterceptableMethod delegateMethod = methodCache.get(method);
    Class<?> implClass = delegate.getClass();
    if (delegateMethod == null) {
      Method effectiveMethod = findDeclaredMethod(effectiveClass, method);
      Method implMethod = implClass.getMethod(method.getName(), method.getParameterTypes());
      Interceptor interceptor = InterceptionUtilities.getInstance().findInterceptors(
                                                    Interceptable.class, effectiveMethod, implMethod);
      delegateMethod = new InterceptableMethod(method, interceptor);
      methodCache.put(method, delegateMethod);
    }

    // invoke the method
    Duration duration = needDuration() ? new Duration() : null;
    Object retval = delegateMethod.invoke(delegate, args);
    log(delegate, method, args, retval, duration);
    return retval;
  }


  /**
   * Finds the topmost overridden interface method for a given method.
   *
   * @param clazz the interface
   * @param method the method
   * @return the possibly overridden method, else the original method
   */
  protected Method findDeclaredMethod(Class<?> clazz, Method method) {
    try {
      method = clazz.getDeclaredMethod(method.getName(), method.getParameterTypes());
      // if found: return that method
    }
    catch (NoSuchMethodException nme) {
      // try interfaces
      for (Class<?> ifClass: clazz.getInterfaces()) {
        Method m = findDeclaredMethod(ifClass, method);
        if (!m.equals(method)) {
          method = m;
          break;
        }
      }
    }
    return method;
  }


  /**
   * Returns whether measuring the duration is necessary.
   *
   * @return true if duration necessary
   */
  protected boolean needDuration() {
    return logInvocations || collectStatistics || logMinDurationMillis > 0 || logMinReturnedCollectionSize > 0;
  }


  /**
   * Logs the invocation.
   * <p>
   *
   * @param delegate the delegate to invoke the method on
   * @param method the method executed
   * @param args the method args
   * @param retval the method's return value
   * @param duration the duration when execution was started, null if don't care
   */
  protected void log(Interceptable delegate, Method method, Object[] args, Object retval, Duration duration) {

    boolean durationExceeded = false;

    if (duration != null) {
      if (!duration.isValid()) {
        duration.end();
      }
      if (logMinDurationMillis > 0 && duration.millis() > logMinDurationMillis) {
        durationExceeded = true;
      }
      if (collectStatistics) {
        methodStats.countMethodInvocation(method, delegate.getClass(), duration);
      }
    }

    int collectionSize = retval instanceof Collection ? ((Collection) retval).size() : 0;
    boolean collectionSizeExceeded = logMinReturnedCollectionSize > 0 && collectionSize > logMinReturnedCollectionSize;

    if (logInvocations || durationExceeded || collectionSizeExceeded) {
      StringBuilder buf = new StringBuilder();
      if (logInvocationDetails) {
        buf.append(ReflectionHelper.getClassBaseName(method.getDeclaringClass())).
            append(": ").
            append(ReflectionHelper.getClassBaseName(method.getDeclaringClass())).
            append('.').
            append(method.getName()).
            append('(');
        if (args != null) {
          for (int i=0; i < args.length; i++) {
            if (i > 0)  {
              buf.append(',');
            }
            buf.append(StringHelper.objectToLoggableString(args[i]));
          }
        }
        buf.append(") = ").
            append(StringHelper.objectToLoggableString(retval)).
            append(" {").
            append(duration != null ? duration.millisToString() : "<unknown>").
            append("ms");
        if (collectionSize > 0) {
          buf.append(", ").
              append(collectionSize).
              append(" items");
        }
        buf.append('}');
      }
      else {
        buf.append(method.getDeclaringClass().getName()).
            append('.').
            append(method.getName()).
            append(" [").
            append(duration != null ? duration.millisToString() : "<unknown>").
            append("ms]");
      }

      if (durationExceeded || collectionSizeExceeded) {
        if (durationExceeded) {
          buf.insert(0, ">>> duration >>> ");
        }
        if (collectionSizeExceeded) {
          buf.insert(0, ">>> size >>> ");
        }
        LOGGER.warning(buf.toString());
      }
      else {
        LOGGER.info(buf.toString());
      }
    }
  }


  /**
   * Logs the statistics.
   *
   * @param level the logging level
   * @param clear true if clear statistics after dump
   */
  public void logStatistics(Logger.Level level, boolean clear) {
    methodStats.logStatistics(level, "    >" + name + "-Stats: ", clear);
  }

}
