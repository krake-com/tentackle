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

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.tools.Diagnostic.Kind;
import java.util.Map;
import java.util.Set;

import static javax.lang.model.SourceVersion.RELEASE_12;


/**
 * Processor for the {@code @Interception} annotation.<br>
 * Provides annotation checking during compilation and IDE editing.
 *
 * @author harald
 */
@SupportedAnnotationTypes("org.tentackle.reflect.Interception")
@SupportedSourceVersion(RELEASE_12)
@AnnotationProcessor
public class InterceptionAnnotationProcessor extends AbstractProcessor {

  private static final String ANNO_NAME = Interception.class.getName();


  @Override
  public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
    for (TypeElement annotation: annotations) {
      processAnnotation(annotation, roundEnv);
    }
    return true;    // claimed
  }


  private void processAnnotation(TypeElement annotationType, RoundEnvironment roundEnv) {

    for (Element element : roundEnv.getElementsAnnotatedWith(annotationType)) {
      if (element.getKind().equals(ElementKind.ANNOTATION_TYPE)) {
        for (AnnotationMirror annoMirror : element.getAnnotationMirrors()) {
          if (annoMirror.getAnnotationType().toString().equals(ANNO_NAME)) {
            // verify that only one of the three args is given
            int validCount = 0;
            for (Map.Entry<? extends ExecutableElement, ? extends AnnotationValue> entry :
                annoMirror.getElementValues().entrySet()) {
              String implName = entry.getKey().getSimpleName().toString();
              String implValue = entry.getValue().toString();
              if ("implementedBy".equals(implName) && !ANNO_NAME.equals(implValue) ||
                  "implementedByName".equals(implName) && !implValue.isEmpty() ||
                  "implementedByService".equals(implName) && !implValue.isEmpty()) {
                validCount++;
              }
            }
            if (validCount > 1) {
              processingEnv.getMessager().printMessage(
                  Kind.ERROR,
                  "only one of implementedBy, implementedByName or implementedByService must be specified",
                  element);
            }
          }
        }
      }
      else {
        processingEnv.getMessager().printMessage(
                  Kind.ERROR,
                  "annotated element '" + element + "' is not an annotation",
                  element);
      }
    }
  }

}
