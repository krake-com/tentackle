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

/**
 * A short and a long text.
 * <p>
 * A {@code ShortLongText} is something very simple and common:
 * a short string and a longer, more detailed description of
 * an object.
 *
 * @author harald
 */
public interface ShortLongText {

  /**
   * Gets the short text.<br>
   * Usually implemented as {@link Object#toString()}.
   * @return the short text
   */
  String getShortText();


  /**
   * Gets the long text.<br>
   * Usually something like {@code object.getName()}.
   * @return the long text
   */
  String getLongText();

}
