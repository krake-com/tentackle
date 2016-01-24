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


package org.tentackle.apt;

import java.util.Set;
import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Types;
import javax.tools.Diagnostic.Kind;
import org.tentackle.apt.visitor.SuperTypeVisitor;
import org.tentackle.reflect.Interceptable;



/**
 * Processor for annotations annotated with {@code @Interception}.<br>
 * Provides annotation checking during compilation and IDE editing.
 *
 * @author harald
 */
public abstract class AbstractInterceptorAnnotationProcessor extends AbstractProcessor {

  /** the interceptor type. */
  public enum Type {

    /** public interceptors only. */
    PUBLIC,

    /** hidden interceptors only. */
    HIDDEN,

    /** public or hidden. */
    ALL

  }

  private final Type type;
  private Types typeUtils;
  private SuperTypeVisitor typeVisitor;


  /**
   * Creates an interception annotation processor.
   *
   * @param type the interceptor type
   */
  public AbstractInterceptorAnnotationProcessor(Type type) {
    this.type = type;
  }



  @Override
  public synchronized void init(ProcessingEnvironment processingEnv) {
    super.init(processingEnv);
    typeVisitor = new SuperTypeVisitor(processingEnv, true);
    typeUtils = processingEnv.getTypeUtils();
  }

  @Override
  public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
    for (TypeElement annotation: annotations) {
      processAnnotation(annotation, roundEnv);
    }
    return true;    // claimed
  }


  private void processAnnotation(TypeElement annotationType, RoundEnvironment roundEnv) {

    for (Element element : roundEnv.getElementsAnnotatedWith(annotationType)) {
      if (element.getKind().equals(ElementKind.METHOD)) {    // only annotated methods

        Element enclosingElement = element.getEnclosingElement();
        if (type == Type.PUBLIC && enclosingElement.getKind().equals(ElementKind.INTERFACE) ||
            type == Type.HIDDEN && enclosingElement.getKind().equals(ElementKind.CLASS) ||
            type == Type.ALL && (enclosingElement.getKind().equals(ElementKind.INTERFACE) ||
            enclosingElement.getKind().equals(ElementKind.CLASS))) {

          TypeElement classElement = (TypeElement) enclosingElement;
          // check interfaces of this class and superclasses
          while (classElement != null) {
            for (TypeMirror iFace: classElement.getInterfaces()) {
              if (iFace.accept(typeVisitor, Interceptable.class)) {
                if (type == Type.HIDDEN && enclosingElement.getKind().equals(ElementKind.INTERFACE)) {
                  processingEnv.getMessager().printMessage(
                    Kind.ERROR,
                    "@" + annotationType.getSimpleName() +
                    " cannot be applied to a method of an Interceptable interface",
                    element);
                }
                // else ok
                return;
              }
            }
            TypeMirror superType = classElement.getSuperclass();
            if (superType == null ||
                superType.getKind() == TypeKind.NONE ||
                superType.getKind() == TypeKind.VOID ||
                superType.getKind() == TypeKind.PACKAGE) {
              break;  // end of chain
            }
            classElement = (TypeElement) typeUtils.asElement(superType);
          }
        }
        if (type == Type.PUBLIC) {
          processingEnv.getMessager().printMessage(
                    Kind.ERROR,
                    "@" + annotationType.getSimpleName() +
                    " can only be applied to a method of an Interceptable interface",
                    element);
        }
        else if (type == Type.HIDDEN) {
          processingEnv.getMessager().printMessage(
                    Kind.ERROR,
                    "@" + annotationType.getSimpleName() +
                    " can only be applied to a method of a class implementing Interceptable",
                    element);
        }
        else {
          processingEnv.getMessager().printMessage(
                    Kind.ERROR,
                    "@" + annotationType.getSimpleName() +
                    " can only be applied to a method of an Interceptable interface or a class implementing Interceptable",
                    element);
        }
      }
      else {
        processingEnv.getMessager().printMessage(
                  Kind.ERROR,
                  "annotated element '" + element + "' is not a method",
                  element);
      }
    }
  }

}
