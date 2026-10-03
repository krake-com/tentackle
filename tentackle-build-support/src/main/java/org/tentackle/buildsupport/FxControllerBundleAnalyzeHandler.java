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

import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import java.util.Map;


/**
 * Handler to extract the name of the resource bundle from
 * the class annotated with {@code @FxControllerService}.
 * <p>
 * The bundle name is given by the annotation's {@code resources} and defaults to the controller's classname.
 * {@code RESOURCES_NONE} (or the literal {@code "NONE"}) means that the controller has no bundle.
 *
 * @author harald
 */
public class FxControllerBundleAnalyzeHandler extends BundleAnalyzeHandler {

  private static final String NONE_LITERAL = "\"" + Constants.NAME_NONE + "\"";

  @Override
  protected String getBundleName(Element element, String annotationName) {

    String bundleName = element.toString();   // annotated class, same as the name of the bundle (default)

    // check if bundle name is overridden
    // we must use mirrors because class isn't loaded already and probably cannot be loaded at all
    for (AnnotationMirror annoMirror : element.getAnnotationMirrors()) {
      if (annoMirror.getAnnotationType().toString().equals(annotationName)) {
        for (Map.Entry<? extends ExecutableElement, ? extends AnnotationValue> entry : annoMirror.getElementValues().entrySet()) {
          if ("resources".equals(entry.getKey().getSimpleName().toString())) {
            bundleName = entry.getValue().toString();
            if (NONE_LITERAL.equals(bundleName) ||    // written as a string
                bundleName.endsWith("_NONE")) {       // no deps to fx here, hence not FxControllerService.RESOURCES_NONE
              bundleName = null; // explicitly no bundle
            }
            else {
              bundleName = xlateBundleName(bundleName);
            }
            break;
          }
        }
        break;
      }
    }

    return bundleName;
  }

}
