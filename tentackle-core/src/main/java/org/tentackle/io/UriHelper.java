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

package org.tentackle.io;

import org.tentackle.log.Logger;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * Some URI helper methods.
 */
public class UriHelper {

  private static final Logger LOGGER = Logger.get(UriHelper.class);


  /**
   * Gets a map of key-value pairs from a URI's query string.
   *
   * @param query the query string
   * @param converter the converter from strings to values
   * @return the map of query parameters
   */
  public static <T> Map<String, T> getQueryParameters(String query, Function<String, T> converter) {
    Map<String, T> map = new HashMap<>();
    if (query != null) {
      String[] pairs = query.split("&");
      for (String pair : pairs) {
        String[] keyValue = pair.split("=");
        if (keyValue.length == 2) {
          try {
            map.put(keyValue[0], converter.apply(keyValue[1]));
          }
          catch (RuntimeException ex) {
            // don't log as an error since this may be expected for mixed types
            LOGGER.fine("failed to convert query parameter {0} -> ignored", pair, ex);
          }
        }
        else {
          LOGGER.fine("invalid query parameter {0} -> ignored", pair);
        }
      }
    }
    return map;
  }


  private UriHelper() {}
}
