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

package org.tentackle.trip;

import org.tentackle.common.MappedService;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Allows a server to receive the annotated type from a client that is not authenticated yet.
 * <p>
 * Before a client has logged in, a server enforcing a {@link PreAuthenticationPolicy} resolves only
 * the class names on its allowlist. Each annotated class or interface is added to that list. Annotate
 * the types a client must send to log in or to reach another public service, and nothing else.
 * For example: the session info, the credentials, or the client info of the update service.
 * <p>
 * The allowlist is collected at build time and read as plain class names, so no class has to be
 * loaded to decide whether it may be loaded.
 *
 * @see PreAuthenticationPolicy
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@MappedService(PreAuthenticationPolicy.class)
public @interface TripPreAuth {

  /**
   * Names the public service the type is needed for.<br>
   * Only for documentation and diagnostics, for example {@code "login"} or {@code "update"}.
   *
   * @return the purpose
   */
  String value();

}
