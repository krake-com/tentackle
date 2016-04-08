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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
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
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.tools.Diagnostic;
import org.tentackle.buildsupport.AbstractTentackleProcessor;
import org.tentackle.validate.Validator;


/**
 * Validations checker processor.
 *
 * @author harald
 */
@SupportedSourceVersion(SourceVersion.RELEASE_8)
@SupportedOptions("verbosity")
@SupportedAnnotationTypes("*")
public class CheckValidationsProcessor extends AbstractTentackleProcessor {

  public static class ValidatorEntry {

    private final Validator validator;
    private final String className;

    public ValidatorEntry(Validator validator, String className) {
      this.validator = validator;
      this.className = className;
    }

    public Validator getValidator() {
      return validator;
    }

    public String getClassName() {
      return className;
    }
  }

  private static final String VALIDATION_CLASSNAME = "org.tentackle.validate.Validation";

  private ClassLoader processingClassLoader;
  private int errors;
  private List<ValidatorEntry> validators;

  /**
   * Gets the error count.
   *
   * @return the error count
   */
  public int getErrors() {
    return errors;
  }

  /**
   * Gets the validators found.
   *
   * @return the validators
   */
  public List<ValidatorEntry> getValidators() {
    return validators;
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
    validators = new ArrayList<>();
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
   * @param mirror the annotation mirror
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
        Class<?> validatorClass = null;

        Map<? extends ExecutableElement, ? extends AnnotationValue> annotationParameters = mirror.getElementValues();
        for (Map.Entry<? extends ExecutableElement, ? extends AnnotationValue> entry : annotationParameters.entrySet()) {
          ExecutableElement elem = entry.getKey();
          AnnotationValue anno = entry.getValue();
          String implName = anno.toString();
          if (implName.endsWith(".class")) {
            implName = implName.substring(0, implName.length() - 6);
          }

          try {
            validatorClass = getProcessingClassLoader().loadClass(implName);
            break;
          }
          catch (ClassNotFoundException nx) {
            getProcessingEnvironment().getMessager().printMessage(
                  Diagnostic.Kind.ERROR, "cannot load validator implementation class " + implName);
            errors++;
          }
        }

        if (validatorClass != null) {
          try {
            Validator validator = (Validator) validatorClass.newInstance();
            validator.setAnnotation(annotation);
            Element enclosingClass = element;
            while (enclosingClass != null &&
                   enclosingClass.getKind() != ElementKind.INTERFACE &&
                   enclosingClass.getKind() != ElementKind.CLASS) {
              enclosingClass = enclosingClass.getEnclosingElement();
            }
            String enclosingName = enclosingClass == null ? "java.lang.Object" : enclosingClass.asType().toString();
            // cut generics
            int ndx = enclosingName.indexOf('<');
            if (ndx > 0) {
              enclosingName = enclosingName.substring(0, ndx);
            }
            validators.add(new ValidatorEntry(validator, enclosingName));
          }
          catch (ClassCastException cx) {
            getProcessingEnvironment().getMessager().printMessage(
                  Diagnostic.Kind.ERROR, validatorClass + " is not a validator");
            errors++;
          }
          catch (IllegalAccessException | InstantiationException ex) {
            getProcessingEnvironment().getMessager().printMessage(
                  Diagnostic.Kind.ERROR, "cannot create validator for " + validatorClass);
            errors++;
          }
        }
      }
    }
  }

}
