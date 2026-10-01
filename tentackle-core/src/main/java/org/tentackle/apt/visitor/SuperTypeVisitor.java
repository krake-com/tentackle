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


package org.tentackle.apt.visitor;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.SimpleTypeVisitor8;

/**
 * A type visitor that finds type matches in supertypes as well.<br>
 * The visitor parameter is the class to look for. Types of the {@code java.*} packages are neither
 * matched nor searched, so the visitor is meant for the application's and Tentackle's own types.
 * Returns {@code null} for types other than declared types.
 *
 * @author harald
 */
public class SuperTypeVisitor extends SimpleTypeVisitor8<Boolean, Class<?>> {

  private final ProcessingEnvironment processingEnv;
  private final boolean checkInterfaces;

  /**
   * Creates a super type visitor.
   *
   * @param processingEnv the annotation processing environment
   * @param checkInterfaces true if check interfaces as well (not only supertypes)
   */
  public SuperTypeVisitor(ProcessingEnvironment processingEnv, boolean checkInterfaces) {
    this.processingEnv = processingEnv;
    this.checkInterfaces = checkInterfaces;
  }

  @Override
  public Boolean visitDeclared(DeclaredType t, Class<?> v) {
    return visitDeclared(t, v.getName());

  }

  private boolean visitDeclared(DeclaredType t, String className) {
    boolean rv = visit(t, className);
    if (!rv && checkInterfaces) {
      for (TypeMirror iFace : ((TypeElement) t.asElement()).getInterfaces()) {
        if (iFace.getKind() == TypeKind.DECLARED &&
            visit((DeclaredType) iFace, className)) {
          return true;
        }
      }
    }
    return rv;
  }


  private boolean visit(DeclaredType t, String className) {
    String elementName = t.asElement().toString();
    if (!elementName.startsWith("java.")) {
      if (t.asElement().toString().equals(className)) {
        return true;
      }
      for (TypeMirror m : processingEnv.getTypeUtils().directSupertypes(t)) {
        if (visitDeclared((DeclaredType) m, className)) {
          return true;
        }
      }
    }
    return false;
  }

}
