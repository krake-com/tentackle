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
 * Handler for the annotations that denote interceptors.
 * <p>
 * Handles annotations annotated with {@code org.tentackle.reflect.Interception}.
 * Depending on the interception {@code type} ({@code ALL} (default), {@code PUBLIC} or {@code HIDDEN}),
 * the interceptor annotation is appended to
 * {@code META-INF/services/org.tentackle.apt.<Type>InterceptorAnnotationProcessor} in the service directory,
 * which registers it with the corresponding annotation processor of tentackle-core.
 * The processor name is logged to {@code <annotation>/service.log} in the analyze directory.
 *
 * @author harald
 */
public class InterceptionAnalyzeHandler extends AbstractAnalyzeHandler {

  // tentackle-build-support has no dependency to tentackle-core, hence strings only
  private static final String ANNO_NAME = "org.tentackle.reflect.Interception";
  private static final String ALL_TYPE = "ALL";
  private static final String PUBLIC_TYPE = "PUBLIC";
  private static final String HIDDEN_TYPE = "HIDDEN";
  private static final String ALL_APT = "org.tentackle.apt.AllInterceptorAnnotationProcessor";
  private static final String PUBLIC_APT = "org.tentackle.apt.PublicInterceptorAnnotationProcessor";
  private static final String HIDDEN_APT = "org.tentackle.apt.HiddenInterceptorAnnotationProcessor";

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
      if (element.getKind().equals(ElementKind.ANNOTATION_TYPE)) {    // only annotated annotations

        String interceptorName = element.toString();

        for (AnnotationMirror annoMirror: element.getAnnotationMirrors()) {
          String annoName = annoMirror.getAnnotationType().toString();
          if (ANNO_NAME.equals(annoName)) {
            String aptName = ALL_APT;   // default if type is missing
            for (Map.Entry<? extends ExecutableElement, ? extends AnnotationValue> entry :
                    annoMirror.getElementValues().entrySet()) {
              String argName = entry.getKey().getSimpleName().toString();

              if ("type".equals(argName)) {
                String typeStr = entry.getValue().toString();
                String processorName = null;
                if (typeStr.contains(ALL_TYPE)) {
                  processorName = ALL_APT;
                }
                else if (typeStr.contains(PUBLIC_TYPE)) {
                  processorName = PUBLIC_APT;
                }
                else if (typeStr.contains(HIDDEN_TYPE)) {
                  processorName = HIDDEN_APT;
                }
                if (processorName == null) {
                  print(Kind.ERROR, "invalid interceptor type '" + typeStr + "'", element);
                  return;
                }
                aptName = processorName;
              }
            }

            String relativeName = Constants.DEFAULT_SERVICE_PATH + aptName;

            if (processor.isDebugLogging()) {
              print(Kind.NOTE, "appending '" + interceptorName + "' to " + relativeName, element);
            }

            try {
              processor.getResourceManager(processor.getServiceDir()).getWriter(relativeName).println(interceptorName);
              processor.getResourceManager(processor.getAnalyzeDir())
                       .getWriter(interceptorName.replace('.', '/') + "/service.log").println(aptName);
            }
            catch (IOException ex) {
              print(Kind.ERROR, "file generation error: " + ex, element);
              return;
            }
          }
        }
      }
    }
  }

}
