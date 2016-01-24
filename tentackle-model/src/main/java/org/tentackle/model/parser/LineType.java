/*
 * Tentackle - http://www.tentackle.org.
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

package org.tentackle.model.parser;

/**
 * The configuration file line type.
 *
 * <ol>
 * <li>All lines beginning with a '#' are comments. Anything following a # is comment as well.</li>
 * <li>Lines containing a '=' not containing a ':' in between the start-of-line and the '=' are configuration lines.
 *   <ul>
 *      <li>pdo = &lt;Pdo-Interface&gt;</li>
 *      <li>index &lt;name&gt; = column_1, ...</li>
 *      <li>unique index &lt;name&gt; = column1, ...</li>
 *   </ul>
 * </li>
 * <li>Lines beginning with '[' are global options.</li>
 * <li>Lines with the first word (sep. by whitespaces) not followed by a colon are attributes.</li>
 * <li>Lines with a word starting in lowercase followed by a colon are attribute option lines (e.g. for validation).</li>
 * <li>Lines with a word starting in uppercase followed by a colon are relation lines.</li>
 * </ol>
 *
 * Leading whitespaces are ignored when determining the line type.
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
    switch (this) {
      case EMPTY:
        return new EmptyLine(document, offset, this);

      case COMMENT:
        return new CommentLine(document, offset, this);

      case CONFIGURATION:
        return new ConfigurationLine(document, offset, this);

      case GLOBAL_OPTION:
        return new GlobalOptionLine(document, offset, this);

      case ATTRIBUTE_OPTION:
        return new OptionLine(document, offset, this);

      case ATTRIBUTE:
        return new AttributeLine(document, offset, this);

      case RELATION:
        return new RelationLine(document, offset, this);

      case RELATION_OPTION:
        return new OptionLine(document, offset, this);

      default:  throw new IllegalArgumentException("unimplemented line type");
    }
  }
}
