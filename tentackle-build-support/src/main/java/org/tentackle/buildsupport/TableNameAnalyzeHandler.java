/*
 * Tentackle - https://tentackle.org.
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

import org.tentackle.common.Constants;
import org.tentackle.common.StringHelper;

import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.tools.Diagnostic;
import java.io.IOException;
import java.util.Map;

/**
 * Handler for the {@code @TableName} annotation.
 * <p>
 * Although a mapped service, we need an extra implementation because of mapSchema=true|false.
 *
 * @author harald
 */
public class TableNameAnalyzeHandler extends AbstractAnalyzeHandler {

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

              String methodName = entry.getKey().getSimpleName().toString();
              switch (methodName) {

                case "value":
                  tableName = entry.getValue().toString();
                  break;

                case "mapSchema":
                  mapSchema = Boolean.valueOf(entry.getValue().toString());
                  break;

                case "prefix":
                  prefix = StringHelper.parseString(entry.getValue().toString());
                  if (StringHelper.isAllWhitespace(prefix)) {
                    prefix = null;
                  }
                  break;

                default:
                  print(Diagnostic.Kind.WARNING, "unknown method '" + methodName + "' in annotation " + annotationType, element);
              }
            }
            break;
          }
        }

        if (tableName == null) {
          print(Diagnostic.Kind.ERROR, "no value method in @" + StringHelper.lastAfter(annotationType.getClass().getName(), '.'), element);
        }
        else {
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
            print(Diagnostic.Kind.NOTE, "appending '" + configuration + "' to " + relativeName, element);
          }

          try {
            processor.getResourceManager(processor.getServiceDir()).getWriter(relativeName).println(configuration);
            processor.getResourceManager(processor.getAnalyzeDir()).getWriter(
                servicingClassName.replace('.', '/') + "/service.log").println(annoName + ": " + configuration);
          }
          catch (IOException ex) {
            print(Diagnostic.Kind.ERROR, "file generation error: " + ex, element);
            return;
          }
        }
      }
      else {
        print(Diagnostic.Kind.ERROR, "annotated element '" + element + "' is not a class or interface", element);
      }
    }
  }

}
