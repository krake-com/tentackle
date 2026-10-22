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

package org.tentackle.maven.plugin.check;

import org.tentackle.buildsupport.AbstractTentackleProcessor;
import org.tentackle.validate.RepeatableValidation;
import org.tentackle.validate.Validation;
import org.tentackle.validate.Validator;

import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedOptions;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.tools.Diagnostic;
import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;


/**
 * Validations checker processor.<br>
 * Creates a validator for each occurrence of a validation annotation, including repeated ones.
 * Validators that cannot be created are reported as errors via the {@code Messager}.
 *
 * @author harald
 */
@SupportedOptions("verbosity")
@SupportedAnnotationTypes("*")
public class CheckValidationsProcessor extends AbstractTentackleProcessor {

  /**
   * Validator entry.<br>
   * Holds the validator and the class where the annotation was found.
   *
   * @param validator the validator instance
   * @param className the binary name of the enclosing class of the annotation, i.e. nested classes
   *        are separated by a {@code '$'} so that the class can be loaded
   */
  public record ValidatorEntry(Validator validator, String className) {

    @Override
    public String toString() {
      return validator.getClass().getName() + " in " + className;
    }
  }


  private static final String VALIDATION_CLASSNAME = Validation.class.getName();

  private static final String REPEATABLE_VALIDATION_CLASSNAME = RepeatableValidation.class.getName();

  private ClassLoader processingClassLoader;
  private int errors;
  private final List<ValidatorEntry> validators;

  /**
   * The elements already processed per validation annotation.<br>
   * A repeated annotation is offered to the processor as its container, but the compiler may
   * offer the repeated annotation itself as well, which would create the validators twice.
   */
  private final Map<String, Set<Element>> processedElements;


  /**
   * Creates the processor.
   */
  public CheckValidationsProcessor() {
    validators = new ArrayList<>();
    processedElements = new HashMap<>();
  }


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

  /**
   * Gets the annotation type a {@code Class}-valued annotation mirror refers to.
   *
   * @param mirror the annotation mirror
   * @return the annotation type, null if the mirror has no such value
   */
  private TypeElement getAnnotationClassValue(AnnotationMirror mirror) {
    for (AnnotationValue value : mirror.getElementValues().values()) {
      if (value.getValue() instanceof DeclaredType declaredType &&
          declaredType.asElement() instanceof TypeElement typeElement) {
        return typeElement;
      }
    }
    return null;
  }

  /**
   * Gets the {@code @Validation} mirror of an annotation type.
   *
   * @param annotationType the annotation type
   * @return the mirror, null if the type is not a validation annotation
   */
  private AnnotationMirror getValidationMirror(TypeElement annotationType) {
    for (AnnotationMirror mirror : annotationType.getAnnotationMirrors()) {
      if (mirror.getAnnotationType().toString().equals(VALIDATION_CLASSNAME)) {
        return mirror;
      }
    }
    return null;
  }

  @Override
  public boolean process(Set<? extends TypeElement> annotationTypes, RoundEnvironment roundEnv) {
    boolean claimed = false;
    if (!roundEnv.processingOver()) {
      for (TypeElement annotationType: annotationTypes) {
        if (!annotationType.toString().startsWith("java.lang.")) {
          for (AnnotationMirror mirror : annotationType.getAnnotationMirrors()) {
            String mirrorType = mirror.getAnnotationType().toString();
            if (mirrorType.equals(VALIDATION_CLASSNAME)) {
              // annotated with @Validation
              if (isDebugLogging()) {
                processingEnv.getMessager().printMessage(
                        Diagnostic.Kind.NOTE, "processing annotation " + annotationType);
              }
              processAnnotation(annotationType, annotationType, mirror, roundEnv);
              claimed = true;
            }
            else if (mirrorType.equals(REPEATABLE_VALIDATION_CLASSNAME)) {
              // annotated with @RepeatableValidation: a repeated validation annotation is replaced
              // by its container, so the round offers the container only. The validators are those
              // of the repeated annotation it holds.
              TypeElement repeatedType = getAnnotationClassValue(mirror);
              AnnotationMirror validationMirror = repeatedType == null ? null : getValidationMirror(repeatedType);
              if (validationMirror != null) {
                if (isDebugLogging()) {
                  processingEnv.getMessager().printMessage(
                          Diagnostic.Kind.NOTE, "processing repeated annotation " + repeatedType +
                                                " contained in " + annotationType);
                }
                processAnnotation(annotationType, repeatedType, validationMirror, roundEnv);
                claimed = true;
              }
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
   * @param declaredType the annotation type as it appears in the sources, i.e. the container for
   *        a repeated annotation
   * @param annotationType the validation annotation type, i.e. the repeated annotation itself
   * @param mirror the {@code @Validation} mirror of the validation annotation type
   * @param roundEnv the round environment
   */
  @SuppressWarnings("unchecked")
  public void processAnnotation(TypeElement declaredType, TypeElement annotationType,
                                AnnotationMirror mirror, RoundEnvironment roundEnv) {

    Class<Annotation> annotationClass;
    try {
      annotationClass = (Class<Annotation>) getProcessingClassLoader().loadClass(annotationType.toString());
    }
    catch (ClassNotFoundException nfe) {
      getProcessingEnvironment().getMessager().printMessage(
                  Diagnostic.Kind.ERROR, "cannot load annotation class " + annotationType + ": " + nfe);
      return;
    }

    Set<Element> processed = processedElements.computeIfAbsent(annotationType.toString(), t -> new HashSet<>());

    for (Element element : roundEnv.getElementsAnnotatedWith(declaredType)) {
      if (!processed.add(element)) {
        continue;   // already processed via the repeated annotation or its container
      }
      if (isDebugLogging()) {
        getProcessingEnvironment().getMessager().printMessage(
                Diagnostic.Kind.NOTE, "found " + element + " in " +
                        element.getEnclosingElement() + " annotated with " + annotationClass.getName());
      }

      for (Annotation annotation: element.getAnnotationsByType(annotationClass)) {
        Class<?> validatorClass = null;

        Map<? extends ExecutableElement, ? extends AnnotationValue> annotationParameters = mirror.getElementValues();
        for (Map.Entry<? extends ExecutableElement, ? extends AnnotationValue> entry : annotationParameters.entrySet()) {
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
            Validator validator = (Validator) validatorClass.getDeclaredConstructor().newInstance();
            validator.setAnnotation(annotation);
            Element enclosingClass = element;
            while (enclosingClass != null && !(enclosingClass instanceof TypeElement)) {
              enclosingClass = enclosingClass.getEnclosingElement();
            }
            // the binary name, not the canonical one: nested classes are separated by a '$',
            // or the class could not be loaded by the mojo (see CheckValidationsMojo#checkMessage)
            String enclosingName = enclosingClass == null ? "java.lang.Object"
                : getProcessingEnvironment().getElementUtils().getBinaryName((TypeElement) enclosingClass).toString();
            validators.add(new ValidatorEntry(validator, enclosingName));
          }
          catch (ClassCastException cx) {
            getProcessingEnvironment().getMessager().printMessage(
                  Diagnostic.Kind.ERROR, validatorClass + " is not a validator");
            errors++;
          }
          catch (IllegalAccessException | InstantiationException |
                 InvocationTargetException | NoSuchMethodException ex) {
            getProcessingEnvironment().getMessager().printMessage(
                  Diagnostic.Kind.ERROR, "cannot create validator for " + validatorClass);
            errors++;
          }
        }
      }
    }
  }

}
