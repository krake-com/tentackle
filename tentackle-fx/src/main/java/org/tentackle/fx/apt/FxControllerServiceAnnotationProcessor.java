/*
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


package org.tentackle.fx.apt;

import java.util.Set;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.tools.Diagnostic;
import org.tentackle.apt.AbstractServiceAnnotationProcessor;
import org.tentackle.common.AnnotationProcessor;

import static javax.lang.model.SourceVersion.RELEASE_9;

/**
 * Annotation processor for the {@code @FxControllerService} annotation.<br>
 *
 * @author harald
 */
@SupportedAnnotationTypes("org.tentackle.fx.FxControllerService")
@SupportedSourceVersion(RELEASE_9)
@AnnotationProcessor
public class FxControllerServiceAnnotationProcessor extends AbstractServiceAnnotationProcessor {


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


  @Override
  protected void processClass(Element element) {
    super.processClass(element);
    if (!verifyConstructor(element, noArgsVisitor)) {
      processingEnv.getMessager().printMessage(
          Diagnostic.Kind.ERROR,
          "class " + element + " needs a no-args constructor", element);
    }
    verifyImplements(element, "org.tentackle.fx.FxController");
  }

}
