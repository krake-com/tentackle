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

import java.io.Closeable;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.PortUnreachableException;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A UDP relay between clients and one server, standing in for a router that forwards each flow on a path
 * of its own.
 * <p>
 * Every client address gets an upstream socket of its own, so the server sees one address per client
 * flow, just like behind a NAT. A flow can be black-holed by its client port, in both directions, which is
 * what a flow hashed onto a path that stopped delivering looks like to both ends: the others keep going.
 */
public class UdpRelay implements Closeable {

  private final InetSocketAddress server;
  private final DatagramChannel downstream;                           // where the clients send to
  private final Selector selector;
  private final Map<InetSocketAddress, DatagramChannel> upstreams;    // by client address
  private final Map<DatagramChannel, InetSocketAddress> clients;      // by upstream socket
  private final Set<Integer> blackholed;                              // client ports
  private final Thread thread;
  private volatile boolean closed;

  /**
   * Starts a relay on a loopback port of its own.
   *
   * @param server the server to relay to
   * @throws IOException if a socket cannot be opened
   */
  public UdpRelay(InetSocketAddress server) throws IOException {
    this.server = server;
    upstreams = new ConcurrentHashMap<>();
    clients = new ConcurrentHashMap<>();
    blackholed = ConcurrentHashMap.newKeySet();
    selector = Selector.open();
    downstream = DatagramChannel.open();
    downstream.bind(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0));
    downstream.configureBlocking(false);
    downstream.register(selector, SelectionKey.OP_READ);
    thread = Thread.ofPlatform().daemon().name("UDP relay").start(this::run);
  }

  /**
   * Gets the address the clients connect to instead of the server.
   *
   * @return the address
   */
  public InetSocketAddress getAddress() {
    try {
      return (InetSocketAddress) downstream.getLocalAddress();
    }
    catch (IOException e) {
      throw new IllegalStateException(e);
    }
  }

  /**
   * Drops every datagram of a flow from now on, in both directions.
   *
   * @param clientPort the client's source port
   */
  public void blackhole(int clientPort) {
    blackholed.add(clientPort);
  }

  /**
   * Gets the number of client flows seen so far.
   *
   * @return the number of flows
   */
  public int getFlowCount() {
    return upstreams.size();
  }

  @Override
  public void close() {
    closed = true;
    selector.wakeup();
    try {
      thread.join(1000);
      selector.close();
      downstream.close();
      for (DatagramChannel upstream : upstreams.values()) {
        upstream.close();
      }
    }
    catch (IOException | InterruptedException e) {
      // test teardown, nothing left to do
    }
  }


  private void run() {
    ByteBuffer buffer = ByteBuffer.allocate(65535);
    try {
      while (!closed) {
        selector.select(100);
        selector.selectedKeys().clear();
        relayUpstream(buffer);
        for (Map.Entry<DatagramChannel, InetSocketAddress> entry : clients.entrySet()) {
          relayDownstream(buffer, entry.getKey(), entry.getValue());
        }
      }
    }
    catch (IOException e) {
      if (!closed) {
        throw new IllegalStateException("the UDP relay failed", e);
      }
    }
  }

  private void relayUpstream(ByteBuffer buffer) throws IOException {
    SocketAddress source;
    while ((source = receive(downstream, buffer)) != null) {
      InetSocketAddress client = (InetSocketAddress) source;
      if (blackholed.contains(client.getPort())) {
        continue;
      }
      DatagramChannel upstream = upstreams.get(client);
      if (upstream == null) {
        upstream = DatagramChannel.open();
        upstream.bind(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0));
        upstream.configureBlocking(false);
        upstream.register(selector, SelectionKey.OP_READ);
        upstreams.put(client, upstream);
        clients.put(upstream, client);
      }
      upstream.send(buffer, server);
    }
  }

  private void relayDownstream(ByteBuffer buffer, DatagramChannel upstream, InetSocketAddress client)
          throws IOException {
    while (receive(upstream, buffer) != null) {
      if (!blackholed.contains(client.getPort())) {
        downstream.send(buffer, client);
      }
    }
  }

  private static SocketAddress receive(DatagramChannel channel, ByteBuffer buffer) throws IOException {
    while (true) {
      buffer.clear();
      try {
        SocketAddress source = channel.receive(buffer);
        buffer.flip();
        return source;
      }
      catch (PortUnreachableException e) {
        // an earlier datagram was relayed to a client port that is closed by now, e.g. the one of a path
        // that failed validation, and bounced off an ICMP "port unreachable". On Windows this surfaces on
        // the next receive(), see QuicheEndpoint. A router does not care, and this relay must not die of it,
        // or every flow would stall for good.
      }
    }
  }

}
