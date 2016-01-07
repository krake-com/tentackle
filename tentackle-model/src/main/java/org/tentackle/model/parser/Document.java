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

import java.util.ArrayList;
import java.util.List;
import java.util.StringTokenizer;
import org.tentackle.common.Constants;
import org.tentackle.model.ModelException;
import org.tentackle.model.SourceInfo;

/**
 * The input document.
 *
 * @author harald
 */
public class Document {

  private final String text;            // the whole document text
  private final SourceInfo sourceInfo;  // source information, null if none

  private int offset;                   // current offset
  private List<Line> parsedLines;       // the parsed lines


  /**
   * Creates a document.
   *
   * @param text the model specification source
   */
  public Document(String text) {
    if (text == null) {
      throw new NullPointerException("document text cannot be null");
    }
    if (text.startsWith(Constants.ORIGIN_INFO_LEAD)) {
      // source info line
      int ndx = text.indexOf('\n');   // the end of line
      if (ndx > 0) {
        String firstLine = text.substring(2, ndx);
        text = text.substring(ndx + 1);   // remove source line, 1 line added again below in new SourceInfo(....)
        ndx = firstLine.indexOf(':');   // get line number
        if (ndx > 0) {
          sourceInfo = new SourceInfo(firstLine.substring(0, ndx), Integer.valueOf(firstLine.substring(ndx + 1)) + 1);
        }
        else  {
          sourceInfo = new SourceInfo(firstLine);
        }
      }
      else  {
        sourceInfo = null;
      }
    }
    else  {
      sourceInfo = null;
    }
    this.text = processContinuationLines(text);
  }


  /**
   * Gets the document text.
   *
   * @return the document text
   */
  public String getText() {
    return text;
  }


  /**
   * Gets the source info.
   *
   * @return the info, null if unknown
   */
  public SourceInfo getSourceInfo() {
    return sourceInfo;
  }


  /**
   * Gets the parsed lines.
   *
   * @return the lines, never null
   * @throws ModelException if parsing failed
   */
  public List<Line> getLines() throws ModelException {
    if (parsedLines == null) {
      parsedLines = parse();
    }
    return parsedLines;
  }


  /**
   * Gets the line number for a given offset within the document.
   *
   * @param offsetInDocument the offset
   * @return the linenumber starting at 1
   */
  public int getLineNumber(int offsetInDocument) {
    int lineNumber = 1;
    int ndx = 0;
    while (ndx < offsetInDocument) {
      ndx = text.indexOf('\n', ndx);
      if (ndx >= 0) {
        lineNumber++;
        ndx++;
      }
      else  {
        break;
      }
    }
    return lineNumber;
  }






  /**
   * Parses the document and returns a list of lines.
   *
   * @return the list of lines, never null
   * @throws ModelException if parsing failed
   */
  protected List<Line> parse() throws ModelException {
    List<Line> lines = new ArrayList<>();
    Line line = null;   // current line
    for (;;) {
      line = nextLine(line);
      if (line == null) {
        break;
      }
      line.parse();
      lines.add(line);
    }

    return lines;
  }


  /**
   * Gets the next line.
   *
   * @param currentLine the current line, null if start of document
   * @return the line, null if reached end of document
   * @throws ModelException if parsing failed
   */
  protected Line nextLine(Line currentLine) throws ModelException {

    if (currentLine == null) {
      offset = 0;
    }
    else  {
      offset += currentLine.getLength() + 1;
    }

    if (offset < text.length()) {

      // determine next physical line
      int lineEnd = text.indexOf('\n', offset);
      if (lineEnd == -1) {
        // this is the last line
        lineEnd = text.length();
      }
      String lineText = text.substring(offset, lineEnd);

      // determine line type
      String trimmedLine = lineText.trim(); // trim to skip leading whitespaces (trailing doesnt matter here)
      if (trimmedLine.length() == 0) {
        return LineType.EMPTY.createLine(this, offset);
      }

      char firstChar = trimmedLine.charAt(0);
      if (firstChar == '#') {
        return LineType.COMMENT.createLine(this, offset);
      }

      if (firstChar == '[') {
        return LineType.GLOBAL_OPTION.createLine(this, offset);
      }

      int colonIndex = lineText.indexOf(':');
      int equalsIndex = lineText.indexOf('=');

      if (colonIndex > 0 && colonIndex == equalsIndex - 1) {
        return LineType.CONFIGURATION.createLine(this, offset);
      }

      StringTokenizer stok = new StringTokenizer(lineText, " \t\r\f:=");
      if (stok.hasMoreTokens()) {
        String firstWord = stok.nextToken();
        int secondWordIndex = -1;
        String secondWord = null;
        if (stok.hasMoreTokens()) {
          secondWord = stok.nextToken();
          secondWordIndex = lineText.indexOf(secondWord);
        }

        if (colonIndex == -1 || secondWordIndex > 0 && colonIndex > secondWordIndex) {
          // first word not followed by a colon
          return LineType.ATTRIBUTE.createLine(this, offset);
        }

        if (Character.isUpperCase(firstWord.charAt(0))) {
          // relation or relation option
          if (secondWord != null && secondWord.charAt(0) == '@') {
            // annotation -> validation line
            return LineType.RELATION_OPTION.createLine(this, offset);
          }
          return LineType.RELATION.createLine(this, offset);
        }
        else  {
          if (secondWord != null) {
            return LineType.ATTRIBUTE_OPTION.createLine(this, offset);
          }
          // syntax error
        }
      }

      // parsing error.
      throw new ModelException("cannot determine line type",
                               getSourceInfo().add(getLineNumber(offset) - 1, 0));
    }

    return null;    // end of file
  }


  /**
   * Concatenate continuation lines.<br>
   * Lines ending with a backslash are continued on the next line.<br>
   * Notice that any spaces following the backslash are ignored.
   *
   * @param text the original text
   * @return the processed text
   */
  private String processContinuationLines(String text) {
    StringBuilder buf = new StringBuilder(text.length());
    int lastBackslashIndex = -1;
    for (int pos = 0; pos < text.length(); pos++) {
      char c = text.charAt(pos);
      if (c == '\n' && lastBackslashIndex >= 0) {
        // concatenate
        buf.delete(lastBackslashIndex, buf.length()); // remove backslash incl. trailing whitespaces
      }
      else  {
        if (c == '\\') {
          lastBackslashIndex = buf.length();
        }
        else if (c != ' ') {
          lastBackslashIndex = -1;
        }
        buf.append(c);
      }
    }
    return buf.toString();
  }

}
