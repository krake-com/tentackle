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

import org.tentackle.common.InterruptedRuntimeException;
import org.tentackle.io.MutableByteArrayInputStream;
import org.tentackle.log.Logger;
import org.tentackle.misc.PoolSlot;
import org.tentackle.trip.Connection;
import org.tentackle.trip.Deserializer;
import org.tentackle.trip.Dictionary;
import org.tentackle.trip.Serializer;
import org.tentackle.trip.TripFactory;
import org.tentackle.trip.TripStream;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * The loopback client connection pool for testing purposes.<br>
 * The connection pool also maintains the server-side transport
 * (since there is only one connection pool maintained by the client-side transport).
 */
public class LoopbackConnectionPool extends AbstractConnectionPool {

  private static final Logger LOGGER = Logger.get(LoopbackConnectionPool.class);

  private final ByteArrayOutputStream bos = new ByteArrayOutputStream();
  private final MutableByteArrayInputStream bis = new MutableByteArrayInputStream();
  private final Serializer serializer = TripFactory.getInstance().createSerializer(bos);
  private final Deserializer deserializer = TripFactory.getInstance().createDeserializer(bis);

  private final LoopbackTransport serverTransport;

  /** client write queue and server read queue. */
  private final BlockingQueue<byte[]> clientQueue = new LinkedBlockingQueue<>(1);
  /** server write queue and client read queue. */
  private final BlockingQueue<byte[]> serverQueue = new LinkedBlockingQueue<>(1);


  /**
   * Creates a loopback connection pool.
   *
   * @param transport     the transport associated with this pool
   * @param dictionary    the dictionary associated with this pool
   */
  public LoopbackConnectionPool(LoopbackTransport transport, Dictionary dictionary) {
    super(transport, dictionary);
    setMaximumSize(1);
    setIdleMinutes(0);

    // create opposite transport, will also be a loopback transport!
    serverTransport = (LoopbackTransport) TripFactory.getInstance().createTransport(getTransport().getUri());
    serverTransport.accept();
    serverTransport.createServerThread(new AcceptLoop()).start();
    serverTransport.setConnectionPool(this);
  }

  /**
   * Gets the server transport to register the remote objects for tests.
   *
   * @return the server transport
   */
  public LoopbackTransport getServerTransport() {
    return serverTransport;
  }

  /**
   * Sends a request to the server.
   */
  public void request() {
    LOGGER.info("sending request");
    clientQueue.add(bos.toByteArray());
    bos.reset();
  }

  /**
   * Waits for a request from the client.
   *
   * @return the received data
   */
  public byte[] getRequest() {
    LOGGER.info("waiting for request");
    try {
      return clientQueue.take();
    }
    catch (InterruptedException e) {
      throw new InterruptedRuntimeException(e);
    }
    finally {
      LOGGER.info("request received");
    }
  }

  /**
   * Responds to the client.
   *
   * @param response the response data
   */
  public void respond(byte[] response) {
    LOGGER.info("sending response");
    serverQueue.add(response);
  }

  /**
   * Waits for the response from the server and passes it to the client's stream.
   */
  public void getResponse() {
    try {
      LOGGER.info("waiting for response");
      bis.setBuffer(serverQueue.take());
      LOGGER.info("response received");
    }
    catch (InterruptedException e) {
      throw new InterruptedRuntimeException(e);
    }
  }


  @Override
  protected PoolSlot<Connection> createSlot(int slotNumber) {
    if (slotNumber != 0) {
      throw new IllegalArgumentException("slot number must be 0 for loopback connection pool");
    }
    TripStream clientStream = TripFactory.getInstance().createStream(
      TripFactory.getInstance().createDictionary(getTransport().getId(), false), serializer, deserializer);
    return new ConnectionPoolSlot(this, slotNumber,
                                  new LoopbackConnection((LoopbackTransport) getTransport(), clientStream));
  }


  private class AcceptLoop implements Runnable {

    @Override
    public void run() {
      // there is only one connection -> we don't need to loop and wait for other connections
      byte[] request = getRequest();
      serverTransport.getConnectionThreadPool().execute(() -> {
        try {
          serverTransport.createConnectionHandler("loopback",
                                                  new ByteArrayInputStream(request),
                                                  new ByteArrayOutputStream())
                         .run();
        }
        catch (Exception e) {
          LOGGER.warning("cannot handle loopback connection", e);
        }
        catch (Throwable t) {
          LOGGER.severe("unexpected error in loopback handler thread", t);
        }
      });
    }
  }

}
