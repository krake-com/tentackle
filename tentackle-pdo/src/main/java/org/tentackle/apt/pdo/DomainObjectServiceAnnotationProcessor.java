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


package org.tentackle.apt.pdo;

import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.element.Element;
import javax.tools.Diagnostic;
import org.tentackle.apt.visitor.ClassArgConstructorVisitor;
import org.tentackle.common.AnnotationProcessor;
import org.tentackle.pdo.PersistentDomainObject;

import static javax.lang.model.SourceVersion.RELEASE_10;

/**
 * Annotation processor for the {@code @DomainObjectService} annotation.<br>
 * Enforces the implementation of the following constructors:
 *
 * <ul>
 *   <li>({@link PersistentDomainObject})</li>
 *   <li>()</li>
 * </ul>
 *
 * @author harald
 */
@SupportedAnnotationTypes("org.tentackle.pdo.DomainObjectService")
@SupportedSourceVersion(RELEASE_10)
@AnnotationProcessor
public class DomainObjectServiceAnnotationProcessor extends AbstractDomainServiceAnnotationProcessor {


  private final ClassArgConstructorVisitor pdoVisitor = new ClassArgConstructorVisitor(this, PersistentDomainObject.class);


  @Override
  protected void processClass(Element element) {
    super.processClass(element);
    if (!verifyConstructor(element, noArgsVisitor)) {
      processingEnv.getMessager().printMessage(
          Diagnostic.Kind.ERROR,
          "class " + element + " needs a no-args constructor", element);
    }
    if (!verifyConstructor(element, pdoVisitor)) {
      processingEnv.getMessager().printMessage(
          Diagnostic.Kind.ERROR,
          "class " + element + " needs constructor (PersistentDomainObject)", element);
    }
    verifyImplements(element, "org.tentackle.pdo.DomainObject");
    verifyStatelessDomainLogic(element);
  }

}
