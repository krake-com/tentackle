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

package org.tentackle.reflect;

import org.tentackle.common.StringHelper;
import org.tentackle.log.Logger;
import org.tentackle.log.MethodStatistics;
import org.tentackle.misc.Duration;

import java.lang.reflect.Method;
import java.util.Collection;

/**
 * Invoker for {@link InterceptableMethod}s.<br>
 * Optionally logs and counts method invocations.<br>
 * There is one invoker per interceptable type, e.g. PDO, Operation, etc...
 */
public class InterceptableMethodInvoker extends MethodStatistics {

  private static final Logger LOGGER = Logger.get(InterceptableMethodInvoker.class);


  /**
   * If true method invocations are counted and measured per method.
   */
  private boolean collectStatistics;

  /**
   * If true, all invocations are logged.
   */
  private boolean logInvocations;

  /**
   * If true, args and return values will be logged as well.
   */
  private boolean logInvocationDetails;

  /**
   * Minimum milliseconds a method needs to execute before being logged.<br>
   * 0 to disable.<p>
   * For analysis and debugging purposes only.
   */
  private long logMinDurationMillis;

  /**
   * Minimum size of returned collection to get logged.<br>
   * 0 to disable.<p>
   * For analysis and debugging purposes only.
   */
  private int logMinReturnedCollectionSize;

  /**
   * Set to true if method duration must be measured.
   */
  private boolean durationNecessary;

  /**
   * the invoker's name.
   */
  private final String name;


  /**
   * Creates an invoker.
   *
   * @param name the invoker's name
   */
  public InterceptableMethodInvoker(String name) {
    this.name = name;
  }


  /**
   * Gets the invoker's name.
   *
   * @return the name
   */
  public String getName() {
    return name;
  }


  @Override
  public String toString() {
    return getName();
  }


  /**
   * Retuns whether statictics should be collected.
   *
   * @return true if method invocations are counted and measured per method
   */
  public boolean isCollectingStatistics() {
    return collectStatistics;
  }

  /**
   * Sets whether statictics should be collected.
   *
   * @param collectStatistics true if method invocations are counted and measured per method
   */
  public void setCollectingStatistics(boolean collectStatistics) {
    this.collectStatistics = collectStatistics;
    updateDurationNecessary();
  }

  /**
   * Returns whether each invocation should be logged.
   *
   * @return true if invocations are logged
   */
  public boolean isLoggingInvocations() {
    return logInvocations;
  }

  /**
   * Sets whether each invocation should be logged.
   *
   * @param logInvocations true if invocations are logged
   */
  public void setLoggingInvocations(boolean logInvocations) {
    this.logInvocations = logInvocations;
    updateDurationNecessary();
  }

  /**
   * Returns whether args and return values will be logged as well.
   *
   * @return true if log args and retval
   */
  public boolean isLoggingInvocationDetails() {
    return logInvocationDetails;
  }

  /**
   * Sets whether args and return values will be logged as well.
   *
   * @param logInvocationDetails true if log args and retval
   */
  public void setLoggingInvocationDetails(boolean logInvocationDetails) {
    this.logInvocationDetails = logInvocationDetails;
  }

  /**
   * Gets the minimum milliseconds a method needs to execute before being logged.
   *
   * @return 0 if disabled
   */
  public long getLogMinDurationMillis() {
    return logMinDurationMillis;
  }

  /**
   * Sets the minimum milliseconds a method needs to execute before being logged.
   *
   * @param logMinDurationMillis 0 to disable
   */
  public void setLogMinDurationMillis(long logMinDurationMillis) {
    this.logMinDurationMillis = logMinDurationMillis;
    updateDurationNecessary();
  }

  /**
   * Gets the minimum size of returned collection to get logged.
   *
   * @return 0 if disabled
   */
  public int getLogMinReturnedCollectionSize() {
    return logMinReturnedCollectionSize;
  }

