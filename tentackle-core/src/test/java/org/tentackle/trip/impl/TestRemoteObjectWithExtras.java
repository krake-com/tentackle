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

package org.tentackle.trip.impl;

/**
 * A remote object with a public method that its remote interface does not declare.
 * <p>
 * Such a method must not be invocable from a client, which can only hold a proxy for
 * {@link TestRemoteInterface} anyway.
 */
@SuppressWarnings("missing-explicit-ctor")
public class TestRemoteObjectWithExtras implements TestRemoteInterface {

  private boolean secretInvoked;

  @Override
  public String getName() {
    return "Eiermann";
  }

  /** Not declared by the remote interface. */
  public String secret() {
    secretInvoked = true;
    return "should not be reachable remotely";
  }

  public boolean isSecretInvoked() {
    return secretInvoked;
  }

  @Override
  public String toString() {
    return "the remote object";
  }

}
