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


package org.tentackle.buildsupport;

import org.tentackle.common.Constants;
import org.tentackle.common.Service;
import org.tentackle.common.ServiceName;
import org.tentackle.common.StringHelper;

import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.tools.Diagnostic.Kind;
import java.io.IOException;
import java.util.Map;


/**
 * Handler for the annotations that denote services.
 * <p>
 * Handles {@code @Service} and {@code @ServiceName}, used directly or as meta-annotations.
 * Appends the servicing class (the provider) to {@code META-INF/services/<serviced class>} in the service
 * directory, or to {@code META-INF/meta-services/<serviced class>} if {@code meta=true}.
 * In the latter case, the servicing class is taken from the element of the annotated annotation
 * named by {@code method}. In addition, the serviced class is logged to {@code <servicing class>/service.log}
 * in the analyze directory.
 *
 * @author harald
 */
public class ServiceAnalyzeHandler extends AbstractAnalyzeHandler {

  /**
   * Classname of the {@link Service} annotation
   */
  private static final String SERVICE_CLASS = Service.class.getName();

  /**
   * Classname of the {@link ServiceName} annotation
   */
  private static final String SERVICENAME_CLASS = ServiceName.class.getName();


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

        // determine the serviced class
        String servicedClassName = null;
        String methodName = "value";
        boolean meta = false;

        // check if this is meta-annotation annotated by @Service or @ServiceName
        for (AnnotationMirror annoMirror: annotationType.getAnnotationMirrors()) {
          String annoName = annoMirror.getAnnotationType().toString();
          if (SERVICE_CLASS.equals(annoName) || SERVICENAME_CLASS.equals(annoName)) {
            // this is the correct annotation: find the value element
            for (Map.Entry<? extends ExecutableElement, ? extends AnnotationValue> entry :
                    annoMirror.getElementValues().entrySet()) {
              String argName = entry.getKey().getSimpleName().toString();
              if ("value".equals(argName)) {
                servicedClassName = extractServicedClassName(entry.getValue());
              }
              else if ("meta".equals(argName)) {
                // if true: get servicing class from value of annotated annotation
                meta = Boolean.parseBoolean(entry.getValue().toString());
              }
              else if ("method".equals(argName)) {
                methodName = StringHelper.parseString(entry.getValue().toString());
              }
            }
            break;
          }
        }

        if (servicedClassName == null || meta) {
          // check if @Service or @ServiceName annotation used directly or get value from here if meta
          for (AnnotationMirror annoMirror: element.getAnnotationMirrors()) {
            if (annoMirror.getAnnotationType().toString().equals(annotationName)) {
              for (Map.Entry<? extends ExecutableElement, ? extends AnnotationValue> entry :
                      annoMirror.getElementValues().entrySet()) {
                if (methodName.equals(entry.getKey().getSimpleName().toString())) {
                  String name = extractServicedClassName(entry.getValue());
                  if (meta) {
                    servicingClassName = name;
                  }
                  else {
                    servicedClassName = name;
                  }
                  break;
                }
              }
              break;
            }
          }
        }
        if (servicedClassName == null) {
          print(Kind.ERROR, "no '" + methodName + "' method in @" + StringHelper.lastAfter(annotationName, '.'), element);
          continue;
        }

        // remove double quotes if service was given as a name (@ServiceName)
        if (servicedClassName.charAt(0) == '"' && servicedClassName.length() > 1) {
          servicedClassName = servicedClassName.substring(1, servicedClassName.length() - 1);
        }

        String relativeName = (meta ? Constants.META_SERVICE_PATH : Constants.DEFAULT_SERVICE_PATH) + servicedClassName;

        if (processor.isDebugLogging()) {
          print(Kind.NOTE, "appending '" + servicingClassName + "' to " + relativeName, element);
        }

        try {
          processor.getResourceManager(processor.getServiceDir()).getWriter(relativeName).println(servicingClassName);
          processor.getResourceManager(processor.getAnalyzeDir())
                   .getWriter(servicingClassName.replace('.', '/') + "/service.log").println(servicedClassName);
        }
        catch (IOException ex) {
          print(Kind.ERROR, "file generation error: " + ex, element);
          return;
        }
      }
      else {
        print(Kind.ERROR, "annotated element '" + element + "' is not a class or interface", element);
      }
    }
  }


  private String extractServicedClassName(AnnotationValue value) {
    String servicedClassName = value.toString();
    // cut trailing .class
    int ndx = servicedClassName.lastIndexOf(".class");
    if (ndx >= 0) {
      servicedClassName = servicedClassName.substring(0, ndx);
    }
    return servicedClassName;
  }
}
