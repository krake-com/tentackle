/*
 * Tentackle - https://tentackle.org.
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
 *
 */

package org.tentackle.bind;

/**
 * A listener invoked before the binding will update the view with the model's value.
 *
 * @author harald
 */
@FunctionalInterface
public interface ToViewListener {

  /**
   * The view will be updated by the model.
   *
   * @param e the binding event
   * @throws BindingVetoException if the view should not be updated
   */
  void toView(BindingEvent e) throws BindingVetoException;

}
