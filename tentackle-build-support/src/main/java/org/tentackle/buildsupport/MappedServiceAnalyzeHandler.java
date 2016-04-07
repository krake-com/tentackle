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


package org.tentackle.buildsupport;

import java.io.IOException;
import java.util.Map;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.tools.Diagnostic.Kind;
import org.tentackle.common.Constants;
import org.tentackle.common.MappedService;


/**
 * Handler for the @MappedService annotation.
 *
 * @author harald
 */
public class MappedServiceAnalyzeHandler extends AbstractAnalyzeHandler {

  /**
   * Classname of the {@link MappedService} annotation
   */
  private static final String ANNOTATION_NAME = MappedService.class.getName();


  private AnalyzeProcessor processor;     // the annotation processor

  @Override
  public void setProcessor(AnalyzeProcessor processor) {
    this.processor = processor;
  }

  @Override
  public AnalyzeProcessor getProcessor() {
    return processor;
  }

  @Override
  @SuppressWarnings("unchecked")
  public void processAnnotation(TypeElement annotationType, RoundEnvironment roundEnv) {

    String annotationName = annotationType.toString();

    for (Element element : roundEnv.getElementsAnnotatedWith(annotationType)) {
      if (element.getKind().equals(ElementKind.ANNOTATION_TYPE)) {
        continue;   // ignore annotated annotations (such as in tentackle-core)
      }
      if (element.getKind().equals(ElementKind.CLASS) ||
          element.getKind().equals(ElementKind.INTERFACE)) {    // only annotated classes or interfaces

        String servicingClassName = element.toString();   // annotated class, the provider

        // we must use mirrors because class isn't loaded already and probably cannot be loaded at all

        // determine the service which is configured by @MappedService
        String serviceClassName = null;
        for (AnnotationMirror annoMirror: annotationType.getAnnotationMirrors()) {
          if (annoMirror.getAnnotationType().toString().equals(ANNOTATION_NAME)) {
            // this is the correct annotation: find the value element
            for (Map.Entry<? extends ExecutableElement, ? extends AnnotationValue> entry :
                    annoMirror.getElementValues().entrySet()) {
              if ("value".equals(entry.getKey().getSimpleName().toString())) {
                serviceClassName = entry.getValue().toString();    // found
                // cut trailing .class
                int ndx = serviceClassName.lastIndexOf(".class");
                if (ndx >= 0) {
                  serviceClassName = serviceClassName.substring(0, ndx);
                }
                break;
              }
            }
            break;
          }
        }
        if (serviceClassName == null) {
          processor.getProcessingEnvironment().getMessager().printMessage(
                  Kind.ERROR, "no value method in annotation " + ANNOTATION_NAME, element);
          return;
        }

        // determine the serviced pdo class
        String servicedClassName = null;
        for (AnnotationMirror annoMirror: element.getAnnotationMirrors()) {
          if (annoMirror.getAnnotationType().toString().equals(annotationName)) {
            for (Map.Entry<? extends ExecutableElement, ? extends AnnotationValue> entry :
                    annoMirror.getElementValues().entrySet()) {
              if ("value".equals(entry.getKey().getSimpleName().toString())) {
                servicedClassName = entry.getValue().toString();    // found
                // cut trailing .class
                int ndx = servicedClassName.lastIndexOf(".class");
                if (ndx >= 0) {
                  servicedClassName = servicedClassName.substring(0, ndx);
                }
                break;
              }
            }
            break;
          }
        }
        if (servicedClassName == null) {
          processor.getProcessingEnvironment().getMessager().printMessage(
                  Kind.ERROR, "no value method in annotation " + serviceClassName, element);
          return;
        }

        String configuration = servicingClassName + " = " + servicedClassName;
        String relativeName = Constants.MAPPED_SERVICE_PATH + serviceClassName;

        if (processor.isDebugLogging()) {
          processor.getProcessingEnvironment().getMessager().printMessage(
                  Kind.NOTE, "appending '" + configuration + "' to " + relativeName);
        }

        try {
          processor.getResourceManager(processor.getServiceDir()).getWriter(relativeName).
                  println(configuration);
          processor.getResourceManager(processor.getAnalyzeDir()).getWriter(
                  servicingClassName.replace('.', '/') + "/service.log").
                  println(configuration);
        }
        catch (IOException ex) {
          processor.getProcessingEnvironment().getMessager().printMessage(
                  Kind.ERROR, "file generation error: " + ex);
          return;
        }
      }
      else {
        processor.getProcessingEnvironment().getMessager().printMessage(
                  Kind.ERROR, "annotated element '" + element + "' is not a class or interface", element);
      }
    }
  }


}
