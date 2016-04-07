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
import javax.tools.Diagnostic;
import org.tentackle.common.Constants;


/**
 * Handler to extract the name of the resource bundle from
 * the class annotated with &#64;Bundle.
 *
 * @author harald
 */
public class BundleHandler extends AbstractAnalyzeHandler {

  public static final String PROPS_EXTENSION = ".properties";

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

        String bundleName = getBundleName(element, annotationName);
        if (bundleName != null) {

          String clsName = element.toString();
          if (!bundleName.equals(clsName)) {
            // not the same as the classname: append original classname
            bundleName += " = " + clsName;
          }

          String fileName = Constants.BUNDLE_SERVICE_PATH + annotationName;

          if (processor.isDebugLogging()) {
            processor.getProcessingEnvironment().getMessager().printMessage(
                    Diagnostic.Kind.NOTE, "appending '" + bundleName + "' to " + fileName);
          }

          try {
            processor.getResourceManager(processor.getServiceDir()).getWriter(fileName).println(bundleName);
          }
          catch (IOException ex) {
            processor.getProcessingEnvironment().getMessager().printMessage(
                    Diagnostic.Kind.ERROR, "file generation error: " + ex);
            return;
          }
        }
      }
      else {
        processor.getProcessingEnvironment().getMessager().printMessage(
                  Diagnostic.Kind.ERROR, "annotated element '" + element + "' is not a class or interface", element);
      }
    }
  }


  /**
   * Gets the bundlename from the annotated element.
   *
   * @param element the bundle name
   * @param annotationName the annotation name (e.g. ...Bundle)
   * @return the bundle name, null if no bundle
   */
  protected String getBundleName(Element element, String annotationName) {

    String bundleName = element.toString();   // annotated class, same as the name of the bundle (default)

    // check if bundle name is overridden
    // we must use mirrors because class isn't loaded already and probably cannot be loaded at all
    for (AnnotationMirror annoMirror : element.getAnnotationMirrors()) {
      if (annoMirror.getAnnotationType().toString().equals(annotationName)) {
        for (Map.Entry<? extends ExecutableElement, ? extends AnnotationValue> entry :
                annoMirror.getElementValues().entrySet()) {
          if ("value".equals(entry.getKey().getSimpleName().toString())) {
            bundleName = xlateBundleName(entry.getValue().toString());
            break;
          }
        }
        break;
      }
    }

    return bundleName;
  }


  /**
   * Translates the bundle name to a standard format.
   *
   * @param bundleName the name
   * @return the standardized name
   */
  protected String xlateBundleName(String bundleName) {
    if (bundleName != null && !bundleName.isEmpty()) {
      if (bundleName.length() > 2 && bundleName.charAt(0) == '"' && bundleName.charAt(bundleName.length() - 1) == '"') {
        // strip quotes
        bundleName = bundleName.substring(1, bundleName.length() - 1);
      }
      if (bundleName.endsWith(PROPS_EXTENSION)) {
        // cut trailing ".properties"
        bundleName = bundleName.substring(0, bundleName.length() - PROPS_EXTENSION.length());
      }
      if (bundleName.charAt(0) == '/') {
        bundleName = bundleName.substring(1);   // cut leading slash
      }
      // transform to classname format
      bundleName = bundleName.replace('/', '.');
    }
    return bundleName;
  }

}
