/*
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

package org.tentackle.maven;

import java.lang.annotation.Annotation;
import java.util.Map;
import java.util.Set;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedOptions;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.tools.Diagnostic;
import org.tentackle.buildsupport.AbstractTentackleProcessor;


/**
 * Validations checker processor.
 *
 * @author harald
 */
@SupportedSourceVersion(SourceVersion.RELEASE_8)
@SupportedOptions("verbosity")
@SupportedAnnotationTypes("*")
public class CheckValidationsProcessor extends AbstractTentackleProcessor {

  private static final String VALIDATION_CLASSNAME = "org.tentackle.validate.Validation";

  private ClassLoader processingClassLoader;
  private int errors;

  /**
   * Gets the error count.
   *
   * @return the errors
   */
  public int getErrors() {
    return errors;
  }

  /**
   * Gets the classloader to be used instead of the default.
   *
   * @return the classloader, never null (fallback to current classloader)
   */
  public ClassLoader getProcessingClassLoader() {
    return processingClassLoader == null ? getClass().getClassLoader() : processingClassLoader;
  }

  /**
   * Sets the classloader to be used instead of the default.
   *
   * @param processingClassLoader the classloader, null if default
   */
  public void setProcessingClassLoader(ClassLoader processingClassLoader) {
    this.processingClassLoader = processingClassLoader;
  }

  @Override
  public synchronized void init(ProcessingEnvironment processingEnvironment) {
    super.init(processingEnvironment);
    errors = 0;
  }

  @Override
  public boolean process(Set<? extends TypeElement> annotationTypes, RoundEnvironment roundEnv) {
    boolean claimed = false;
    if (!roundEnv.processingOver()) {
      for (TypeElement annotationType: annotationTypes) {
        if (!annotationType.toString().startsWith("java.lang.")) {
          for (AnnotationMirror mirror : annotationType.getAnnotationMirrors()) {
            if (mirror.getAnnotationType().toString().equals(VALIDATION_CLASSNAME)) {
              // annotated with @Validation
              if (isDebugLogging()) {
                processingEnv.getMessager().printMessage(
                        Diagnostic.Kind.NOTE, "processing annotation " + annotationType);
              }
              processAnnotation(annotationType, mirror, roundEnv);
              claimed = true;
            }
          }
        }
      }
    }
    return claimed;
  }

  /**
   * Processes the annotation.
   *
   * @param annotationType the annotation type
   * @param roundEnv the processing environment
   */
  @SuppressWarnings("unchecked")
  public void processAnnotation(TypeElement annotationType, AnnotationMirror mirror, RoundEnvironment roundEnv) {

    Class<Annotation> annotationClass;
    try {
      annotationClass = (Class<Annotation>) processingClassLoader.loadClass(annotationType.toString());
    }
    catch (ClassNotFoundException nfe) {
      getProcessingEnvironment().getMessager().printMessage(
                  Diagnostic.Kind.ERROR, "cannot load annotation class " + annotationType + ": " + nfe);
      return;
    }

    for (Element element : roundEnv.getElementsAnnotatedWith(annotationType)) {
      if (isDebugLogging()) {
        getProcessingEnvironment().getMessager().printMessage(
                Diagnostic.Kind.NOTE, "found " + element + " in " +
                        element.getEnclosingElement() + " annotated with " + annotationClass.getName());
      }

      for (Annotation annotation: element.getAnnotationsByType(annotationClass)) {
        Object validator = null;

        Map<? extends ExecutableElement, ? extends AnnotationValue> annotationParameters = mirror.getElementValues();
        for (Map.Entry<? extends ExecutableElement, ? extends AnnotationValue> entry : annotationParameters.entrySet()) {
          ExecutableElement elem = entry.getKey();
          AnnotationValue anno = entry.getValue();
          String implName = anno.toString();
          if (implName.endsWith(".class")) {
            implName = implName.substring(0, implName.length() - 6);
          }

          try {
            validator = getProcessingClassLoader().loadClass(implName);
            break;
          }
          catch (ClassNotFoundException nx) {
            getProcessingEnvironment().getMessager().printMessage(
                  Diagnostic.Kind.ERROR, "cannot create validator " + implName);
            errors++;
          }
        }

        if (validator != null) {
            // todo: we need tentackle-core in the mojo's classpath
            // --> new tentackle-plugin (tentackle-test ?)
        }
      }

    }

  }
}
