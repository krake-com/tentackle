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

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.tools.Diagnostic.Kind;


/**
 * Handler for the @RemoteMethod annotation.
 *
 * @author harald
 */
public class RemoteMethodAnalyzeHandler extends AbstractAnalyzeHandler {

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

    Class<Annotation> annotationClass;
    try {
      annotationClass = (Class<Annotation>) Class.forName(annotationType.toString());
    }
    catch (ClassNotFoundException nfe) {
      processor.getProcessingEnvironment().getMessager().printMessage(
                  Kind.ERROR, "cannot load annotation class " + annotationType + ": " + nfe, annotationType);
      return;
    }

    for (Element element : roundEnv.getElementsAnnotatedWith(annotationType)) {
      if (element.getKind().equals(ElementKind.ANNOTATION_TYPE)) {
        continue;   // ignore annotated annotations (such as in tentackle-core)
      }
      if (element.getKind().equals(ElementKind.METHOD)) {
        ExecutableElement methodElement = (ExecutableElement) element;
        Element enclosingElement = methodElement.getEnclosingElement();
        if (enclosingElement != null && enclosingElement.getKind().equals(ElementKind.CLASS)) {
          String className = enclosingElement.toString();
          Annotation annotation = methodElement.getAnnotation(annotationClass);
          CharSequence methodName = methodElement.getSimpleName();    // default
          try {
            Method valueMethod = annotationClass.getDeclaredMethod("value");
            String value = (String) valueMethod.invoke(annotation);
            if (!value.startsWith("[")) {
              methodName = value;
            }
          }
          catch (Exception nme) {
            processor.getProcessingEnvironment().getMessager().printMessage(
                  Kind.ERROR, "no value method for annotation " + annotationClass, element);
            return;
          }
          if (getProcessor().isDebugLogging()) {
            processor.getProcessingEnvironment().getMessager().printMessage(
                  Kind.NOTE, "found annotated method " + methodName + " in class " + className, element);
          }
          try {
            // create info object
            RemoteMethodInfo info = new RemoteMethodInfo(getProcessor().getProcessingEnvironment(), methodElement);
            File outputDir = getDirectory(getProcessor().getAnalyzeDir(), className);
            outputDir.mkdirs();
            File outputFile = new File(outputDir, methodName + RemoteMethodInfo.INFO_FILE_EXTENSION);
            try (PrintWriter pw = new PrintWriter(
                                    new BufferedWriter(
                                      new OutputStreamWriter(
                                        new FileOutputStream(outputFile), Settings.encodingCharset)))) {
              info.write(pw);
            }
            if (getProcessor().isDebugLogging()) {
              processor.getProcessingEnvironment().getMessager().printMessage(
                  Kind.NOTE, "created " + outputFile, element);
            }
          }
          catch (Exception ex) {
            processor.getProcessingEnvironment().getMessager().printMessage(
                  Kind.ERROR, "extracting AnalyzeInfo failed: " + ex, element);
          }
        }
        else {
          processor.getProcessingEnvironment().getMessager().printMessage(Kind.WARNING,
                  "enclosing element '" + enclosingElement + "' of annotated method '" +
                  methodElement.getSimpleName() + "' is not a class -> ignored", element);
        }
      }
      else {
        processor.getProcessingEnvironment().getMessager().printMessage(
                  Kind.WARNING, "annotated element '" + element + "' is not a method -> ignored", element);
      }
    }
  }

}
