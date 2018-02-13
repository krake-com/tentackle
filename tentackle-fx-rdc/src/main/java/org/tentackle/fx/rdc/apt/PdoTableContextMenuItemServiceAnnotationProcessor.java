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


package org.tentackle.fx.rdc.apt;

import java.util.List;
import java.util.Set;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.ExecutableType;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.type.TypeVisitor;
import javax.lang.model.util.SimpleTypeVisitor8;
import javax.tools.Diagnostic;
import org.tentackle.apt.AbstractServiceAnnotationProcessor;
import org.tentackle.common.AnnotationProcessor;
import org.tentackle.fx.rdc.PdoTableCell;

import static javax.lang.model.SourceVersion.RELEASE_9;

/**
 * Annotation processor for the {@code @PdoTableContextMenuItemService} annotation.<br>
 * Enforces the implementation of the following constructors:
 *
 * <ul>
 *   <li>({@link PdoTableCell})</li>
 * </ul>
 *
 * @author harald
 */
@SupportedAnnotationTypes("org.tentackle.fx.rdc.PdoTableContextMenuItemService")
@SupportedSourceVersion(RELEASE_9)
@AnnotationProcessor
public class PdoTableContextMenuItemServiceAnnotationProcessor extends AbstractServiceAnnotationProcessor {


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
    if (!verifyConstructor(element, pdoVisitor)) {
      processingEnv.getMessager().printMessage(
          Diagnostic.Kind.ERROR,
          "class " + element + " needs constructor (PdoTableCell)", element);
    }
    verifyImplements(element, "org.tentackle.fx.rdc.PdoTableContextMenuItem");
  }


  private final TypeVisitor<Boolean, Void> pdoVisitor = new SimpleTypeVisitor8<Boolean, Void>() {
    @Override
    public Boolean visitExecutable(ExecutableType t, Void v) {
      List<? extends TypeMirror> typeList = t.getParameterTypes();
      return typeList.size() == 1 &&
             acceptTypeVisitor(typeList.get(0), PdoTableCell.class);
    }
  };

}
