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

package org.tentackle.apt.pdo;

import java.util.Set;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.tools.Diagnostic;
import org.tentackle.apt.AbstractServiceAnnotationProcessor;
import org.tentackle.common.Constants;

/**
 * Base class for DomainObjectServiceAnnotationProcessor and DomainOperationServiceAnnotationProcessor.
 *
 * @author harald
 */
public class AbstractDomainServiceAnnotationProcessor extends AbstractServiceAnnotationProcessor {

  @Override
  public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
    if (!roundEnv.processingOver()) {
      for (TypeElement type: annotations) {
        for (Element element : roundEnv.getElementsAnnotatedWith(type)) {
          processClass(element);
        }
      }
    }
    return true; // claim annotation
  }


  /**
   * Verifies that there is no stateful logic.
   *
   * @param element the element
   */
  public void verifyStatelessDomainLogic(Element element) {
    // don't allow fields in class as domain objects are stateless
    SuppressWarnings swAnno = element.getAnnotation(SuppressWarnings.class);
    if (swAnno != null) {
      for (String text : swAnno.value()) {
        if (text.equals(Constants.SUPPRESS_WARNINGS_STATEFUL_DOMAIN_LOGIC)) {
          return;   // class is annotated
        }
      }
    }

    for (Element elem: element.getEnclosedElements()) {
      if (elem.getKind() == ElementKind.FIELD &&
          !elem.getModifiers().contains(Modifier.STATIC) &&
          !elem.getModifiers().contains(Modifier.FINAL)) {
        boolean suppressed = false;
        swAnno = elem.getAnnotation(SuppressWarnings.class);
        if (swAnno != null) {
          for (String text: swAnno.value()) {
            if (text.equals(Constants.SUPPRESS_WARNINGS_STATEFUL_DOMAIN_LOGIC)) {
              suppressed = true;
              break;
            }
          }
        }
        if (!suppressed) {
          processingEnv.getMessager().printMessage(
            Diagnostic.Kind.WARNING,
            "PDO domain logic should be stateless! Consider making '" +
                    elem + "' final or annotate it with @SuppressWarnings(\"stateful-domain-logic\").", elem);
        }
      }
    }
  }

}
