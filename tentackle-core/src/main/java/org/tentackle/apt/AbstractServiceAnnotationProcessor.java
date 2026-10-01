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

import org.tentackle.apt.visitor.NoArgsVisitor;
import org.tentackle.apt.visitor.SuperTypeVisitor;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.type.TypeVariable;
import javax.lang.model.type.TypeVisitor;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import javax.tools.Diagnostic;
import java.util.Set;

/**
 * Common implementation for service annotation processors.<br>
 * Provides helper methods to verify the annotated classes and reports abstract classes as errors.
 *
 * @author harald
 */
public abstract class AbstractServiceAnnotationProcessor extends AbstractProcessor {

  /** the type utilities, set by {@link #init(ProcessingEnvironment)}. */
  protected Types typeUtils;
  /** the element utilities, set by {@link #init(ProcessingEnvironment)}. */
  protected Elements elementUtils;
  /** visitor to check for a supertype or interface, set by {@link #init(ProcessingEnvironment)}. */
  protected SuperTypeVisitor typeVisitor;
  /** visitor to check for a no-args constructor, set by {@link #init(ProcessingEnvironment)}. */
  protected NoArgsVisitor noArgsVisitor;


  /**
   * Parent constructor.
   */
  public AbstractServiceAnnotationProcessor() {
    // see -Xlint:missing-explicit-ctor since Java 16
  }

  @Override
  public SourceVersion getSupportedSourceVersion() {
    return SourceVersion.latest();
  }

  @Override
  public synchronized void init(ProcessingEnvironment processingEnv) {
    super.init(processingEnv);
    typeUtils = processingEnv.getTypeUtils();
    elementUtils = processingEnv.getElementUtils();
    typeVisitor = new SuperTypeVisitor(processingEnv, true);
    noArgsVisitor = new NoArgsVisitor();
  }

  @Override
  public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
    if (!roundEnv.processingOver()) {
      for (TypeElement type: annotations) {
        for (Element element : roundEnv.getElementsAnnotatedWith(type)) {
          processClass(element);
        }
      }
    }
    return true; // claim annotation
  }


  /**
   * Checks whether the given type mirror is the given class or one of its subtypes.<br>
   * Type variables are resolved to their upper bound.
   *
   * @param m the type mirror
   * @param v the class to check for
   * @return true if the type is or extends/implements the class
   */
  public boolean acceptTypeVisitor(TypeMirror m, Class<?> v) {
    if (m.getKind() == TypeKind.TYPEVAR) {
      TypeVariable typeVar = (TypeVariable) m;
      TypeMirror upper = typeVar.getUpperBound();    // something like "T pdo"
      if (upper != null) {
        m = upper;
      }
    }
    Boolean accept = m.accept(typeVisitor, v);
    return accept != null && accept;
  }

  /**
   * Verifies that the annotated class implements a certain interface or extends a given class.<br>
   * Reports an error if not.
   *
   * @param element the element to verify
   * @param className the fully qualified name of the interface or class
   */
  public void verifyImplements(Element element, String className) {
    // get mirrors without generics
    TypeMirror assignableType = typeUtils.erasure(elementUtils.getTypeElement(className).asType());
    TypeMirror elementType = typeUtils.erasure(element.asType());
    if (!typeUtils.isAssignable(elementType, assignableType)) {
      processingEnv.getMessager().printMessage(
          Diagnostic.Kind.ERROR,
          "class " + element + " must implement " + className, element);
    }
  }

  /**
   * Verifies that the element provides a certain public constructor.
   *
   * @param el the element
   * @param visitor the type visitor that checks the constructor's signature
   * @return true if such a constructor was found
   */
  public boolean verifyConstructor(Element el, TypeVisitor<Boolean, Void> visitor) {
    for (Element subelement : el.getEnclosedElements()) {
      if (subelement.getKind() == ElementKind.CONSTRUCTOR &&
          subelement.getModifiers().contains(Modifier.PUBLIC)) {
        TypeMirror mirror = subelement.asType();
        if (mirror.accept(visitor, null)) {
          return true;
        }
      }
    }
    return false;
  }

  /**
   * Processes the annotated class.<br>
   * The default implementation reports an error if the class is abstract.
   * Subclasses usually override this method to apply further checks.
   *
   * @param element the class
   */
  protected void processClass(Element element) {
    if (element.getModifiers().contains(Modifier.ABSTRACT)) {
      processingEnv.getMessager().printMessage(
          Diagnostic.Kind.ERROR,
          "class " + element + " cannot be abstract", element);
    }
  }

}
