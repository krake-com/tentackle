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
import java.util.function.Consumer;

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
   * @param first true to find the first, else the last in chain
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
   * Extracts any of the given exception types from an exception chain.<br>
   * The chain is followed via {@link Throwable#getCause()}.
   *
   * @param first true to find the first, else the last in chain
   * @param headOfChain the head of chained exceptions, may be null
   * @param types the exception classes to search for
   * @return the exception, null if none
   */
  @SuppressWarnings("unchecked")
  public static Throwable extractException(boolean first, Throwable headOfChain, Class<? extends Throwable>... types) {
    Throwable ex = null;    // the chained exception found
    outer: while (headOfChain != null) {
      for (Class<? extends Throwable> type: types) {
        if (type.isAssignableFrom(headOfChain.getClass())) {
          ex = headOfChain;
          if (first) {
            break outer;
          }
          break;
        }
      }
      headOfChain = headOfChain.getCause();
    }
    return ex;
  }


  /**
   * Extracts the first/last {@link TentackleRuntimeException} marked as temporary in the throwable chain.
   *
   * @param first true to find the first, else the last in chain
   * @param ex the head of chained exceptions, may be null
   * @return the temporary exception, null if not found
   */
  public static TentackleRuntimeException extractTemporaryException(boolean first, Throwable ex) {
    TentackleRuntimeException temporaryException = null;
    while (ex != null) {
      if (ex instanceof TentackleRuntimeException tex && tex.isTemporary()) {
        temporaryException = tex;
        if (first)  {
          break;
        }
      }
      ex = ex.getCause();
    }
    return temporaryException;
  }

  /**
   * Determines the message of the first exception in the chain with a non-blank message.
   *
   * @param ex the head of chained exceptions, may be null
   * @return the message, null if none
   */
  public static String getMessage(Throwable ex) {
    while (ex != null) {
      String message = ex.getMessage();
      if (message != null && !message.isBlank()) {
        return message;
      }
      ex = ex.getCause();
    }
    return null;
  }


  /**
   * Exception handler.
   *
   * @param <T> the exception type
   * @see #handleException(boolean, Throwable, Handler[])
   */
  public static class Handler<T extends Throwable> {
    private final Class<T> type;
    private final Consumer<T> consumer;

    /**
     * Creates an exception handler.
     *
     * @param type the exception class
     * @param consumer the consumer to handle the exception
     */
    public Handler(Class<T> type, Consumer<T> consumer) {
      this.type = type;
      this.consumer = consumer;
    }

    /**
     * Checks if handler applies to the given exception.
     *
     * @param t the exception
     * @return true if handler applies
     */
    public boolean appliesTo(Throwable t) {
      return type.isAssignableFrom(t.getClass());
    }

    /**
     * Handles the exception.
     *
     * @param t the exception
     */
    public void handle(T t) {
      consumer.accept(t);
    }
  }

  /**
   * Handles an exception from an exception chain.<br>
   * Finds the first or last exception in the chain that any of the handlers applies to
   * and invokes that handler. If more than one handler applies to the same exception,
   * the first handler in the list is invoked.
   *
   * @param first true to handle the first, else the last matching exception in chain
   * @param headOfChain the head of chained exceptions, may be null
   * @param handlers the exception handlers
   * @return true if handled, false if no matching exception found in chain
   */
  @SuppressWarnings({ "rawtypes", "unchecked" })
  public static boolean handleException(boolean first, Throwable headOfChain, Handler<? extends Throwable>... handlers) {
    Throwable ex = null;      // the chained exception found
    Handler found = null;     // corresponding handler
    outer: while (headOfChain != null) {
      for (Handler<? extends Throwable> handler: handlers) {
        if (handler.appliesTo(headOfChain)) {
          found = handler;
          ex = headOfChain;
          if (first) {
            break outer;
          }
          break;
        }
      }
      headOfChain = headOfChain.getCause();
    }
    if (found != null) {
      found.handle(ex);
      return true;
    }
    return false;
  }


  /**
   * Concatenates the messages of an exception chain.<br>
   * The messages are separated by newlines. A message of a cause that is already contained
   * in the messages collected so far is skipped.
   *
   * @param headOfChain the head of chained exceptions, may be null
   * @return the message string, empty if no messages
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
          if (!buf.isEmpty()) {
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
   * Checks if the class provides valuable information within a stacktrace.<br>
   * Classes of the JDK and Tentackle's invocation handlers and interceptors are considered noise.
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
   * Filters a given stacktrace skipping noisy elements without relevant information for the developer.<br>
   * The leading elements up to and including the first valuable element are always kept, so that the
   * origin of the exception remains visible. All further noisy elements are skipped
   * (see {@link #isClassValuableForStackTrace(String)}).
   *
   * @param trace the stacktrace, may be null
   * @return the reduced stacktrace, never null
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
   * Prevents instantiation.
   */
  private ExceptionHelper() { }

}
