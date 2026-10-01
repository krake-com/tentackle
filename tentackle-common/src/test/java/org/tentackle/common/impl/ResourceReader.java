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

package org.tentackle.common.impl;

import org.tentackle.common.StringHelper;

import java.io.IOException;

/**
 * Reads a resource relative to this package without passing the caller explicitly.
 */
public final class ResourceReader {

  /**
   * Reads the text of {@code readtext.txt} in this package.
   *
   * @return the text
   * @throws IOException if reading failed
   */
  public static String readText() throws IOException {
    return StringHelper.readTextFromResource(null, "readtext.txt");
  }

  private ResourceReader() {}

}