  /**
   * Sets the minimum size of returned collection to get logged.
   *
   * @param logMinReturnedCollectionSize 0 to disable
   */
  public void setLogMinReturnedCollectionSize(int logMinReturnedCollectionSize) {
    this.logMinReturnedCollectionSize = logMinReturnedCollectionSize;
    updateDurationNecessary();
  }


  /**
   * Performs the method invocation.<br>
   * Optionally logs and counts stats.
   *
   * @param delegate the delegate to invoke the method on
   * @param delegateMethod the method of the delegate to invoke
   * @param args the method's arguments
   * @return the method's return value
   * @throws Throwable if invocation failed
   */
  public Object invoke(Object delegate, InterceptableMethod delegateMethod, Object[] args) throws Throwable {
    if (durationNecessary) {
      Duration duration = new Duration();
      Object retval = delegateMethod.invoke(delegate, args);
      duration.end();
      log(delegate, delegateMethod.getMethod(), args, retval, duration);
      return retval;
    }
    else {
      return delegateMethod.invoke(delegate, args);
    }
  }


  /**
   * Logs the statistics.
   *
   * @param level the logging level
   * @param clear true if clear statistics after dump
   */
  public void logStatistics(Logger.Level level, boolean clear) {
    logStatistics(toString(), level, "    >" + name + "-Stats: ", clear);
  }


  /**
   * Logs the invocation.
   *
   * @param delegate the delegate to invoke the method on
   * @param method the method executed
   * @param args the method args
   * @param retval the method's return value
   * @param duration the duration when execution was started
   */
  protected void log(Object delegate, Method method, Object[] args, Object retval, Duration duration) {

    boolean durationExceeded = logMinDurationMillis > 0 && duration.millis() > logMinDurationMillis;
    if (collectStatistics) {
      countMethodInvocation(method, delegate.getClass(), duration);
    }

    int collectionSize = retval instanceof Collection ? ((Collection) retval).size() : 0;
    boolean collectionSizeExceeded = logMinReturnedCollectionSize > 0 && collectionSize > logMinReturnedCollectionSize;

    if (durationExceeded || collectionSizeExceeded) {
      LOGGER.warning(() -> {
        StringBuilder buf = createInvocationLog(delegate, method, args, retval, duration, collectionSize);
        if (durationExceeded) {
          buf.insert(0, ">>> duration exceeded:\n");
        }
        if (collectionSizeExceeded) {
          buf.insert(0, ">>> collection size exceeded:\n");
        }
        return buf.toString();
      });
    }
    else if (logInvocationDetails) {
      LOGGER.info(() -> createInvocationLog(delegate, method, args, retval, duration, collectionSize).toString());
    }
  }


  private StringBuilder createInvocationLog(Object delegate, Method method, Object[] args, Object retval, Duration duration, int collectionSize) {
    StringBuilder buf = new StringBuilder();
    if (logInvocationDetails) {
      buf.append(ReflectionHelper.getClassBaseName(delegate.getClass())).
          append(": ").
          append(ReflectionHelper.getClassBaseName(method.getDeclaringClass())).
          append('.').
          append(method.getName()).
          append('(');
      if (args != null) {
        for (int i = 0; i < args.length; i++) {
          if (i > 0) {
            buf.append(',');
          }
          buf.append(StringHelper.objectToLoggableString(args[i]));
        }
      }
      buf.append(") = ").
          append(StringHelper.objectToLoggableString(retval)).
          append(" {").
          append(duration.millisToString()).
          append("ms");
      if (collectionSize > 0) {
        buf.append(", ").
            append(collectionSize).
            append(" items");
      }
      buf.append('}');
    }
    else {
      buf.append(ReflectionHelper.getClassBaseName(method.getDeclaringClass())).
          append('.').
          append(method.getName()).
          append(" [").
          append(duration.millisToString()).
          append("ms]");
    }
    return buf;
  }

  private void updateDurationNecessary() {
    durationNecessary = logInvocations || collectStatistics || logMinDurationMillis > 0 || logMinReturnedCollectionSize > 0;
  }

}
