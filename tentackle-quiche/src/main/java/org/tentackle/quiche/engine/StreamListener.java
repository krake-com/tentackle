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

package org.tentackle.quiche.engine;

/**
 * Notified when the peer opens a stream.
 */
@FunctionalInterface
public interface StreamListener {

  /**
   * Invoked once per stream the peer opens, as soon as its first data arrives.
   * <p>
   * <strong>Runs on the endpoint's event loop thread</strong> (or on the thread installing the listener, for
   * the streams that were already open by then). Everything else on this endpoint waits while it runs, so an
   * implementation must hand the stream to another thread and return, never read from it here. Nor must it
   * throw: an exception escaping into the event loop fails the whole endpoint.
   *
   * @param stream the new stream
   */
  void streamOpened(QuicheStream stream);

}
