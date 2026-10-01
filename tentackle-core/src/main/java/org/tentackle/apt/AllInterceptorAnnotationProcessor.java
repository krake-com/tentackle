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


package org.tentackle.apt;

import org.tentackle.common.AnnotationProcessor;
import org.tentackle.reflect.Interception;

/**
 * Processor for all interception annotations in the core package.<br>
 * Applies to interceptors applicable to methods of interfaces extending {@code Interceptable} or
 * of classes implementing {@code Interceptable}.
 *
 * @author harald
 */
@AnnotationProcessor
public class AllInterceptorAnnotationProcessor extends AbstractInterceptorAnnotationProcessor {

  /**
   * Creates the processor for {@link Interception.Type#ALL}.
   */
  public AllInterceptorAnnotationProcessor() {
    super(Interception.Type.ALL);
  }

}
