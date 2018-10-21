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

package org.tentackle.common;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Helper methods dealing with exceptions.
 *
 * @author harald
 */
public final class ExceptionHelper {

  /**
   * Extracts the given exception type from an exception chain.
   *
   * @param <T> the exception type to search for
   * @param type the exception class to search for
   * @param first true if finds the first, else the last in chain
   * @param headOfChain the head of chained exceptions, may be null
   * @return the exception, null if none
   */
  @SuppressWarnings("unchecked")
  public static <T extends Throwable> T extractException(Class<T> type, boolean first, Throwable headOfChain) {
    T ex = null;    // the chained exception found
    while (headOfChain != null) {
      if (type.isAssignableFrom(headOfChain.getClass())) {
        ex = (T) headOfChain;
        if (first)  {
          break;
        }
      }
      headOfChain = headOfChain.getCause();
    }
    return ex;
  }

  /**
   * Extracts any of the given exception types from an exception chain.
   *
   * @param first true if finds the first, else the last in chain
   * @param headOfChain the head of chained exceptions, may be null
   * @param types the exception classes to search for
   * @return the exception, null if none
   */
  @SuppressWarnings("unchecked")
  public static Throwable extractException(boolean first, Throwable headOfChain, Class<? extends Throwable>... types) {
    for (Class<? extends Throwable> type: types) {
      Throwable t = extractException(type, first, headOfChain);
      if (t != null) {
        return t;
      }
    }
    return null;
  }


  /**
   * Concatenates the messages of an exception chain.
   *
   * @param headOfChain the head of chained exceptions, may be null
   * @return the message string
   */
  public static String concatenateMessages(Throwable headOfChain) {
    StringBuilder buf = new StringBuilder();
    if (headOfChain != null) {
      if (headOfChain.getMessage() != null) {
        buf.append(headOfChain.getMessage());
      }
      Throwable cause = headOfChain.getCause();
      while (cause != null) {
        String subMsg = cause.getMessage();
        if (subMsg != null && buf.indexOf(subMsg) < 0) {
          if (buf.length() > 0) {
            buf.append('\n');
          }
          buf.append(subMsg);
        }
        cause = cause.getCause();
      }
    }
    return buf.toString();
  }


  /**
   * Returns the stacktrace of a given exception as a string.
   *
   * @param cause the exception
   * @return the stacktrace as a printable string
   */
  public static String getStackTraceAsString(Throwable cause)  {
    ByteArrayOutputStream bs = new ByteArrayOutputStream();
    try (PrintStream ps = new PrintStream(bs)) {
      cause.printStackTrace(ps);
      ps.flush();
      return bs.toString();
    }
  }


  /**
   * Checks if the class is a valuable information within a stracktrace.
   *
   * @param className the classname
   * @return true if valuable, else noise
   */
  public static boolean isClassValuableForStackTrace(String className) {
    return !className.startsWith("java") && !className.startsWith("com.sun.") &&
           !className.startsWith("sun.") && !className.startsWith("jdk.") &&
           (!className.startsWith("org.tentackle.") ||
            !className.contains("InvocationHandler") && !className.contains("Interceptable"));
  }


  /**
   * Filters a given stacktrace skipping all noisy elements without relevant information for the developer.
   *
   * @param trace the stacktrace
   * @return the reduced stacktrace
   */
  public static StackTraceElement[] filterStackTrace(StackTraceElement[] trace) {
    List<StackTraceElement> stackList = new ArrayList<>();
    if (trace != null && trace.length > 0) {
      boolean valuableFound = false;
      for (StackTraceElement elem: trace) {
        boolean valuable = isClassValuableForStackTrace(elem.getClassName());
        if (valuable || !valuableFound) {
          stackList.add(elem);
        }
        if (valuable) {
          valuableFound = true;
        }
      }
    }
    trace = new StackTraceElement[stackList.size()];
    stackList.toArray(trace);
    return trace;
  }


  /**
   * prevent instantiation.
   */
  private ExceptionHelper() { }

}
