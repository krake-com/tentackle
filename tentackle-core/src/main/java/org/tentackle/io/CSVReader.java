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

// Created on December 7, 2004, 4:42 PM

package org.tentackle.io;

import java.io.IOException;
import java.io.Reader;
import org.tentackle.misc.ApplicationException;

/**
 * Generic reader for CSV-Files.<br>
 *
 * @author harald
 */
public class CSVReader {

  private final Reader reader;          // the reader with proper charset
  private int state;                    // parsing state
  private int objectCount;              // object counter
  private int lineCount;                // current line-number
  private int charCount;                // character count in line
  private int index;                    // index of current CSV-field
  private StringBuilder value;           // the parsed value
  private CSVObject csvObj;             // current object
  private int aheadChar;                // character read ahead, -1 = nothing read ahead
  private char fieldDelimiter = ',';    // field delimiter, default is comma
  private char quoteCharacter = '"';    // quote character, default is double-quote


  private static final int IN_FIELD         = 1;    // in between a non-quoted field
  private static final int IN_QUOTED_FIELD  = 2;    // in between a quoted field
  private static final int END_OF_QUOTED    = 3;    // after end of quoted field
  private static final int END_OF_LINE      = 4;    // end of line



  /**
   * Creates a new CSV-reader.
   *
   * @param reader the open reader
   */
  public CSVReader(Reader reader) {
    this.reader = reader;
    state       = END_OF_LINE;
    aheadChar   = -1;
    objectCount = 0;
    lineCount   = 0;
    charCount   = 0;
  }


  /**
   * Parses the next CSV-Object.
   *
   * @param object the CSV-Object to read
   * @return true if object converted, false if end of file/stream reached
   *
   * @throws ApplicationException if parsing and/or conversion failed
   */
  public boolean parseCSVObject(CSVObject object) throws ApplicationException {

    csvObj = object;
    index  = 0;                      // start with first field
    value  = new StringBuilder();

    return parse(true);
  }


  /**
   * Skips a CSV-object.<br>
   *
   * Same as parseCSVObject but don't write to object.
   * Useful to recover from an exception and skip to the next object.
   *
   * @return false if end of stream reached
   * @throws ApplicationException if parsing failed
   */
  public boolean skipCSVObject() throws ApplicationException {
    value = new StringBuilder();
    return parse(false);
  }



  /**
   * Gets the number of converted objects so far.
   *
   * @return the number of converted objects
   */
  public int getObjectCount() {
    return objectCount;
  }

  /**
   * Gets the current line number.
   *
   * @return the current line number (useful for error-reporting)
   */
  public int getLineCount() {
    return lineCount;
  }

  /**
   * Gets the character position in current line.
   *
   * @return the character position in current line (useful for error-reporting)
   */
  public int getCharCount() {
    return charCount;
  }

  /**
   * Fets the field delimiter.
   *
   * @return the field delimiter.
   */
  public char getFieldDelimiter() {
    return fieldDelimiter;
  }

  /**
   * Sets the field delimiter.
   *
   * @param fieldDelimiter the delimiter, default is comma
   */
  public void setFieldDelimiter(char fieldDelimiter) {
    this.fieldDelimiter = fieldDelimiter;
  }

  /**
   * Gets the quote character.
   *
   * @return the quote character.
   */
  public char getQuoteCharacter() {
    return quoteCharacter;
  }

  /**
   * Sets the quote character.
   *
   * @param quoteCharacter the quote character, default is double-quote
   */
  public void setQuoteCharacter(char quoteCharacter) {
    this.quoteCharacter = quoteCharacter;
  }








  /**
   * the workhorse
   */
  private boolean parse(boolean flush) throws ApplicationException {

    try {

      int rv;

      while ((rv = read()) != -1)  {

        char c = (char) rv;

        if (state == END_OF_LINE) {
          if (c == '\r' || c == '\n') {
            continue;
          }
          // other character
          if (c == quoteCharacter) {
            state = IN_QUOTED_FIELD;
            continue;
          }
          state = IN_FIELD;
          // continue processing below...
        }

        if (state == IN_FIELD)  {
          // simple, non-quoted field
          if (c == fieldDelimiter) {
            flushValue(flush);
          }
          else if (c == '\r' || c == '\n') {
            flushValue(flush);
            eol();
            objectCount++;
            return true;    // object converted!
          }
          else if (c == quoteCharacter)  {
            if (value.length() == 0 || value.toString().trim().length() == 0)  {
              // ignore leading blanks before starting quote
              state = IN_QUOTED_FIELD;
              value.setLength(0);
            }
            else  {
              // treat quote as part of data (is that correct?)
              value.append(c);
            }
          }
          else  {
            // else character, append to value
            value.append(c);
          }
        }

        else if (state == IN_QUOTED_FIELD)  {
          // quoted field
          if (c == quoteCharacter) {
            // quote encountered, check if immediately followed by another quote
            int qc = read();
            if (qc == quoteCharacter)  {
              // embedded quote
              value.append(c);
            }
            else  {
              // put back
              aheadChar = qc;
              // this is the end of the quoted string
              state = END_OF_QUOTED;
              flushValue(flush);
            }
          }
          else  {
            // append to value
            if (c == '\r') {
              continue; // skip carriage returns
            }
            if (c == '\n') {
              lineCount++; // count lines
            }
            value.append(c);
          }
        }

        else if (state == END_OF_QUOTED)  {
          // comma seperator expected, or line-seperator
          if (c == fieldDelimiter) {
            state = IN_FIELD;
          }
          else if (c == '\r' || c == '\n')  {
            eol();
            objectCount++;
            return true;    // object converted!
          }
          else if (!Character.isWhitespace(c)) {
            throw new ApplicationException("illegal character '" + c + "' after closing quote");
          }
          // else is a whitespace: can be ignored
        }

      }
    }
    catch (ApplicationException e)  {
      throw e;
    }
    catch (Exception e) {
      throw new ApplicationException("parsing object failed", e);
    }

    return false;     // end of stream
  }


  /**
   * read next character from stream
   */
  private int read() throws IOException {
    if (aheadChar != -1)  {
      int c = aheadChar;
      aheadChar = -1;
      return c;
    }
    charCount++;
    return reader.read();
  }


  /**
   * process end of line
   */
  private void eol()  {
    state = END_OF_LINE;
    lineCount++;
    charCount=0;
  }

  /**
   * flush value to object
   *
   * @param flush is false if don't flush to object (useful for skipObject())
   */
  private void flushValue(boolean flush) throws ApplicationException {
    if (flush) {
      if (state == IN_FIELD)  {
        if (value.length() == 0) {
          csvObj.parseCSVField(index++, null);
        }
        else  {
          csvObj.parseCSVField(index++, value.toString().trim());
        }
      }
      else  {
        csvObj.parseCSVField(index++, value.toString());
      }
    }
    value.setLength(0);
  }

}
