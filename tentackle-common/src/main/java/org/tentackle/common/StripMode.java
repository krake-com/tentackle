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

package org.tentackle.common;

import java.util.Locale;

/**
 * Stripping mode for strings with respect to a filler character.
 * <p>
 * Each mode corresponds to a bind option (see {@link #getBindOption()} and {@link #fromBindOption(String)}).
 */
public enum StripMode {

  /**
   * No stripping.
   */
  NONE(false, false, Constants.BIND_STRIP_NONE),

  /**
   * Remove leading filler characters.
   */
  LEFT(true, false, Constants.BIND_STRIP_LEFT),

  /**
   * Remove trailing filler characters.
   */
  RIGHT(false, true, Constants.BIND_STRIP_RIGHT),

  /**
   * Remove leading and trailing filler characters.<br>
   * This is the default.
   */
  BOTH(true, true, Constants.BIND_STRIP_BOTH);


  private final boolean stripLeft;
  private final boolean stripRight;
  private final String bindOption;

  StripMode(boolean stripLeft, boolean stripRight, String bindOption) {
    this.stripLeft = stripLeft;
    this.stripRight = stripRight;
    this.bindOption = bindOption;
  }

  /**
   * Returns true if the mode strips the left side.
   *
   * @return true if the mode strips the left side
   */
  public boolean isStripLeft() {
    return stripLeft;
  }

  /**
   * Returns true if the mode strips the right side.
   *
   * @return true if the mode strips the right side
   */
  public boolean isStripRight() {
    return stripRight;
  }

  /**
   * Gets the bind option name.
   *
   * @return the bind option name
   */
  public String getBindOption() {
    return bindOption;
  }

  /**
   * Strips the given text.
   *
   * @param text the original text, null if none
   * @param filler the filler character
   * @return the stripped text, null if none
   */
  public String strip(String text, char filler) {
    if (text != null) {
      if (stripLeft) {
        text = StringHelper.trimLeft(text, filler);
      }
      if (stripRight) {
        text = StringHelper.trimRight(text, filler);
      }
    }
    return text;
  }


  /**
   * Converts a mode string as defined in {@link Constants} to a StripMode.
   *
   * @param s one of {@link Constants#BIND_STRIP_NONE}, {@link Constants#BIND_STRIP_LEFT},
   *          {@link Constants#BIND_STRIP_RIGHT}, {@link Constants#BIND_STRIP_BOTH},
   *          case-insensitive, never null
   * @return the corresponding StripMode
   * @throws TentackleRuntimeException if the string is invalid
   */
  public static StripMode fromBindOption(String s) {
    return switch (s.toUpperCase(Locale.ROOT)) {
      case Constants.BIND_STRIP_NONE -> NONE;
      case Constants.BIND_STRIP_LEFT -> LEFT;
      case Constants.BIND_STRIP_RIGHT -> RIGHT;
      case Constants.BIND_STRIP_BOTH -> BOTH;
      default -> throw new TentackleRuntimeException("invalid strip mode: " + s);
    };
  }

}
