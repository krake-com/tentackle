/*
 * Tentackle - a framework for java desktop applications
 * http://www.tentackle.org
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

package org.tentackle.prefs;

import java.util.prefs.BackingStoreException;

/**
 * Exception thrown whenever a preferences node became invalid and needs
 * to be reloaded because it has been updated or removed by another thread or JVM.
 *
 * @author harald
 */
public class PreferencesInvalidException extends BackingStoreException {

  private static final long serialVersionUID = 1L;

  /**
   * Constructs a PreferencesInvalidException with the specified detail message.
   *
   * @param s the detail message.
   */
  public PreferencesInvalidException(String s) {
    super(s);
  }

  /**
   * Constructs a PreferencesInvalidException with the specified cause.
   *
   * @param cause the cause
   */
  public PreferencesInvalidException(Throwable cause) {
    super(cause);
  }

}
