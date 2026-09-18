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

import org.tentackle.apt.visitor.SuperTypeVisitor;
import org.tentackle.common.Constants;
import org.tentackle.common.TentackleRuntimeException;
import org.tentackle.reflect.Interceptable;
import org.tentackle.reflect.Interception.Type;
import org.tentackle.reflect.NotInterceptable;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.NoType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.ElementFilter;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import javax.tools.Diagnostic.Kind;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Set;

/**
 * Processor for annotations annotated with {@code @Interception}.<br>
 * Provides annotation checking during compilation and IDE editing.
 *
 * @author harald
 */
public abstract class AbstractInterceptorAnnotationProcessor extends AbstractProcessor {

  private final Type type;
  private final Set<String> annotationTypes;
  private Types typeUtils;
  private Elements elementUtils;
  private SuperTypeVisitor typeVisitor;


  /**
   * Creates an interception annotation processor.
   *
   * @param type the interceptor type
   */
  public AbstractInterceptorAnnotationProcessor(Type type) {
    this.type = type;
    annotationTypes = loadAnnotationTypes();
  }

  @Override
  public SourceVersion getSupportedSourceVersion() {
    return SourceVersion.latest();
  }

  @Override
  public Set<String> getSupportedAnnotationTypes() {
    return annotationTypes;
  }

  @Override
  public synchronized void init(ProcessingEnvironment processingEnv) {
    super.init(processingEnv);
    typeVisitor = new SuperTypeVisitor(processingEnv, true);
    typeUtils = processingEnv.getTypeUtils();
    elementUtils = processingEnv.getElementUtils();
  }

  @Override
  public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
    for (TypeElement annotation: annotations) {
      processAnnotation(annotation, roundEnv);
    }
    return true;    // claimed
  }


  private void processAnnotation(TypeElement annotationType, RoundEnvironment roundEnv) {

    outer:
    for (Element element : roundEnv.getElementsAnnotatedWith(annotationType)) {
      if (element.getKind().equals(ElementKind.METHOD)) {    // only annotated methods

        checkNotInterceptable(annotationType, (ExecutableElement) element);

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
                continue outer;
              }
            }
            TypeMirror superType = classElement.getSuperclass();
            if (superType instanceof NoType ||
                superType.getKind() == TypeKind.NONE ||
                superType.getKind() == TypeKind.VOID ||
                superType.getKind() == TypeKind.MODULE ||
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


  /**
   * Verifies that the annotated method does not override a method marked
   * {@code @NotInterceptable}, i.e. a method the framework invokes directly,
   * bypassing the interceptor chain entirely.
   *
   * @param annotationType the interception annotation applied to the method
   * @param method the annotated method
   */
  private void checkNotInterceptable(TypeElement annotationType, ExecutableElement method) {
    ExecutableElement sealed;
    if (method.getAnnotation(NotInterceptable.class) != null) {
      sealed = method;    // marked and annotated on the very same declaration
    }
    else {
      TypeElement enclosingType = (TypeElement) method.getEnclosingElement();
      sealed = findNotInterceptable(method, enclosingType, enclosingType, new HashSet<>());
    }
    if (sealed != null) {
      processingEnv.getMessager().printMessage(
        Kind.ERROR,
        "@" + annotationType.getSimpleName() + " cannot be applied to '" + method +
        "': " + sealed.getEnclosingElement() + "." + sealed +
        " is @NotInterceptable and is never routed through the interceptor chain",
        method);
    }
  }

  private ExecutableElement findNotInterceptable(ExecutableElement method, TypeElement searchType,
                                                  TypeElement originatingType, Set<TypeElement> visited) {
    if (!visited.add(searchType)) {
      return null;    // already visited, e.g. diamond interface inheritance
    }
    for (ExecutableElement candidate : ElementFilter.methodsIn(searchType.getEnclosedElements())) {
      if (candidate.getAnnotation(NotInterceptable.class) != null &&
          elementUtils.overrides(method, candidate, originatingType)) {
        return candidate;
      }
    }
    for (TypeMirror superType : typeUtils.directSupertypes(searchType.asType())) {
      if (superType.getKind() == TypeKind.DECLARED) {
        ExecutableElement found = findNotInterceptable(
          method, (TypeElement) typeUtils.asElement(superType), originatingType, visited);
        if (found != null) {
          return found;
        }
      }
    }
    return null;
  }


  private Set<String> loadAnnotationTypes() {
    Set<String> types = new HashSet<>();
    try {
      // we can't use ServiceFinder because it's not in META-INF / the classpath of the compiler
      Enumeration<URL> urls = getClass().getClassLoader().getResources(Constants.DEFAULT_SERVICE_PATH + getClass().getName());
      while (urls.hasMoreElements()) {
        URL url = urls.nextElement();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(url.openStream(), StandardCharsets.UTF_8))) {
          String line;
          while ((line = reader.readLine()) != null) {
            line = line.trim();
            if (!line.isEmpty() && !line.startsWith("#")) {
              types.add(line);
            }
          }
        }
      }
      return types;
    }
    catch (IOException iox) {
      throw new TentackleRuntimeException("loading annotation types failed", iox);
    }
  }

}
