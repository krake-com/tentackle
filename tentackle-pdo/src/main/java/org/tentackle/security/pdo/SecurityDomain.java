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


package org.tentackle.security.pdo;

import org.tentackle.bind.Bindable;
import org.tentackle.pdo.DomainContext;
import org.tentackle.pdo.DomainObject;
import org.tentackle.security.Permission;


/**
 * Security rule domain interface.
 *
 * @author harald
 */
public interface SecurityDomain extends DomainObject<Security> {

  /**
   * Evaluates a rule.
   * <p>
   * The method is only invoked for rules that apply to the object and/or class in question.
   * Notice further, that the given {@link DomainContext} may point to a different
   * {@link org.tentackle.session.Session} than this security rule belongs to.
   *
   * @param context the domain context the object or class is used in, null = all
   * @param permission the requested permission
   * @return true if rule fires, false if this rule does not apply
   */
  boolean evaluate(DomainContext context, Permission permission);

  /**
   * Gets the grantee as a string.
   *
   * @return the grantee string
   */
  @Bindable
  String granteeToString();

  /**
   * Gets the securable as a string.
   *
   * @return the securable string
   */
  @Bindable
  String securableToString();

  /**
   * Gets the domain context as a string.
   *
   * @return the context string
   */
  @Bindable
  String contextToString();

}
