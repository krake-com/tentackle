/*
 * Tentackle - https://tentackle.org.
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

package org.tentackle.dbms;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * A statement id.
 * <p>
 * Statement IDs are unique integers used to access prepared statements.
 *
 * @author harald
 */
public class StatementId {


  private static final AtomicInteger count = new AtomicInteger();


  private final int id;     // the id; cannot be changed once set

  /**
   * Creates a unique statement id.
   */
  public StatementId() {
    id = count.incrementAndGet();
  }

  /**
   * Gets the value of the id.
   *
   * @return the id, always &gt; 0
   */
  public int value() {
    return id;
  }


  @Override
  public String toString() {
    return Integer.toString(id);
  }

  // hashCode and equals default implementation is ok because id is unique
}
