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

package org.tentackle.daemon;

/**
 * Something that can be terminated gracefully.<br>
 * Useful for {@link Thread}s.
 *
 * @author harald
 */
public interface Terminatable {

  /**
   * Terminates the terminatable.<br>
   * Requests for termination and waits until terminatable has terminated.
   */
  void terminate();

  /**
   * Requests to terminate.<br>
   * Does not wait and returns immediately.
   */
  void requestTermination();

  /**
   * Returns whether termination has been requested.<br>
   * Can be used to determine whether the terminatable terminated intentionally.
   *
   * @return true if requested
   */
  boolean isTerminationRequested();

}
