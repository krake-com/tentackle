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
 *
 */

package org.tentackle.apt.visitor;

import java.util.List;
import javax.lang.model.type.ExecutableType;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.SimpleTypeVisitor8;
import org.tentackle.apt.AbstractServiceAnnotationProcessor;

/**
 * A visitor for constructors with a single argument of a given class or one of its subtypes.
 *
 * @author harald
 */
public class ClassArgConstructorVisitor extends SimpleTypeVisitor8<Boolean, Void> {

  private final AbstractServiceAnnotationProcessor annotationProcessor;
  private final Class<?> clazz;

  /**
   * Creates the visitor.
   *
   * @param annotationProcessor the annotation processor to check the argument type with
   * @param clazz the class the argument must be assignable to
   */
  public ClassArgConstructorVisitor(AbstractServiceAnnotationProcessor annotationProcessor, Class<?> clazz) {
    this.annotationProcessor = annotationProcessor;
    this.clazz = clazz;
  }

  @Override
  public Boolean visitExecutable(ExecutableType t, Void v) {
    List<? extends TypeMirror> typeList = t.getParameterTypes();
    return typeList.size() == 1 &&
           annotationProcessor.acceptTypeVisitor(typeList.get(0), clazz);
  }

}
