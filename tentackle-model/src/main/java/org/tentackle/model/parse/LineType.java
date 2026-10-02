/*
 * Tentackle - https://tentackle.org.
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

package org.tentackle.model.parse;

/**
 * The model source line type.
 *
 * <ol>
 * <li>Empty lines or lines consisting of whitespace only are empty lines.</li>
 * <li>All lines beginning with a '#' are comments.</li>
 * <li>Lines beginning with '[' are global options.</li>
 * <li>Lines containing {@code :=} are configuration lines.
 *   <ul>
 *      <li>name := &lt;Entity&gt;</li>
 *      <li>index &lt;name&gt; := column_1 ...</li>
 *      <li>unique index &lt;name&gt; := column1 ...</li>
 *   </ul>
 * </li>
 * <li>Lines with the first word (sep. by whitespaces) not followed by a colon are attributes.</li>
 * <li>Lines with a word starting in lowercase followed by a colon are attribute option lines (e.g. for validation).</li>
 * <li>Lines with a word starting in uppercase followed by a colon are relation lines,
 * or relation option lines if the second word starts with an '@'.</li>
 * <li>Lines starting with an '@' following an attribute or relation (option) line are option lines of the same kind.</li>
 * </ol>
 *
 * Leading whitespace is ignored when determining the line type.
 *
 * @author harald
 */
public enum LineType {

  /** the empty line. */
  EMPTY,

  /** comment line. */
  COMMENT,

  /** configuration line. */
  CONFIGURATION,

  /** global options line. */
  GLOBAL_OPTION,

  /** attribute line. */
  ATTRIBUTE,

  /** attribute options line. */
  ATTRIBUTE_OPTION,

  /** relation line. */
  RELATION,

  /** relation options line. */
  RELATION_OPTION;


  /**
   * Creates a line for this line type.
   *
   * @param document the model's document
   * @param offset the start of line within the document
   * @return the created line
   */
  public Line createLine(Document document, int offset) {
    return switch (this) {
      case EMPTY -> new EmptyLine(document, offset, this);
      case COMMENT -> new CommentLine(document, offset, this);
      case CONFIGURATION -> new ConfigurationLine(document, offset, this);
      case GLOBAL_OPTION -> new GlobalOptionLine(document, offset, this);
      case ATTRIBUTE_OPTION, RELATION_OPTION -> new OptionLine(document, offset, this);
      case ATTRIBUTE -> new AttributeLine(document, offset, this);
      case RELATION -> new RelationLine(document, offset, this);
    };
  }
}
