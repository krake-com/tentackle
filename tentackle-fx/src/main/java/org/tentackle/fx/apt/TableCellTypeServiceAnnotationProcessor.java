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

import javax.annotation.processing.SupportedAnnotationTypes;
import javax.lang.model.element.Element;
import javax.tools.Diagnostic;

/**
 * Annotation processor for the {@code @TableCellTypeService} annotation.<br>
 * Enforces a no-args constructor and the implementation of {@code TableCellType}.
 *
 * @author harald
 */
@SupportedAnnotationTypes("org.tentackle.fx.table.TableCellTypeService")
@AnnotationProcessor
public class TableCellTypeServiceAnnotationProcessor extends AbstractServiceAnnotationProcessor {

  /**
   * Creates the annotation processor.
   */
  public TableCellTypeServiceAnnotationProcessor() {
    // see -Xlint:missing-explicit-ctor since Java 16
  }


  @Override
  protected void processClass(Element element) {
    super.processClass(element);
    if (!verifyConstructor(element, noArgsVisitor)) {
      processingEnv.getMessager().printMessage(
          Diagnostic.Kind.ERROR,
          "class " + element + " needs a no-args constructor", element);
    }
    verifyImplements(element, "org.tentackle.fx.table.TableCellType");
  }

}
