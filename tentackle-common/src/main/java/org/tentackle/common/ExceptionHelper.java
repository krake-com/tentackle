/*
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

package org.tentackle.common;

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
        if (subMsg != null) {
          if (buf.indexOf(subMsg) < 0) {
            if (buf.length() > 0) {
              buf.append('\n');
            }
            buf.append(subMsg);
          }
        }
        cause = cause.getCause();
      }
    }
    return buf.toString();
  }


  /**
   * prevent instantiation.
   */
  private ExceptionHelper() { }

}
