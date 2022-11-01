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

package org.tentackle.reflect.interceptable;

import org.tentackle.reflect.AbstractInterceptor;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;

/**
 * Interceptor.
 *
 * @author harald
 */
@SuppressWarnings("missing-explicit-ctor")
public class OffsetInterceptor extends AbstractInterceptor {

  private int offset;

  @Override
  public void setAnnotation(Annotation annotation) {
    super.setAnnotation(annotation);
    if (annotation instanceof Offset) {
      offset = ((Offset) annotation).value();
    }
  }

  @Override
  public Object proceed(Object proxy, Method method, Object[] args, Object orgProxy, Method orgMethod, Object[] orgArgs) throws Throwable {
    return (Integer) method.invoke(proxy, args) + offset;
  }

}

