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

package org.tentackle.security;


/**
 * Represents a result from the {@link SecurityManager} for a privilege request.
 *
 * @author harald
 */
public interface SecurityResult {

  /**
   * Denotes whether a privilege is accepted.
   *
   * @return true if accepted
   */
  boolean isAccepted();

  /**
   * Returns whether the result was created by default.<br>
   *
   * @return true if no rules applied
   */
  boolean isDefault();

  /**
   * Formats a string for an explanation of rule that fired.<br>
   * The default explanation will be returned unchanged if there is no message in the rule.
   *
   * @param defaultMessage the default message if not changed by this result
   * @return the formatted message or defaultMessage unchanged
   */
  String explain(String defaultMessage);

}
