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

package org.tentackle.pdo;

import org.tentackle.common.Timestamp;

import java.io.Serializable;


/**
 * The edited-by token lock info.<br>
 * Holds the columns editedBy, editedSince and editedExpiry in one DTO.
 *
 * @param editedBy     the object ID of the user holding the token, 0 = unlocked
 * @param editedSince  the timestamp when editing started
 * @param editedExpiry the timestamp when token will expire
 */
public record TokenLockInfo(long editedBy, Timestamp editedSince, Timestamp editedExpiry) implements Serializable {

  private static final long serialVersionUID = 438202973417850958L;

  @Override
  public String toString() {
    return "editedBy=" + editedBy + ", since=" + editedSince + ", expiry=" + editedExpiry;
  }

}
