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

package org.tentackle.model.print;

import java.util.ArrayList;
import java.util.List;
import org.tentackle.model.ModelDefaults;

/**
 * Holds parameters to configure the generated output.
 *
 * @author harald
 */
public class PrintConfiguration {

  private final boolean printingAsComment;      // print as a comment block
  private final boolean usingVariables;         // use variables in model
  private final ModelDefaults modelDefaults;    // optional model defaults
  private final List<String> optionAnnotations; // annotations that should be printed as attribute options
  private final int columnGap;                  // minimum number of spaces between columns in attribute section


  /**
   * Creates a printing configuration.
   *
   * @param printingAsComment create a comment block
   * @param usingVariables use variables for tablename, classid and mapping
   * @param modelDefaults the model defaults, null if none
   * @param optionAnnotations annotations that should be printed as attribute options
   * @param columnGap minimum number of spaces between columns in attribute section
   */
  public PrintConfiguration(boolean printingAsComment, boolean usingVariables, ModelDefaults modelDefaults,
                            List<String> optionAnnotations, int columnGap) {
    this.printingAsComment = printingAsComment;
    this.usingVariables = usingVariables;
    this.modelDefaults = modelDefaults;
    this.optionAnnotations = optionAnnotations == null ? new ArrayList<>() : optionAnnotations;
    this.columnGap = columnGap < 1 ? 1 : columnGap;
  }

  /**
   * Returns whether generated output is a comment block.
   *
   * @return true if comment, else normal output
   */
  public boolean isPrintingAsComment() {
    return printingAsComment;
  }

  /**
   * Returns whether variables should be used.
   *
   * @return true if tablename, classid and mapping are variables
   */
  public boolean isUsingVariables() {
    return usingVariables;
  }

  /**
   * Gets the model defaults.
   *
   * @return the defaults, null if none
   */
  public ModelDefaults getModelDefaults() {
    return modelDefaults;
  }

  /**
   * Gets the annotations that should be printed as attribute options.
   *
   * @return the options, empty if none
   */
  public List<String> getOptionAnnotations() {
    return optionAnnotations;
  }

  /**
   * Gets the minimum number of spaces between columns in attribute section.
   *
   * @return the gap (always &ge; 1)
   */
  public int getColumnGap() {
    return columnGap;
  }

  /**
   * Returns whether given annotation should be printed as an attribute option.
   *
   * @param annotation the annotation string
   * @return true if attribute option
   */
  public boolean isOptionAnnotation(String annotation) {
    for (String anno: optionAnnotations) {
      if (annotation.startsWith(anno) &&
          (annotation.equals(anno) ||
          // is longer and followed by some args
          !Character.isJavaIdentifierPart(annotation.charAt(anno.length())))) {
        return true;
      }
    }
    return false;
  }

}
