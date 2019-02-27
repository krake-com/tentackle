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

import java.util.Arrays;

/**
 * Object wrapper for a char array.
 */
public class CharArray {

  private final char[] characters;

  public CharArray(char[] characters) {
    this.characters = characters;
  }

  public char[] getCharacters() {
    return characters;
  }

  public void clear() {
    if (characters != null) {
      for (int i=0; i < characters.length; i++) {
        characters[i] = ' ';
      }
    }
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    CharArray charArray = (CharArray) o;
    return Arrays.equals(characters, charArray.characters);
  }

  @Override
  public int hashCode() {
    return Arrays.hashCode(characters);
  }
}
