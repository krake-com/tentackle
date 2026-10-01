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

package org.tentackle.log;

import org.tentackle.common.ParameterString;

import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Base implementation of a mapped diagnostic context.
 *
 * @author harald
 */
public abstract class AbstractMappedDiagnosticContext implements MappedDiagnosticContext {

  /**
   * Creates a mapped diagnostic context.
   */
  public AbstractMappedDiagnosticContext() {
    // see -Xlint:missing-explicit-ctor since Java 16
  }

  /**
   * Gets the map.
   * <p>
   * Note: may be a copy of the internal map,
   * i.e. modifications may not show up in the real MDC.
   *
   * @return the key/value map, never null
   */
  public abstract Map<String, String> getContext();


  @Override
  public Set<String> getKeys() {
    return getContext().keySet();
  }


  /**
   * Returns the entries as a string.<br>
   * The format is that of a {@link ParameterString}, i.e. {@code key='value', ...}.
   *
   * @return the string representation
   * @see ParameterString
   */
  @Override
  public String toString() {
    ParameterString parStr = new ParameterString();
    for (Map.Entry<String, String> entry: getContext().entrySet()) {
      parStr.setParameter(entry.getKey(), entry.getValue());
    }
    return parStr.toString();
  }

  
  @Override
  public boolean matchesPattern(Pattern pattern) {
    return pattern.matcher(toString()).matches();
  }

}
