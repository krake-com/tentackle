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

import org.tentackle.model.ModelException;

/**
 * Maps the annotation string of the model.<br>
 *
 * Implements the modifiers.
 */
public class AnnotationOption {

  private final String annotation;
  private boolean setterOnly;
  private boolean setterAndGetter;
  private boolean hidden;

  private enum Modifier {
    SETTER_ONLY,
    SETTER_AND_GETTER,
    HIDDEN
  }

  public AnnotationOption(String annotation) throws ModelException {
    if (annotation == null || !annotation.startsWith("@") || annotation.length() < 2) {
      throw new ModelException("malformed annotation " + annotation);
    }

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


  public String getAnnotation() {
    return annotation;
  }

  public boolean isSetterOnly() {
    return setterOnly;
  }

  public boolean isSetterAndGetter() {
    return setterAndGetter;
  }

  public boolean isHidden() {
    return hidden;
  }



  private Modifier toModifier(char c) {
    switch(c) {
      case '=': return Modifier.SETTER_ONLY;
      case '+': return Modifier.SETTER_AND_GETTER;
      case '~': return Modifier.HIDDEN;
    }
    return null;
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
