/**
 * Tentackle - http://www.tentackle.org
 * Copyright (C) 2001-2008 Harald Krake, harald@krake.de, +49 7722 9508-0
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


package org.tentackle.bind;

/**
 * Binding exception.
 * <p>
 * Binding exceptions are unchecked runtime exceptions.
 * 
 * @author harald
 */
public class BindingException extends RuntimeException {

  private static final long serialVersionUID = -8560243373742735009L;

  /**
   * Creates a binding exception.
   *
   * @param cause the cause for the exception
   */
  public BindingException(Throwable cause) {
    super(cause);
  }

  /**
   * Creates a binding exception.
   *
   * @param message a detailed message
   * @param cause the cause for the exception
   */
  public BindingException(String message, Throwable cause) {
    super(message, cause);
  }

  /**
   * Creates a binding exception.
   *
   * @param message a detailed message
   */
  public BindingException(String message) {
    super(message);
  }

  /**
   * Creates a binding exception.
   */
  public BindingException() {
    super();
  }

}
