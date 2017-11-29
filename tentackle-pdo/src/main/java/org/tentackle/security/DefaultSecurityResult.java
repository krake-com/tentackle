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


package org.tentackle.security;

/**
 * Default implementation of a security result.
 *
 * @author harald
 */
public class DefaultSecurityResult implements SecurityResult {

  private final boolean accepted;   // true if accepted
  private final boolean byDefault;  // true if default result
  private final String message;     // further explanation


  /**
   * Creates a security result.
   *
   * @param message the message, null if none
   * @param accepted true if accepted, else denied
   * @param byDefault true if result was created by default
   */
  public DefaultSecurityResult(String message, boolean accepted, boolean byDefault) {
    this.message = message;
    this.accepted = accepted;
    this.byDefault = byDefault;
  }


  @Override
  public String explain(String defaultMessage) {
    return message == null ? defaultMessage : (defaultMessage + "\n" + message);
  }

  @Override
  public boolean isAccepted() {
    return accepted;
  }

  @Override
  public boolean isDefault() {
    return byDefault;
  }

  @Override
  public String toString() {
    StringBuilder buf = new StringBuilder();
    if (accepted) {
      buf.append("accepted");
    }
    else {
      buf.append("denied");
    }
    if (byDefault) {
      buf.append(" by default");
    }
    if (message != null) {
      buf.append(" '").append(message).append("'");
    }
    return buf.toString();
  }

}
