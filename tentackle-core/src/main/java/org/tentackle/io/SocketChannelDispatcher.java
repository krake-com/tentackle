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
 */
public class SocketChannelDispatcher {

  private static final Logger LOGGER = Logger.get(SocketChannelDispatcher.class);


  private static final AtomicInteger THREAD_COUNTER = new AtomicInteger();
  private static final Cleaner CLEANER = Cleaner.create();        // instead of deprecated finalize()

  /**
   * Holds the resources and state to be cleaned up.
   */
  private static class Resources implements Runnable {

    private final String name;

    private ServerSocketChannel serverChannel;
    private ExecutorService executorService;
    private SocketChannelDispatcher me;                    // != null if normal shutdown, else cleanup

    Resources(String name, SocketAddress socketAddress, StandardProtocolFamily protocolFamily) {
      this.name = name;
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
      try {
        if (me == null) {
          LOGGER.warning("closing unreferenced channel dispatcher {0}", name);
        }
        shutdownExecutorService();
        closeServerChannel();
      }
      finally {
        me = null;    // in case programmatic shutdown
      }
    }

    private void closeServerChannel() {
      if (serverChannel != null) {
        try {
          serverChannel.close();
        }
        catch (IOException | RuntimeException cx) {
          LOGGER.warning("closing server socket channel failed", cx);
        }
      }
      serverChannel = null;
    }

    private void shutdownExecutorService() {
      if (executorService != null) {
        try {
          executorService.shutdown();
        }
        catch (RuntimeException rx) {
          LOGGER.warning("executor service shutdown failed", rx);
        }
      }
      executorService = null;
    }
  }

  private final Resources resources;
  private final Cleaner.Cleanable cleanable;
  private final String name;
  private final SocketAddress socketAddress;
  private final Supplier<Consumer<SocketChannel>> handlerFactory;
  private final StandardProtocolFamily protocolFamily;


  /**
   * Creates a server socket channel dispatcher.
   *
   * @param name the dispatcher's name
   * @param socketAddress the socket address to listen on
   * @param handlerFactory the socket channel handler factory
   */
  public SocketChannelDispatcher(String name, SocketAddress socketAddress, Supplier<Consumer<SocketChannel>> handlerFactory) {
    this.name = name;
    this.socketAddress = socketAddress;
    this.handlerFactory = handlerFactory;
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

    resources = new Resources(name, socketAddress, protocolFamily);
    cleanable = CLEANER.register(this, resources);

    resources.executorService.execute(new Dispatcher());   // dispatcher thread itself runs within the executor's pool
  }

  /**
   * Shutdown the dispatcher.
   */
  public void shutdown() {
    resources.me = this;      // programmatic close, not a GC cleanup
    cleanable.clean();
  }


  private class Dispatcher implements Runnable {

    @Override
    public void run() {
      Thread.currentThread().setName(name + "(dispatcher)");
      try {
        LOGGER.info("accepting connections @ {0}", socketAddress);
        ExecutorService executorService;
        ServerSocketChannel serverChannel;
        while (((executorService = resources.executorService) != null) && (serverChannel = resources.serverChannel) != null) {
          try {
            SocketChannel channel = serverChannel.accept();
            executorService.execute(() -> handlerFactory.get().accept(channel));
          }
          catch (AsynchronousCloseException ax) {
            LOGGER.warning("channel closed by client", ax);
          }
        }
        LOGGER.info("{0} shutdown", name);
      }
      catch (IOException | RuntimeException ex) {
        LOGGER.severe("server communication failure -> shutdown", ex);
      }
      finally {
        shutdown();
      }
    }
  }

}
