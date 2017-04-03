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

/**
 * Holds parameters to configure the generated output.
 *
 * @author harald
 */
public class PrintConfiguration {

  private final boolean printingAsComment;      // print as a comment block
  private final boolean usingVariables;         // use variables in model

  /**
   * Creates a printing configuration.
   *
   * @param printingAsComment create a comment block
   * @param usingVariables use variables for tablename, classid and mapping
   */
  public PrintConfiguration(boolean printingAsComment, boolean usingVariables) {
    this.printingAsComment = printingAsComment;
    this.usingVariables = usingVariables;
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

}
