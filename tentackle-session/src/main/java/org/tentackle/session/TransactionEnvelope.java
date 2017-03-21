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

package org.tentackle.session;

/**
 * An envelope for the execution within a transaction.<br>
 * Provided as an alternative to the {@link Transaction}-interceptor
 * if the class holding the implementation of the transaction is no {@link org.tentackle.reflect.Interceptable}.
 * <p>
 * Usage:
 * <pre>
 * return session.transaction(() -&gt; {
 *    ..do something..
 *    return whatever;
 * });
 * </pre>
 * The returned <code>whatever</code> is returned by <code>session.transaction()</code>.
 * The type is determined by type inference automatically.
 * If there is nothing to return, <code>null</code> must be returned by the envelope:
 * <pre>
 * session.transaction(() -&gt; {
 *    ..do something..
 *    return null;
 * });
 * </pre>
 *
 * @author harald
 */
@FunctionalInterface
public interface TransactionEnvelope<T> {

  /**
   * Runs the code withing the transaction.
   *
   * @return the optional return value
   */
  T run();

}
