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

// Created on September 12, 2002, 2:38 PM

package org.tentackle.security;

import org.tentackle.pdo.DomainContext;
import org.tentackle.pdo.Session;

/**
 * The SecurityManager applies security rules to {@link PersistentDomainObject}s
 * and grants or denies permissions.<br>
 *
 * @author harald
 */
public interface SecurityManager {

  /**
   * Checks a permission for a PDO or a PDO class.
   *
   * @param context the domain context
   * @param permission the requested permission
   * @param objectClassId the object class id
   * @param objectId the identifiable id, 0 for all pdos of that class
   * @return the SecurityResult
   */
  SecurityResult evaluate(DomainContext context, Permission permission, int objectClassId, long objectId);

  /**
   * Checks a permission for a non-pdo class.
   *
   * @param context the domain context
   * @param permission the requested permission
   * @param clazz the class
   * @return the SecurityResult
   */
  SecurityResult evaluate(DomainContext context, Permission permission, Class<?> clazz);

  /**
   * Invalidate the manager.<br>
   * The next request will re-initialize it.
   */
  void invalidate();

  /**
   * Removes obsolete rules.
   * <p>
   * Since the model has no relation to the security implementation, security rules may become
   * obsolete if PDOs are deleted. This method will remove such obsolete rules.
   */
  void removeObsoleteRules(Session session);

  /**
   * Determines whether this manager is enabled.
   *
   * @return true if enabled.
   */
  boolean isEnabled();

  /**
   * Enables or disables this security manager.
   * <p>
   * If disabled, all requests return a {@link SecurityResult} with
   * {@link SecurityResult#isDefault()}, {@link SecurityResult#isAccepted} or {@link SecurityResult#isDenied} returning true
   * according to {@link #isAcceptByDefault} and {@link #isDenyByDefault}.
   *
   * @param enabled true to enable the security manager
   * @see #setAcceptByDefault(boolean)
   * @see #setDenyByDefault(boolean)
   */
  void setEnabled(boolean enabled);

  /**
   * Determines whether this manager accepts by default.<br>
   *
   * @return true if accept if no matching rule is found
   */
  boolean isAcceptByDefault();

  /**
   * Sets the default behaviour for "no rules found" for the
   * {@link SecurityResult} to "accepted".
   * This is the default.
   * <p>
   * Notice: setting both acceptByDefault and denyByDefault to false will
   * return "neither accepted nor denied" security results if no rules fire.
   *
   * @param acceptByDefault true if {@link SecurityResult#isAccepted} returns true if no rules found.
   */
  void setAcceptByDefault(boolean acceptByDefault);

}
