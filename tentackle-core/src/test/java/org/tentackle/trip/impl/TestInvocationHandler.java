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

import org.tentackle.reflect.CanonicalConstructor;
import org.tentackle.reflect.CanonicalField;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

public class TestInvocationHandler implements InvocationHandler {

  @CanonicalField(ordinal = 0)
  private final Object target;

  @CanonicalConstructor
  public TestInvocationHandler(Object target) {
    this.target = target;
  }

  @Override
  public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
    if (method.getName().equals("equals") && args.length == 1) {
      Object obj = args[0];
      if (obj instanceof TestProxyInterface) {
        return method.invoke(target, ((TestInvocationHandler) Proxy.getInvocationHandler(obj)).target);
      }
      else  {
        return false;
      }
    }
    return method.invoke(target, args);
  }

}
