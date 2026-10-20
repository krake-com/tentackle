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
import org.tentackle.trip.Transport;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketAddress;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;

/**
 * The server socket accept-loop for incoming TCP connections.
 * <p>
 * The loop runs in a regular thread and dispatches each incoming connection
 * to a virtual connection handler thread, which in turn reads the data
 * from the input stream and dispatches to the remote call handlers.
 * The call handlers run in regular threads again.
 */
public class SocketAcceptLoop implements Runnable {

  private static final Logger LOGGER = Logger.get(SocketAcceptLoop.class);

  private static final long EXCEPTION_THROTTLE_WINDOW = 5000L;
  private static final int EXCEPTION_THROTTLE_BURST = 10;
  private static final long EXCEPTION_THROTTLE_DELAY = 10000L;

  private final Transport transport;
  private final ServerSocket serverSocket;                  // server socket to accept connections from

  private long lastExceptionTime;                           // the last time an exception occurred
  private int recentExceptionCount;                         // the number of exceptions within the throttle window

  /**
   * Creates the server socket accept loop.
   *
   * @param transport the transport associated with this accept-loop
   * @param serverSocket the server socket to accept connections from
   */
  public SocketAcceptLoop(Transport transport, ServerSocket serverSocket) {
    this.transport = Objects.requireNonNull(transport);
    this.serverSocket = Objects.requireNonNull(serverSocket);
  }

  @Override
  public void run() {
    try {
      accept(transport.getConnectionThreadPool());
    }
    finally {
      LOGGER.warning("accept loop terminated");
      if (!serverSocket.isClosed()) {
        try {
          // accept loop failed -> close server socket for sure
          serverSocket.close();
        }
        catch (IOException e) {
          LOGGER.severe("cannot close server socket: " + serverSocket, e);
        }
      }
    }
  }

  /**
   * Accepts connections from the server socket and executes connection handlers.
   *
   * @param connectionThreadPool the thread pool for connection handlers
   */
  private void accept(ExecutorService connectionThreadPool) {
    while (!transport.isClosed()) {
      Socket socket = null;
      try {
        socket = serverSocket.accept();
        try {
          final Socket sock = socket;
          connectionThreadPool.execute(() -> {
            try {
              if (transport instanceof TcpTransport tcpTransport) {
                // in the connection's thread: a failure concerns this connection only, not the accept loop
                tcpTransport.configureSocket(sock);
              }
              SocketAddress remoteSocketAddress = sock.getRemoteSocketAddress();
              String connectionName = remoteSocketAddress != null ? remoteSocketAddress.toString() : "0.0.0.0";
              transport.createConnectionHandler(connectionName, sock.getInputStream(), sock.getOutputStream()).run();
            }
            catch (Exception e) {
              LOGGER.warning("cannot handle connection: " + sock, e);
            }
            catch (Throwable t) {
              LOGGER.severe("unexpected error in connection handler thread: " + sock, t);
            }
            finally {
              // connection is closed, some server error, whatever
              try {
                sock.close();
              }
              catch (IOException e) {
                LOGGER.warning("cannot close socket: " + sock, e);
              }
            }
          });
        }
        catch (RejectedExecutionException e) {
          closeSocket(socket);
        }
      }
      catch (Throwable t) {
        // accept failed
        if (serverSocket.isClosed()) {
          LOGGER.info("server socket closed -> accept loop terminated");
          break;
        }

        closeSocket(socket); // just in case some other exception except RejectedExecutionException ??

        if (t instanceof Error error) {
          throw error;
        }

        // some other exception -> throttle loop on exceptions
        long now = System.currentTimeMillis();
        if (lastExceptionTime == 0L || (now - lastExceptionTime) > EXCEPTION_THROTTLE_WINDOW) {
          // first exception or longer ago
          lastExceptionTime = now;
          recentExceptionCount = 0;
        }
        else {
          // we're within burst window -> throttle
          LOGGER.warning("accept loop throttled due to too many exceptions: {0}", t);
          if (++recentExceptionCount >= EXCEPTION_THROTTLE_BURST) {
            try {
              Thread.sleep(EXCEPTION_THROTTLE_DELAY);
            }
            catch (InterruptedException ignore) {
              LOGGER.warning("interrupted while throttling accept loop -> ignored");
            }
          }
        }
      }
    }
  }


  /**
   * Closes the given socket, ignoring any exceptions.
   *
   * @param socket the socket to close, or null
   */
  private static void closeSocket(Socket socket) {
    if (socket != null) {
      try {
        LOGGER.fine("closing socket: " + socket);
        socket.close();
      }
      catch (IOException ex) {
        // eat exception
        LOGGER.warning("cannot close socket: " + socket, ex);
      }
    }
  }

}
