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

package org.tentackle.io;

import org.tentackle.common.TentackleRuntimeException;
import org.tentackle.log.Logger;

import java.io.IOException;
import java.lang.ref.Cleaner;
import java.net.Inet6Address;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.StandardProtocolFamily;
import java.net.UnixDomainSocketAddress;
import java.nio.channels.AsynchronousCloseException;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * A simple implementation of a dispatcher accepting incoming socket channels from client connections.
 * <p>
 * Each accepted channel is passed to a new handler created by the handler factory and processed in a thread
 * of a cached thread pool. The accept loop runs in that pool as well.
 * Supports TCP (IPv4 and IPv6) and Unix domain sockets.<br>
 * The dispatcher should be shut down explicitly. If it becomes unreachable without, it is closed automatically
 * (with a warning).
 */
public class SocketChannelDispatcher {

  private static final Logger LOGGER = Logger.get(SocketChannelDispatcher.class);


  private static final AtomicInteger THREAD_COUNTER = new AtomicInteger();
  private static final Cleaner CLEANER = Cleaner.create();        // instead of deprecated finalize()

  /**
   * Holds the resources and state to be cleaned up.
   * <p>
   * Nothing reachable from here must reference the {@link SocketChannelDispatcher} this belongs to.
   * The cleaner holds the cleanup action, so any such reference would keep the dispatcher strongly
   * reachable and its cleanup would never run.
   * This is why the accept loop lives in here and not in an inner class of the dispatcher:
   * the loop runs in the executor below, which in turn is owned by this cleanup action.
   */
  private static class Resources implements Runnable {

    private final String name;
    private final SocketAddress socketAddress;
    private final Supplier<Consumer<SocketChannel>> handlerFactory;

    // written by the cleaner thread, read by the accept loop
    private volatile ServerSocketChannel serverChannel;
    private volatile ExecutorService executorService;
    private volatile boolean shutdownRequested;   // true if closed deliberately, false if a GC cleanup

    Resources(String name, SocketAddress socketAddress, StandardProtocolFamily protocolFamily,
              Supplier<Consumer<SocketChannel>> handlerFactory) {
      this.name = name;
      this.socketAddress = socketAddress;
      this.handlerFactory = handlerFactory;
      try {
        serverChannel = ServerSocketChannel.open(protocolFamily);
        serverChannel.bind(socketAddress);
      }
      catch (IOException iox) {
        closeServerChannel();
        throw new TentackleRuntimeException("creating server channel failed", iox);
      }

      executorService = Executors.newCachedThreadPool(runnable -> {
        Thread t = new Thread(runnable, name + "(" + THREAD_COUNTER.incrementAndGet() + ")");
        t.setDaemon(true); // don't inhibit termination of JVM!
        return t;
      });
    }

    @Override
    public void run() {
      if (!shutdownRequested) {
        // no shutdown() and no failed accept loop: the dispatcher was dropped without being closed
        LOGGER.warning("closing unreferenced channel dispatcher {0}", name);
        shutdownRequested = true;
      }
      shutdownExecutorService();
      closeServerChannel();
    }

    /**
     * Accepts incoming connections until the server channel is closed.
     */
    private void accept() {
      Thread.currentThread().setName(name + "(dispatcher)");
      try {
        LOGGER.info("accepting connections @ {0}", socketAddress);
        ExecutorService executor;
        ServerSocketChannel channel;
        while ((executor = executorService) != null && (channel = serverChannel) != null) {
          try {
            SocketChannel socketChannel = channel.accept();
            executor.execute(() -> handlerFactory.get().accept(socketChannel));
          }
          catch (AsynchronousCloseException ax) {
            if (shutdownRequested) {
              break;
            }
            LOGGER.warning("channel closed by client unexpectedly", ax);
          }
        }
        LOGGER.info("{0} shutdown", name);
      }
      catch (IOException | RuntimeException ex) {
        if (!shutdownRequested) {
          LOGGER.severe("server communication failure -> shutdown", ex);
        }
      }
      finally {
        shutdownRequested = true;   // released deliberately, not by the GC
        run();
      }
    }

    private void closeServerChannel() {
      ServerSocketChannel channel = serverChannel;
      serverChannel = null;
      if (channel != null) {
        try {
          channel.close();
        }
        catch (IOException | RuntimeException cx) {
          LOGGER.warning("closing server socket channel failed", cx);
        }
      }
    }

    private void shutdownExecutorService() {
      ExecutorService executor = executorService;
      executorService = null;
      if (executor != null) {
        try {
          executor.shutdown();
        }
        catch (RuntimeException rx) {
          LOGGER.warning("executor service shutdown failed", rx);
        }
      }
    }
  }

  private final Resources resources;
  private final Cleaner.Cleanable cleanable;


  /**
   * Creates a server socket channel dispatcher.
   *
   * @param name the dispatcher's name
   * @param socketAddress the socket address to listen on
   * @param handlerFactory the socket channel handler factory
   * @throws TentackleRuntimeException if the address type is not supported or the server channel cannot be bound
   */
  public SocketChannelDispatcher(String name, SocketAddress socketAddress, Supplier<Consumer<SocketChannel>> handlerFactory) {
    StandardProtocolFamily protocolFamily;
    if (socketAddress instanceof UnixDomainSocketAddress) {
      protocolFamily = StandardProtocolFamily.UNIX;
    }
    else if (socketAddress instanceof InetSocketAddress inetSocketAddress) {
      if (inetSocketAddress.getAddress() instanceof Inet6Address) {
        protocolFamily = StandardProtocolFamily.INET6;
      }
      else {
        protocolFamily = StandardProtocolFamily.INET;
      }
    }
    else {
      throw new TentackleRuntimeException("unsupported socket address type: " + socketAddress);
    }

    resources = new Resources(name, socketAddress, protocolFamily, handlerFactory);
    cleanable = CLEANER.register(this, resources);

    // the dispatcher thread itself runs within the executor's pool.
    // 'resources::accept' captures the resources only, never this dispatcher (see Resources)
    resources.executorService.execute(resources::accept);
  }

  /**
   * Shuts down the dispatcher.<br>
   * Closes the server channel and shuts down the thread pool.
   * Connections already accepted are not closed.
   */
  public void shutdown() {
    resources.shutdownRequested = true;   // programmatic close, not a GC cleanup
    cleanable.clean();
  }

  /**
   * Returns whether {@link #shutdown()} was invoked.
   *
   * @return true if shutdown completed or in progress
   */
  public boolean isShutdown() {
    return resources.shutdownRequested;
  }

}
