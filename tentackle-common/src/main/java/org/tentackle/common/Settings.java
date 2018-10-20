/*
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

package org.tentackle.common;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

/**
 * Global settings.
 *
 * @author harald
 */
public class Settings {

  private static Charset encodingCharset = StandardCharsets.UTF_8;


  /**
   * Gets the encoding used to read and write files.<br>
   * Usually set by the maven plugins from "project.build.sourceEncoding".
   *
   * @return the charset, default is {@link StandardCharsets#UTF_8}
   */
  public static Charset getEncodingCharset() {
    return encodingCharset;
  }

  /**
   * Sets the encoding used to read and write files.
   *
   * @param encodingCharset the charset, default is {@link StandardCharsets#UTF_8}
   */
  public static void setEncodingCharset(Charset encodingCharset) {
    Settings.encodingCharset = encodingCharset;
  }


  private Settings() {
  }
}
