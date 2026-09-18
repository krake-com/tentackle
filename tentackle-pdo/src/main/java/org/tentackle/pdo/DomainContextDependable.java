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

package org.tentackle.pdo;

import org.tentackle.reflect.NotInterceptable;


/**
 * Interface all persistent objects must implement.<br>
 * In multi-tenant applications the interface must be subclassed.
 *
 * @see DomainContext
 * @author harald
 */
public interface DomainContextDependable extends DomainContextProvider {


  /**
   * Sets this object's database application context.
   *
   * @param context the domain context
   */
  @NotInterceptable
  void setDomainContext(DomainContext context);


  /**
   * Determines the ID of the domain entity for this context.<br>
   * Sets the attributes of the persistent object accordingly.
   */
  void determineContextId();


  /**
   * Gets the ID of the object describing the context of this object.<br>
   *
   * @return the ID of the context object, 0 if unknown, &lt; 0 if PDO does not support any context (default)
   */
  long getContextId();


  /**
   * Gets the minimum context, i.e. the one that is sufficient for this object.<br>
   * Objects may have a "higher" context they live in.<br>
   * If the current context is already the base context, the current context is returned.
   * Otherwise, a new context will be created.
   *
   * @return the base context, null if no context set at all
   */
  DomainContext getBaseContext();


  /**
   * Creates a valid context for this object.<br>
   * Useful if the object has been loaded without a context or
   * to make the least significant context the object can live in.<br>
   * The returned context is always a new context.
   *
   * @return the new context, never null
   */
  @NotInterceptable
  DomainContext createValidContext();

  /**
   * Returns whether the domain context is immutable.
   *
   * @return true if context cannot be changed
   */
  boolean isDomainContextImmutable();

  /**
   * Sets the immutable flag of the domain context.
   *
   * @param contextImmutable true if context cannot be changed
   */
  void setDomainContextImmutable(boolean contextImmutable);

}
