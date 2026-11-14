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

package org.tentackle.trip.transport;

import org.tentackle.log.Logger;
import org.tentackle.trip.PreAuthenticationPolicy;
import org.tentackle.trip.RejectionLog;
import org.tentackle.trip.Transport;

import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Subjects a connection that carries TRIP connections, such as a QUIC connection with its streams,
 * to the {@link PreAuthenticationPolicy} of the transport.
 * <p>
 * The policy admits each TRIP connection, i.e. each stream, before anything is read from it. A QUIC
 * connection, however, exists before its first stream and outlives its last one, and its keep-alive
 * keeps it open as long as the peer wants. Without the guard, a peer could hold any number of
 * them without ever opening a stream.
 * <p>
 * The guard admits the carrying connection itself, as an unauthenticated connection of the peer's host:
 * <ul>
 *   <li>it counts against the connection limits of the policy and may be evicted, which closes it,</li>
 *   <li>it must open its first stream within the policy's first message timeout,</li>
 *   <li>and, once its last stream is closed, its next one within the idle timeout.</li>
 * </ul>
 * While streams are open, the connection is busy, and its streams' own admissions govern it.
 * Once a client is authenticated on any of its streams, the guard doesn't restrict it anymore.
 */
public final class MultiplexedConnectionGuard {

  private static final Logger LOGGER = Logger.get(MultiplexedConnectionGuard.class);

  private final String name;
  private final Set<ConnectionHandler> handlers;
  private final AtomicBoolean closed;
  private volatile PreAuthenticationPolicy.Admission admission;
  private volatile boolean authenticated;
  private int openStreams;


  /**
   * Admits a connection carrying TRIP connections.
   *
   * @param transport the server transport
   * @param name the name of the connection, the peer's host is derived from it (see {@link TransportClientInfo#getHost(String)})
   * @param closer closes the connection, must not block
   * @return the guard, null if the policy refused the connection and it was closed already
   */
  public static MultiplexedConnectionGuard admit(Transport transport, String name, Runnable closer) {
    Objects.requireNonNull(closer, "missing closer");
    MultiplexedConnectionGuard guard = new MultiplexedConnectionGuard(name);
    PreAuthenticationPolicy policy = transport.getPreAuthenticationPolicy();
    if (policy != null) {
      Runnable close = () -> {
        if (guard.closed.compareAndSet(false, true)) {
          try {
            closer.run();
          }
          catch (RuntimeException ex) {
            LOGGER.warning("closing " + name + " failed", ex);
          }
        }
        // don't rely on the notification of the closed connection: it may have been closed already
        guard.release();
      };
      guard.admission = policy.admit(TransportClientInfo.getHost(name), close, guard::isAuthenticated);
      if (guard.admission == null) {
        if (RejectionLog.isLoggable()) {
          LOGGER.warning("{0} refused: too many unauthenticated connections", name);
        }
        close.run();
        return null;
      }
    }
    return guard;
  }


  private MultiplexedConnectionGuard(String name) {
    this.name = name;
    handlers = ConcurrentHashMap.newKeySet();
    closed = new AtomicBoolean();
  }

  /**
   * Notifies that a stream was opened.
   *
   * @param handler the connection handler of the stream
   */
  public void streamOpened(Runnable handler) {
    if (handler instanceof ConnectionHandler connectionHandler) {
      handlers.add(connectionHandler);
    }
    PreAuthenticationPolicy.Admission currentAdmission = admission;
    synchronized (this) {
      if (openStreams++ == 0 && currentAdmission != null) {
        currentAdmission.messageReceived();
      }
    }
  }

  /**
   * Notifies that a stream was closed.
   *
   * @param handler the connection handler of the stream
   */
  public void streamClosed(Runnable handler) {
    boolean clientAuthenticated = isAuthenticated();    // before the handler is forgotten
    if (handler instanceof ConnectionHandler connectionHandler) {
      handlers.remove(connectionHandler);
    }
    PreAuthenticationPolicy.Admission currentAdmission = admission;
    if (currentAdmission != null) {
      if (clientAuthenticated) {
        release();
      }
      else {
        synchronized (this) {
          if (--openStreams == 0) {
            currentAdmission.callCompleted();
          }
        }
      }
    }
  }

  /**
   * Notifies that the connection was closed.<br>
   * Releases the admission.
   */
  public void closed() {
    closed.set(true);
    release();
  }

  /**
   * Returns whether the client was authenticated on one of the connection's streams.
   *
   * @return true if authenticated
   */
  public boolean isAuthenticated() {
    if (!authenticated) {
      for (ConnectionHandler handler : handlers) {
        if (handler.isAuthenticated()) {
          authenticated = true;
          break;
        }
      }
    }
    return authenticated;
  }

  @Override
  public String toString() {
    return name;
  }


  private void release() {
    PreAuthenticationPolicy.Admission currentAdmission = admission;
    if (currentAdmission != null) {
      admission = null;
      currentAdmission.release();
    }
  }

}
