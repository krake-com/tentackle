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

// Created on December 8, 2004, 3:46 PM

package org.tentackle.io;

import java.io.Writer;
import org.tentackle.misc.ApplicationException;

/**
 * Generic writer for CSV-Files.<br>
 *
 * @author harald
 */
public class CSVWriter {
  
  private final Writer writer;          // where to write the csv output
  private int objectCount;              // number of converted objects
  private char fieldDelimiter = ',';    // field delimiter, default is comma
  private char quoteCharacter = '"';    // quote character, default is double-quote  
  
  
  /**
   * Creates a CSV-Writer.
   *
   * @param writer the output stream to write to
   */
  public CSVWriter(Writer writer) {
    this.writer = writer;
    objectCount = 0;
  }
  
  /**
   * Writes an object to a CSV-stream.
   *
   * @param object the object to be written
   * @throws ApplicationException if some error
   */
  public void formatCSVObject(CSVObject object) throws ApplicationException {
    
    int fieldCount = object.getCSVFieldCount();
    
    try {
      
      for (int index=0; index < fieldCount; index++)  {
        
        String value = makeField(object.formatCSVField(index));
        if (value != null)  {
          writer.write(value);
        }
        if (index < fieldCount - 1) {
          writer.write(fieldDelimiter);
        }
      }
    
      objectCount++;
      writer.write('\n');
    }
    catch (ApplicationException e)  {
      throw e;
    }
    catch (Exception e) {
      throw new ApplicationException("formatting object failed", e);
    }
  }
  
  
  
  /**
   * Gets the number of written objects.
   * 
   * @return the number of converted objects
   */
  public int getObjectCount() {
    return objectCount;
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
   * convert a string to a quoted CSV-Field.
   * @return the quoted string, null if value was null
   */
  private String makeField(String value)  {
    if (value != null)  {
      StringBuilder field = new StringBuilder();
      field.append(quoteCharacter);
      int length = value.length();
      for (int i=0; i < length; i++)  {
        char c = value.charAt(i);
        if (c == quoteCharacter) {
          field.append(quoteCharacter);
        }
        field.append(c);
      }
      field.append(quoteCharacter);
      return field.toString();
    }
    return null;
  }
  
}
