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
import org.tentackle.fx.FxComponent;

import javax.annotation.processing.SupportedAnnotationTypes;
import javax.lang.model.element.Element;
import javax.lang.model.type.ExecutableType;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.type.TypeVisitor;
import javax.lang.model.util.SimpleTypeVisitor8;
import javax.tools.Diagnostic;
import java.util.List;

/**
 * Annotation processor for the {@code @ValueTranslatorService} annotation.<br>
 * Enforces the implementation of {@code ValueTranslator} and one of the following constructors:
 *
 * <ul>
 *   <li>({@link FxComponent})</li>
 *   <li>({@link FxComponent}, Class)</li>
 * </ul>
 *
 * @author harald
 */
@SupportedAnnotationTypes("org.tentackle.fx.ValueTranslatorService")
@AnnotationProcessor
public class ValueTranslatorServiceAnnotationProcessor extends AbstractServiceAnnotationProcessor {

  /**
   * Creates the annotation processor.
   */
  public ValueTranslatorServiceAnnotationProcessor() {
    // see -Xlint:missing-explicit-ctor since Java 16
  }


  @Override
  protected void processClass(Element element) {
    super.processClass(element);
    if (!verifyConstructor(element, componentVisitor) &&
        !verifyConstructor(element, componentClassVisitor)) {
      processingEnv.getMessager().printMessage(
          Diagnostic.Kind.ERROR,
          "class " + element + " needs constructor (FxComponent) or (FxComponent, Class)", element);
    }
    verifyImplements(element, "org.tentackle.fx.ValueTranslator");
  }


  private final TypeVisitor<Boolean, Void> componentVisitor = new SimpleTypeVisitor8<>() {
    @Override
    public Boolean visitExecutable(ExecutableType t, Void v) {
      List<? extends TypeMirror> typeList = t.getParameterTypes();
      return typeList.size() == 1 &&
              acceptTypeVisitor(typeList.get(0), FxComponent.class);
    }
  };

  private final TypeVisitor<Boolean, Void> componentClassVisitor = new SimpleTypeVisitor8<>() {
    @Override
    public Boolean visitExecutable(ExecutableType t, Void v) {
      List<? extends TypeMirror> typeList = t.getParameterTypes();
      return typeList.size() == 2 &&
              acceptTypeVisitor(typeList.get(0), FxComponent.class) &&
              // m.accept does not work with Class.class
              typeUtils.erasure(typeList.get(1)).toString().equals("java.lang.Class");
    }
  };

}
