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
package org.tentackle.wurblet;

import org.tentackle.model.AttributeOptions;

/**
 * Maps the annotation string of the model.<br>
 * Implements the modifiers as described in {@link AttributeOptions#getAnnotations()}.
 */
public class AnnotationOption {

  private final String annotation;
  private String annotationType;
  private boolean setterOnly;
  private boolean setterAndGetter;
  private boolean hidden;

  private enum Modifier {
    SETTER_ONLY,
    SETTER_AND_GETTER,
    HIDDEN
  }

  /**
   * Creates the annotation option.
   *
   * @param annotation the annotation string from the model
   */
  public AnnotationOption(String annotation) {
    if (annotation.length() > 2) {
      Modifier modifier = toModifier(annotation.charAt(1));
      if (modifier != null) {
        annotation = "@" + annotation.substring(2);
        applyModifier(modifier);
        if (annotation.length() > 2) {
          modifier = toModifier(annotation.charAt(1));
          if (modifier != null) {
            annotation = "@" + annotation.substring(2);
            applyModifier(modifier);
          }
        }
      }
    }
    this.annotation = annotation;
  }

  /**
   * Gets the total annotation string without the optional modifier.
   *
   * @return the annotation string
   */
  public String getAnnotation() {
    return annotation;
  }

  /**
   * Returns whether setter annotation only.
   *
   * @return true if setter only
   */
  public boolean isSetterOnly() {
    return setterOnly;
  }

  /**
   * Returns whether setter and getter annotation.
   *
   * @return true if both
   */
  public boolean isSetterAndGetter() {
    return setterAndGetter;
  }

  /**
   * Returns whether this is a hidden annotation.
   *
   * @return true if only applicable to the implementation layer and not the interface
   */
  public boolean isHidden() {
    return hidden;
  }


  /**
   * Gets the annotation type without the at-sign and optional parameters.
   *
   * @return the annotation type
   */
  public String getAnnotationType() {
    if (annotationType == null && annotation.startsWith("@")) {   // lazy...
      // extract the annotation type
      int ndx = annotation.indexOf('(');
      if (ndx > 1) {
        annotationType = annotation.substring(1, ndx).trim();
      }
      else {
        annotationType = annotation.substring(1).trim();
      }
    }
    return annotationType;
  }


  private Modifier toModifier(char c) {
    return switch (c) {
      case '=' -> Modifier.SETTER_ONLY;
      case '+' -> Modifier.SETTER_AND_GETTER;
      case '~' -> Modifier.HIDDEN;
      default -> null;
    };
  }

  private void applyModifier(Modifier modifier) {
    switch (modifier) {
      case HIDDEN:
        hidden = true;
        break;
      case SETTER_ONLY:
        setterOnly = true;
        break;
      case SETTER_AND_GETTER:
        setterAndGetter = true;
        break;
    }
  }

}
