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

import org.tentackle.model.ModelException;
import org.tentackle.model.SourceInfo;

/**
 * A parsed line.<br>
 * May span multiple input lines.
 *
 * @author harald
 */
public abstract class Line {

  private final Document document;
  private final int offset;
  private final LineType lineType;
  private final int lineNumber;

  private int length = -1;    // the parsed length, -1 if not parsed yet


  /**
   * Creates a line.
   *
   * @param document the whole document
   * @param offset offset to first character within document
   * @param lineType the line type
   */
  public Line(Document document, int offset, LineType lineType) {
    this.document = document;
    this.offset = offset;
    this.lineType = lineType;

    lineNumber = document.getLineNumber(offset);
  }


  /**
   * Gets the document this line belongs to.
   *
   * @return the document
   */
  public Document getDocument() {
    return document;
  }

  /**
   * Gets the offset within the document.
   *
   * @return the start of this line
   */
  public int getOffset() {
    return offset;
  }

  /**
   * Gets the first linenumber of this line within the document.
   *
   * @return the linenumber, starting at 1
   */
  public int getLineNumber() {
    return lineNumber;
  }

  /**
   * Gets the line type.
   *
   * @return the line type
   */
  public LineType getLineType() {
    return lineType;
  }


  /**
   * Asserts that this line is parsed.
   *
   * @throws ModelException if not parsed
   */
  protected void assertParsed() throws ModelException {
    if (length < 0) {
      throw createModelException("line not parsed yet");
    }
  }

  /**
   * Gets the length of the parsed line.
   *
   * @return the length
   * @throws ModelException if line has not been parsed yet
   */
  public int getLength() throws ModelException {
    assertParsed();
    return length;
  }


  /**
   * Sets the line's length.<br>
   * Invoked from within {@link #parse()}.
   *
   * @param length the line's length, -1 means to end of file
   */
  protected void setLength(int length) {
    this.length = length < 0 ? getDocument().getText().length() - offset : length;
  }


  /**
   * Gets the line's text.
   *
   * @return the text within the document
   * @throws ModelException if line has not been parsed yet
   */
  public String getText() throws ModelException {
    return document.getText().substring(offset, offset + getLength());
  }

  /**
   * Gets the source info for this line.
   *
   * @return the source info
   */
  public SourceInfo getSourceInfo() {
    SourceInfo sourceInfo = getDocument().getSourceInfo();
    if (sourceInfo != null) {
      return sourceInfo.add(lineNumber - 1, 0);
    }
    return null;
  }

  @Override
  public String toString() {
    SourceInfo info = getSourceInfo();
    return info == null ? ("[offset " + offset + "]") : info.toString();
  }

  /**
   * Parses a line.
   *
   * @throws ModelException if parsing failed
   */
  public abstract void parse() throws ModelException;


  /**
   * Creates a model exception.
   *
   * @param message the message
   * @param cause the optional chained cause
   * @return the exception
   */
  public ModelException createModelException(String message, Throwable cause) {
    return new ModelException(appendSourceInfo(message), cause);
  }

  /**
   * Creates a model exception.
   * @param message the message
   * @return the exception
   */
  public ModelException createModelException(String message) {
    return new ModelException(appendSourceInfo(message));
  }

  /**
   * Appends the source location to a message, if known.<br>
   * There is no source info if the model source does not start with {@link Document#ORIGIN_INFO_LEAD},
   * which is the case for model sources loaded from a jar or the classpath.
   *
   * @param message the message
   * @return the message, followed by the source location if there is one
   */
  private String appendSourceInfo(String message) {
    SourceInfo sourceInfo = getSourceInfo();
    return sourceInfo == null ? message : message + ModelException.AT_ELEMENT + sourceInfo;
  }

}
