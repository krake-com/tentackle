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

package org.tentackle.fx.rdc.apt;

import org.tentackle.apt.AbstractServiceAnnotationProcessor;
import org.tentackle.common.AnnotationProcessor;
import org.tentackle.fx.rdc.PdoTreeCell;

import javax.annotation.processing.SupportedAnnotationTypes;
import javax.lang.model.element.Element;
import javax.lang.model.type.ExecutableType;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.type.TypeVisitor;
import javax.lang.model.util.SimpleTypeVisitor8;
import javax.tools.Diagnostic;
import java.util.List;

/**
 * Annotation processor for the {@code @PdoTreeContextMenuItemService} annotation.<br>
 * Verifies that the annotated class implements {@code PdoTreeContextMenuItem} and
 * enforces the implementation of the following constructors:
 *
 * <ul>
 *   <li>({@link PdoTreeCell})</li>
 * </ul>
 *
 * @author harald
 */
@SupportedAnnotationTypes("org.tentackle.fx.rdc.PdoTreeContextMenuItemService")
@AnnotationProcessor
public class PdoTreeContextMenuItemServiceAnnotationProcessor extends AbstractServiceAnnotationProcessor {

  /**
   * Creates the annotation processor.
   */
  public PdoTreeContextMenuItemServiceAnnotationProcessor() {
    // see -Xlint:missing-explicit-ctor since Java 16
  }


  @Override
  protected void processClass(Element element) {
    super.processClass(element);
    if (!verifyConstructor(element, pdoVisitor)) {
      processingEnv.getMessager().printMessage(
          Diagnostic.Kind.ERROR,
          "class " + element + " needs constructor (PdoTreeCell)", element);
    }
    verifyImplements(element, "org.tentackle.fx.rdc.PdoTreeContextMenuItem");
  }


  private final TypeVisitor<Boolean, Void> pdoVisitor = new SimpleTypeVisitor8<>() {
    @Override
    public Boolean visitExecutable(ExecutableType t, Void v) {
      List<? extends TypeMirror> typeList = t.getParameterTypes();
      return typeList.size() == 1 &&
              acceptTypeVisitor(typeList.get(0), PdoTreeCell.class);
    }
  };

}
