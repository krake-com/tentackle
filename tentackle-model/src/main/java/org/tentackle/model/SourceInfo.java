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

package org.tentackle.model;

import java.io.File;
import java.util.Objects;

/**
 * Information about the source.
 *
 * @author harald
 */
public class SourceInfo {

  private final String fileName;    // the filename
  private final int lineNumber;     // first line number in filename (starting at 1)
  private final int position;       // the character position within the line (starting at 1)

  /**
   * Creates a source info.
   *
   * @param fileName the name
   * @param lineNumber the line number (starting at 1, 0 if unknown)
   * @param position the character position within the line (starting at 1, 0 if unknown)
   */
  public SourceInfo(String fileName, int lineNumber, int position) {
    this.fileName = fileName;
    this.lineNumber = lineNumber;
    this.position = position;
  }

  /**
   * Creates a source info.
   *
   * @param fileName the name
   * @param lineNumber the line number (starting at 1)
   */
  public SourceInfo(String fileName, int lineNumber) {
    this(fileName, lineNumber, 0);
  }

  /**
   * Creates a source info.
   *
   * @param fileName the name
   */
  public SourceInfo(String fileName) {
    this(fileName, 0);
  }

  /**
   * Gets the filename.
   *
   * @return the filename
   */
  public String getFileName() {
    return fileName;
  }

  /**
   * Gets the filename without leading directories.
   *
   * @return the short filename
   */
  public String getShortFileName() {
    String shortName = getFileName();
    int ndx = shortName.lastIndexOf('/');
    if (ndx == -1) {
      ndx = shortName.lastIndexOf(File.separatorChar);
    }
    if (ndx >= 0) {
      shortName = shortName.substring(ndx + 1);
    }
    return shortName;
  }

  /**
   * Gets the line number.
   *
   * @return the line number starting at 1, 0 if unknown
   */
  public int getLineNumber() {
    return lineNumber;
  }

  /**
   * Gets the character position within the line.
   *
   * @return the position, starting at 1, 0 if unknown
   */
  public int getPosition() {
    return position;
  }

  /**
   * Returns a source info with offsets added.
   *
   * @param lineOffset number of lines to add
   * @param positionOffset number of character positions to add
   * @return the new info
   */
  public SourceInfo add(int lineOffset, int positionOffset) {
    return new SourceInfo(fileName, lineNumber + lineOffset, position + positionOffset);
  }

  @Override
  public String toString() {
    StringBuilder buf = new StringBuilder(getShortFileName());
    if (getLineNumber() != 0) {
      buf.append('[');
      buf.append(getLineNumber());
      if (getPosition() != 0) {
        buf.append(',');
        buf.append(getPosition());
      }
      buf.append(']');
    }
    return buf.toString();
  }

  @Override
  public int hashCode() {
    int hash = 3;
    hash = 17 * hash + Objects.hashCode(this.fileName);
    hash = 17 * hash + this.lineNumber;
    hash = 17 * hash + this.position;
    return hash;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (obj == null) {
      return false;
    }
    if (getClass() != obj.getClass()) {
      return false;
    }
    final SourceInfo other = (SourceInfo) obj;
    if (this.lineNumber != other.lineNumber) {
      return false;
    }
    if (this.position != other.position) {
      return false;
    }
    return Objects.equals(this.fileName, other.fileName);
  }

}
