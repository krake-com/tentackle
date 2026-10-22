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

package org.tentackle.misc;

import java.io.Serial;
import java.io.Serializable;
import java.util.Objects;

/**
 * A segment of string.<br>
 * Describes a substring by its indexes within a string.<br>
 * The substring itself is built lazily, thus allowing index computations without
 * string creations.
 * <p>
 * Notice that {@link #equals(Object)} and {@link #hashCode()} refer to the original string and the indexes,
 * not the segment's contents. Use {@link #equalsSegment(SubString)} to compare the contents.
 */
public class SubString implements Serializable {

  @Serial
  private static final long serialVersionUID = 1L;

  private final String string;
  private final int beginIndex;
  private final int endIndex;
  private final int length;
  private String segment;

  /**
   * Creates a string segment.
   *
   * @param string the original string
   * @param beginIndex the beginning index
   * @param endIndex the ending index (exclusive)
   * @throws NullPointerException if string is null
   * @throws IndexOutOfBoundsException if the indexes are out of bounds
   */
  public SubString(String string, int beginIndex, int endIndex) {
    this.string = Objects.requireNonNull(string);
    this.beginIndex = beginIndex;
    this.endIndex = endIndex;
    length = endIndex - beginIndex;
    if (length < 0) {
      throw new IndexOutOfBoundsException("beginIndex > endIndex");
    }
    int totalLength = string.length();
    if (beginIndex < 0 || beginIndex > totalLength) {
      throw new IndexOutOfBoundsException("illegal beginIndex: " + beginIndex);
    }
    if (endIndex < 0 || endIndex > totalLength) {
      throw new IndexOutOfBoundsException("illegal endIndex: " + endIndex);
    }
  }

  /**
   * Creates a string segment up to the end of the given string.
   *
   * @param string the original string
   * @param beginIndex the beginning index
   * @throws IndexOutOfBoundsException if beginIndex is out of bounds
   */
  public SubString(String string, int beginIndex) {
    this(string, beginIndex, string.length());
  }

  /**
   * Creates a string segment as a full copy of a string.
   *
   * @param string the string
   */
  public SubString(String string) {
    this(string, 0);
  }

  /**
   * Gets the original string.
   *
   * @return the string
   */
  public String getString() {
    return string;
  }

  /**
   * Gets the beginning index.
   *
   * @return the beginning
   */
  public int getBeginIndex() {
    return beginIndex;
  }

  /**
   * Gets the ending index.
   *
   * @return the ending (exclusive)
   */
  public int getEndIndex() {
    return endIndex;
  }

  /**
   * Gets the length of the string segment.
   *
   * @return the segment's length
   */
  public int getLength() {
    return length;
  }

  /**
   * Gets the string segment.<br>
   * The segment is created on first invocation.
   *
   * @return the segment
   */
  public String getSegment() {
    if (segment == null) {
      segment = string.substring(beginIndex, endIndex);
    }
    return segment;
  }

  /**
   * Creates a new string segment relative to this segment.
   *
   * @param ndx the index within this segment
   * @param len the length of the derived segment
   * @return the derived segment
   * @throws IllegalArgumentException if len is negative
   * @throws IndexOutOfBoundsException if the derived segment exceeds this segment
   */
  public SubString with(int ndx, int len) {
    if (len < 0) {
      throw new IllegalArgumentException("len must be >= 0");
    }
    if (ndx < 0 || ndx + len > length) {
      throw new IndexOutOfBoundsException("derived segment not within this segment's bounds");
    }
    int absoluteIndex = beginIndex + ndx;
    return new SubString(string, absoluteIndex, absoluteIndex + len);
  }

  /**
   * Returns whether this segment starts with another segment.
   *
   * @param other the other segment
   * @return true if so
   */
  public boolean startsWith(SubString other) {
    return string.regionMatches(beginIndex, other.string, other.beginIndex, other.length);
  }

  /**
   * Returns whether this segment starts with another segment, ignoring the case.
   *
   * @param other the other segment
   * @return true if so
   */
  public boolean startsWithIgnoreCase(SubString other) {
    return string.regionMatches(true, beginIndex, other.string, other.beginIndex, other.length);
  }

  /**
   * Returns whether this segment ends with another segment.
   *
   * @param other the other segment
   * @return true if so
   */
  public boolean endsWith(SubString other) {
    return endsWith(other, false);
  }

  /**
   * Returns whether this segment ends with another segment, ignoring the case.
   *
   * @param other the other segment
   * @return true if so
   */
  public boolean endsWithIgnoreCase(SubString other) {
    return endsWith(other, true);
  }

  /**
   * Gets the index of another segment within this segment.
   *
   * @param other the other segment
   * @return the index relative to this segment, -1 if no match
   */
  public int indexOf(SubString other) {
    return indexOf(other, false);
  }

  /**
   * Gets the index of another segment within this segment, ignoring the case.
   *
   * @param other the other segment
   * @return the index relative to this segment, -1 if no match
   */
  public int indexOfIgnoreCase(SubString other) {
    return indexOf(other, true);
  }

  /**
   * Gets the last index of another segment within this segment.
   *
   * @param other the other segment
   * @return the index relative to this segment, -1 if no match
   */
  public int lastIndexOf(SubString other) {
    return lastIndexOf(other, false);
  }

  /**
   * Gets the last index of another segment within this segment, ignoring the case.
   *
   * @param other the other segment
   * @return the index relative to this segment, -1 if no match
   */
  public int lastIndexOfIgnoreCase(SubString other) {
    return lastIndexOf(other, true);
  }

  /**
   * Returns whether the segments are equal.
   *
   * @param other the other segment
   * @return true if equal
   */
  public boolean equalsSegment(SubString other) {
    return equalsSegment(other, false);
  }

  /**
   * Returns whether the segments are equal, ignoring the case.
   *
   * @param other the other segment
   * @return true if equal
   */
  public boolean equalsSegmentIgnoreCase(SubString other) {
    return equalsSegment(other, true);
  }


  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    SubString subString = (SubString) o;

    if (beginIndex != subString.beginIndex) return false;
    if (endIndex != subString.endIndex) return false;
    return string.equals(subString.string);
  }

  @Override
  public int hashCode() {
    int result = string.hashCode();
    result = 31 * result + beginIndex;
    result = 31 * result + endIndex;
    return result;
  }

  @Override
  public String toString() {
    return getSegment();
  }


  private int indexOf(SubString other, boolean ignoreCase) {
    int len = other.length;
    int max = endIndex - len;
    for (int ndx = beginIndex; ndx <= max; ++ndx) {
      if (string.regionMatches(ignoreCase, ndx, other.string, other.beginIndex, len)) {
        return ndx - beginIndex;
      }
    }
    return -1;
  }

  private int lastIndexOf(SubString other, boolean ignoreCase) {
    int len = other.length;
    int max = endIndex - len;
    for (int ndx = max; ndx >= beginIndex; --ndx) {
      if (string.regionMatches(ignoreCase, ndx, other.string, other.beginIndex, len)) {
        return ndx - beginIndex;
      }
    }
    return -1;
  }

  private boolean endsWith(SubString other, boolean ignoreCase) {
    int len = other.length;
    int offset = length - len;
    return offset >= 0 &&
           string.regionMatches(ignoreCase, beginIndex + offset, other.string, other.beginIndex, len);
  }

  private boolean equalsSegment(SubString other, boolean ignoreCase) {
    return length == other.length &&
           string.regionMatches(ignoreCase, beginIndex, other.string, other.beginIndex, length);
  }

}
