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


package org.tentackle.buildsupport;

import java.util.ArrayList;
import java.util.List;
import java.util.StringTokenizer;
import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.Modifier;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;



/**
 * Helper for annotation processing.
 *
 * @author harald
 */
public final class AnnotationProcessingHelper {


  /**
   * Gets an array of modifiers from a string tokenizer.
   *
   * @param stok the string tokenizer
   * @return the modifier array
   */
  public static Modifier[] readModifiers(StringTokenizer stok) {
    List<Modifier> modList = new ArrayList<>();
    while (stok.hasMoreTokens()) {
      try {
        modList.add(Modifier.valueOf(stok.nextToken().toUpperCase()));
      }
      catch (IllegalArgumentException ex) {
        // unknown modifier: ignore
      }
    }
    return modList.toArray(new Modifier[modList.size()]);
  }


  /**
   * Checks if a modifier is in the set of modifiers.
   *
   * @param modifier the modifier to check
   * @param modifierList the list of modifiers
   * @return true if modifier is in the list of modifiers
   */
  public static boolean isModifierSet(Modifier modifier, Modifier[] modifierList) {
    boolean rv = false;
    if (modifierList != null) {
      for (Modifier m: modifierList) {
        if (m.equals(modifier)) {
          rv = true;
          break;
        }
      }
    }
    return rv;
  }



  /**
   * Creates a string from an object array.
   * Copied from StringHelper to keep dependencies small.
   *
   * @param objArray the array of objects
   * @param separator the string between two objects
   * @return the object array string
   */
  public static String objectArrayToString(Object[] objArray, String separator) {
    StringBuilder buf = new StringBuilder();
    if (objArray != null) {
      boolean addSeparator = false;
      for (Object obj: objArray) {
        if (addSeparator) {
          buf.append(separator);
        }
        buf.append(obj.toString());
        addSeparator = true;
      }
    }
    return buf.toString();
  }




  /**
   * Checks whether a type is an instance of of a given classname and optional generics type.
   *
   * @param processingEnv the annotation processing environment
   * @param typeMirror the element type
   * @param className the class to check
   * @param genType the generic type
   * @return true if type is an instance of of a given classname
   */
  public static boolean isTypeInstanceoOf(ProcessingEnvironment processingEnv, TypeMirror typeMirror, String className, String genType) {

    String typeName;

    boolean genTypeOk = true;

    if (typeMirror instanceof DeclaredType) {
      typeName = ((DeclaredType) typeMirror).asElement().toString();
      if (genType != null) {
        // check type paramaters
        genTypeOk = false;
        for (TypeMirror tm: ((DeclaredType) typeMirror).getTypeArguments()) {
          if (isTypeInstanceoOf(processingEnv, tm, genType)) {
            genTypeOk = true;
            break;
          }
        }
      }
    }
    else  {
      typeName = typeMirror.toString();
    }

    if (typeName.equals(className)) {
      // true if optional type parameters and the classname matches
      return genTypeOk;
    }

    for (TypeMirror tm: processingEnv.getTypeUtils().directSupertypes(typeMirror)) {
      if (isTypeInstanceoOf(processingEnv, tm, className, genType)) {
        return true;
      }
    }
    return false;
  }


  /**
   * Checks whether a type is a given instance of a class.
   *
   * @param processingEnv the annotation processing environment
   * @param typeMirror the element type
   * @param className the class to check
   * @return true if the typeMirror represents an instance of className
   */
  public static boolean isTypeInstanceoOf(ProcessingEnvironment processingEnv, TypeMirror typeMirror, String className) {
    return isTypeInstanceoOf(processingEnv, typeMirror, className, null);
  }


  /**
   * prevent instantiation.
   */
  private AnnotationProcessingHelper() {}

}
