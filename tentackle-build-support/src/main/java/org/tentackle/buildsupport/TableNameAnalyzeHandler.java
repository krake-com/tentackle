/*
 * Tentackle - http://www.tentackle.org.
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
 *
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
import javax.tools.Diagnostic;
import org.tentackle.common.BasicStringHelper;
import org.tentackle.common.Constants;

/**
 * Handler for the @TableName annotation.
 * <p>
 * Although a mapped service, we need an extra implementation because of mapSchema=true|false.
 *
 * @author harald
 */
public class TableNameAnalyzeHandler extends AbstractAnalyzeHandler {

  private AnalyzeProcessor processor;     // the annotation processor

  @Override
  public void setProcessor(AbstractTentackleProcessor processor) {
    this.processor = (AnalyzeProcessor) processor;
  }

  @Override
  public AnalyzeProcessor getProcessor() {
    return processor;
  }

  @Override
  @SuppressWarnings("unchecked")
  public void processAnnotation(TypeElement annotationType, RoundEnvironment roundEnv) {

    String annoName = annotationType.toString();

    for (Element element : roundEnv.getElementsAnnotatedWith(annotationType)) {
      if (element.getKind().equals(ElementKind.ANNOTATION_TYPE)) {
        continue;   // ignore annotated annotations (such as in tentackle-core)
      }
      if (element.getKind().equals(ElementKind.CLASS) ||
          element.getKind().equals(ElementKind.INTERFACE)) {    // only annotated classes or interfaces

        String servicingClassName = element.toString();   // annotated class, the provider

        String tableName = null;
        boolean mapSchema = false;
        String prefix = null;
        for (AnnotationMirror annoMirror: element.getAnnotationMirrors()) {
          if (annoMirror.getAnnotationType().toString().equals(annoName)) {
            for (Map.Entry<? extends ExecutableElement, ? extends AnnotationValue> entry :
                    annoMirror.getElementValues().entrySet()) {

              switch (entry.getKey().getSimpleName().toString()) {

                case "value":
                  tableName = entry.getValue().toString();
                  break;

                case "mapSchema":
                  mapSchema = Boolean.valueOf(entry.getValue().toString());
                  break;

                case "prefix":
                  prefix = entry.getValue().toString();
                  if (prefix != null) {
                    prefix = BasicStringHelper.parseString(prefix);
                    if (BasicStringHelper.isAllWhitespace(prefix)) {
                      prefix = null;
                    }
                  }
                  break;
              }
            }

            if (tableName == null) {
              processor.getProcessingEnvironment().getMessager().printMessage(
                      Diagnostic.Kind.ERROR, "no value method in annotation " + annotationType, element);
              return;
            }
          }
          break;
        }

        if (tableName == null) {
          processor.getProcessingEnvironment().getMessager().printMessage(
                  Diagnostic.Kind.ERROR, "internal logic error");
        }

        // notice: tablename is a string enclosed in doublequotes!

        if (mapSchema) {
          tableName = tableName.replace('.', '_');
        }
        if (prefix != null) {
          tableName = tableName.substring(0, 1) + prefix + tableName.substring(1);
        }

        String configuration = servicingClassName + " = " + tableName;
        String relativeName = Constants.MAPPED_SERVICE_PATH + annoName;

        if (processor.isDebugLogging()) {
          processor.getProcessingEnvironment().getMessager().printMessage(
                  Diagnostic.Kind.NOTE, "appending '" + configuration + "' to " + relativeName);
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
                  Diagnostic.Kind.ERROR, "file generation error: " + ex);
          return;
        }
      }
      else {
        processor.getProcessingEnvironment().getMessager().printMessage(
                  Diagnostic.Kind.ERROR, "annotated element '" + element + "' is not a class or interface", element);
      }
    }
  }

}
