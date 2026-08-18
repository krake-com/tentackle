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

/**
 * A logger whose static {@code getLogger(String)} factory method is inaccessible.
 * <p>
 * {@link DefaultLoggerFactory} finds the method via {@code getDeclaredMethod}, but invoking it
 * throws an {@link IllegalAccessException}, so the factory must fall back to the {@link DefaultLogger}.
 *
 * @author harald
 */
public class PrivateStaticFactoryLogger extends DelegatingTestLogger {

  @SuppressWarnings("unused")   // invoked reflectively by DefaultLoggerFactory (and rejected)
  private static PrivateStaticFactoryLogger getLogger(String name) {
    return new PrivateStaticFactoryLogger(name);
  }

  private PrivateStaticFactoryLogger(String name) {
    super(name);
  }

}
