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

package org.tentackle.fx.apt;

import org.tentackle.apt.AbstractServiceAnnotationProcessor;
import org.tentackle.common.AnnotationProcessor;
import org.tentackle.fx.FxControllerService;

import javax.annotation.processing.SupportedAnnotationTypes;
import javax.lang.model.element.Element;
import javax.lang.model.type.ExecutableType;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.type.TypeVisitor;
import javax.lang.model.util.SimpleTypeVisitor8;
import javax.tools.Diagnostic;
import java.util.List;

/**
 * Annotation processor for the {@code @FxControllerService} annotation.<br>
 * Enforces the implementation of {@code FxController} and a no-args constructor or, if the controller
 * is instantiated without FXML and with a resource bundle, a constructor taking the {@link java.util.ResourceBundle}.
 *
 * @author harald
 */
@SupportedAnnotationTypes("org.tentackle.fx.FxControllerService")
@AnnotationProcessor
public class FxControllerServiceAnnotationProcessor extends AbstractServiceAnnotationProcessor {

  /**
   * Creates the annotation processor.
   */
  public FxControllerServiceAnnotationProcessor() {
    // see -Xlint:missing-explicit-ctor since Java 16
  }


  @Override
  protected void processClass(Element element) {
    super.processClass(element);
    FxControllerService fxControllerService = element.getAnnotation(FxControllerService.class);
    if (!FxControllerService.FXML_NONE.equals(fxControllerService.url()) ||
        FxControllerService.RESOURCES_NONE.equals(fxControllerService.resources())) {
      // loaded by FXML or instantiated without resource bundle
      if (!verifyConstructor(element, noArgsVisitor)) {
        processingEnv.getMessager().printMessage(
            Diagnostic.Kind.ERROR,
            "class " + element + " needs a no-args constructor", element);
      }
    }
    else {
      // programmatically instantiated with resource bundle
      if (!verifyConstructor(element, resourceBundleVisitor)) {
        processingEnv.getMessager().printMessage(
            Diagnostic.Kind.ERROR,
            "class " + element + " needs constructor (ResourceBundle)", element);
      }
    }
    verifyImplements(element, "org.tentackle.fx.FxController");
  }

  private final TypeVisitor<Boolean, Void> resourceBundleVisitor = new SimpleTypeVisitor8<>() {
    @Override
    public Boolean visitExecutable(ExecutableType t, Void v) {
      List<? extends TypeMirror> typeList = t.getParameterTypes();
      return typeList.size() == 1 &&
             // m.accept does not work with ResourceBundle.class
             typeUtils.erasure(typeList.get(0)).toString().equals("java.util.ResourceBundle");
    }
  };

}
