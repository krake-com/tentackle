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

package org.tentackle.model.print;

import org.tentackle.model.ModelDefaults;

import java.util.List;

/**
 * Holds parameters to configure the generated output.
 *
 * @param compact compact output, i.e., omit implicit configuration
 * @param asComment print as a comment block
 * @param useVariables use variables in the printed model
 * @param modelDefaults optional model defaults
 * @param optionAnnotations annotations that should be printed as attribute options
 * @param columnGap minimum number of spaces between columns in the attribute section
 */
public record PrintConfiguration(boolean compact, boolean asComment, boolean useVariables,
                                 ModelDefaults modelDefaults, List<String> optionAnnotations, int columnGap) {

  /**
   * Returns whether the given annotation should be printed as an attribute option.
   * <p>
   * {@link #optionAnnotations} is a list of annotations that should be printed as attribute options.<br>
   * If an entry ends with a {@code |}, it matches only the annotation without parameters.<br>
   * The special annotation {@code @*} matches all annotations and {@code @*|}
   * matches all annotations without parameters.
   * <p>
   * Example: the entries {@code @NotNull|} and {@code @NotZero} print {@code @NotNull}, {@code @NotZero}
   * and {@code @NotZero(...)} as options, but not {@code @NotNull(...)}.
   *
   * @param annotation the annotation string
   * @return true to print as an attribute option
   */
  public boolean isOptionAnnotation(String annotation) {
    boolean withParams = annotation.contains("(");
    for (String anno: optionAnnotations) {
      if ("@*".equals(anno) ||
          "@*|".equals(anno) && !withParams ||
          anno.endsWith("|") && annotation.equals(anno.substring(0, anno.length() - 1)) ||
          !anno.endsWith("|") && annotation.startsWith(anno) &&
            (annotation.equals(anno) ||
             // is longer and followed by some args
             !Character.isJavaIdentifierPart(annotation.charAt(anno.length())))) {
        return true;
      }
    }
    return false;
  }

}
