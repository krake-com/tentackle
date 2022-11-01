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

package org.tentackle.apt.pdo;

import org.tentackle.apt.AbstractServiceAnnotationProcessor;
import org.tentackle.apt.visitor.ClassArgConstructorVisitor;
import org.tentackle.common.AnnotationProcessor;
import org.tentackle.pdo.DomainContext;
import org.tentackle.pdo.PersistentDomainObject;
import org.tentackle.session.Session;

import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.ExecutableType;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.type.TypeVisitor;
import javax.lang.model.util.SimpleTypeVisitor8;
import javax.tools.Diagnostic;
import java.util.List;
import java.util.Set;

/**
 * Annotation processor for the {@code @PersistentObjectService} annotation.<br>
 * Enforces the implementation of the following constructors:
 *
 * <ul>
 *   <li>({@link PersistentDomainObject}, {@link DomainContext})</li>
 *   <li>({@link PersistentDomainObject}, {@link Session})</li>
 *   <li>({@link PersistentDomainObject})</li>
 *   <li>()</li>
 * </ul>
 *
 * @author harald
 */
@SupportedAnnotationTypes("org.tentackle.pdo.PersistentObjectService")
@AnnotationProcessor
public class PersistentObjectServiceAnnotationProcessor extends AbstractServiceAnnotationProcessor {

  private final ClassArgConstructorVisitor pdoVisitor = new ClassArgConstructorVisitor(this, PersistentDomainObject.class);

  /**
   * Creates the processor.
   */
  public PersistentObjectServiceAnnotationProcessor() {
    // see -Xlint:missing-explicit-ctor since Java 16
  }

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
          element + " needs a no-args constructor", element);
    }
    if (!verifyConstructor(element, pdoVisitor)) {
      processingEnv.getMessager().printMessage(
          Diagnostic.Kind.ERROR,
          element + " needs constructor (PersistentDomainObject)", element);
    }
    if (!verifyConstructor(element, pdoContextVisitor)) {
      processingEnv.getMessager().printMessage(
          Diagnostic.Kind.ERROR,
          element + " needs constructor (PersistentDomainObject, DomainContext)", element);
    }
    if (!verifyConstructor(element, pdoSessionVisitor)) {
      processingEnv.getMessager().printMessage(
          Diagnostic.Kind.ERROR,
          element + " needs constructor (PersistentDomainObject, Session)", element);
    }
    verifyImplements(element, "org.tentackle.pdo.PersistentObject");
  }


  private final TypeVisitor<Boolean, Void> pdoContextVisitor = new SimpleTypeVisitor8<>() {
    @Override
    public Boolean visitExecutable(ExecutableType t, Void v) {
      List<? extends TypeMirror> typeList = t.getParameterTypes();
      return typeList.size() == 2 &&
              acceptTypeVisitor(typeList.get(0), PersistentDomainObject.class) &&
              acceptTypeVisitor(typeList.get(1), DomainContext.class);
    }
  };

  private final TypeVisitor<Boolean, Void> pdoSessionVisitor = new SimpleTypeVisitor8<>() {
    @Override
    public Boolean visitExecutable(ExecutableType t, Void v) {
      List<? extends TypeMirror> typeList = t.getParameterTypes();
      return typeList.size() == 2 &&
              acceptTypeVisitor(typeList.get(0), PersistentDomainObject.class) &&
              acceptTypeVisitor(typeList.get(1), Session.class);
    }
  };

}
