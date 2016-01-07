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


package org.tentackle.locale;


/**
 * Bundle exception.
 * 
 * @author harald
 */
public class BundleException extends RuntimeException {

  private static final long serialVersionUID = -5951808213011320583L;

  /**
   * Creates a bundle exception.
   *
   * @param cause the cause for the exception
   */
  public BundleException(Throwable cause) {
    super(cause);
  }

  /**
   * Creates a bundle exception.
   *
   * @param message a detailed message
   * @param cause the cause for the exception
   */
  public BundleException(String message, Throwable cause) {
    super(message, cause);
  }

  /**
   * Creates a bundle exception.
   *
   * @param message a detailed message
   */
  public BundleException(String message) {
    super(message);
  }

  /**
   * Creates a bundle exception.
   */
  public BundleException() {
    super();
  }

}
