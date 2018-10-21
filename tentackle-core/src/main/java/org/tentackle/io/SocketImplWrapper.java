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

import java.io.FileDescriptor;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.SocketException;
import java.net.SocketImpl;
import java.net.SocketOptions;

/**
 * A wrapping SocketImpl.<br>
 *
 * Nice to wrap SSL sockets to add compression, for example.
 * Notice that wrapped sockets are always connected/bound!
 *
 * @author harald
 */
public class SocketImplWrapper extends SocketImpl {

  private final Socket soc;    // the wrapped socket


  private static class WrappedException extends IOException {

    private static final long serialVersionUID = 1L;

    private WrappedException() {
      super("operation not allowed for wrapped socket");
    }
  }


  /**
   * Creates an impl wrapper that delegates all method invocations
   * to the wrapped socket.
   *
   * @param socket the socket to wrap
   */
  public SocketImplWrapper(Socket socket) {
    this.soc = socket;
  }




  // ------------------- overrides SocketImpl ------------------------------

  @Override
  protected void shutdownInput() throws IOException {
    soc.shutdownInput();
  }

  @Override
  protected void shutdownOutput() throws IOException {
    soc.shutdownOutput();
  }

  @Override
  protected FileDescriptor getFileDescriptor() {
    throw new UnsupportedOperationException();
  }

  @Override
  protected InetAddress getInetAddress() {
    return soc.getInetAddress();
  }

  @Override
  protected int getPort() {
    return soc.getPort();
  }

  @Override
  protected int getLocalPort() {
    return soc.getLocalPort();
  }




  // --------------------- implements SocketImpl -----------------------------

  @Override
  protected void create(boolean stream) throws IOException {
    throw new WrappedException();
  }

  @Override
  protected void connect(String host, int port) throws IOException {
    throw new WrappedException();
  }

  @Override
  protected void connect(InetAddress address, int port) throws IOException {
    throw new WrappedException();
  }

  @Override
  protected void connect(SocketAddress address, int timeout) throws IOException {
    throw new WrappedException();
  }

  @Override
  protected void bind(InetAddress host, int port) throws IOException {
    throw new WrappedException();
  }

  @Override
  protected void listen(int backlog) throws IOException {
    throw new WrappedException();
  }

  @Override
  protected void accept(SocketImpl s) throws IOException {
    throw new WrappedException();
  }

  @Override
  protected InputStream getInputStream() throws IOException {
    return soc.getInputStream();
  }

  @Override
  protected OutputStream getOutputStream() throws IOException {
    return soc.getOutputStream();
  }

  @Override
  protected int available() throws IOException {
    return getInputStream().available();
  }

  @Override
  protected void close() throws IOException {
    soc.close();
  }

  @Override
  protected void sendUrgentData (int data) throws IOException {
    throw new WrappedException();
  }


  // ----------------------- implements SocketOptions -----------------------

  @Override
  public void setOption(int optID, Object value) throws SocketException {

    Boolean bval;
    Integer ival;

    if (value instanceof Boolean) {
      bval = (Boolean) value;
      ival = 0;
    }
    else  {
      ival = (Integer) value;
      bval = ival > 0;
    }


    switch (optID) {

      // case SocketOptions.SO_BINDADDR:  read only!

      case SocketOptions.TCP_NODELAY:
        soc.setTcpNoDelay(bval);
        break;

      case SocketOptions.SO_REUSEADDR:
        soc.setReuseAddress(bval);
        break;

      // case SocketOptions.SO_BROADCAST:  ?? -> not supported
      // case SocketOptions.IP_MULTICAST_IF:
      // case SocketOptions.IP_MULTICAST_IF2:
      // case SocketOptions.IP_MULTICAST_LOOP:

      case SocketOptions.IP_TOS:
        soc.setTrafficClass(ival);
        break;

      case SocketOptions.SO_LINGER:
        soc.setSoLinger(bval, ival);
        break;

      case SocketOptions.SO_TIMEOUT:
        soc.setSoTimeout(ival);
        break;

      case SocketOptions.SO_SNDBUF:
        soc.setSendBufferSize(ival);
        break;

      case SocketOptions.SO_RCVBUF:
        soc.setReceiveBufferSize(ival);
        break;

      case SocketOptions.SO_KEEPALIVE:
        soc.setKeepAlive(bval);
        break;

      case SocketOptions.SO_OOBINLINE:
        soc.setOOBInline(bval);
        break;

      default:
          throw new SocketException("unsupported option ID");
    }
  }


  @Override
  public Object getOption(int optID) throws SocketException {
    switch (optID) {
      case SocketOptions.TCP_NODELAY:
        return soc.getTcpNoDelay();

      case SocketOptions.SO_BINDADDR:
        return soc.getLocalAddress();

      case SocketOptions.SO_REUSEADDR:
        return soc.getReuseAddress();

      // case SocketOptions.SO_BROADCAST:  ?? -> not supported
      // case SocketOptions.IP_MULTICAST_IF:
      // case SocketOptions.IP_MULTICAST_IF2:
      // case SocketOptions.IP_MULTICAST_LOOP:

      case SocketOptions.IP_TOS:
        return soc.getTrafficClass();

      case SocketOptions.SO_LINGER:
        return soc.getSoLinger();

      case SocketOptions.SO_TIMEOUT:
        return soc.getSoTimeout();

      case SocketOptions.SO_SNDBUF:
        return soc.getSendBufferSize();

      case SocketOptions.SO_RCVBUF:
        return soc.getReceiveBufferSize();

      case SocketOptions.SO_KEEPALIVE:
        return soc.getKeepAlive();

      case SocketOptions.SO_OOBINLINE:
        return soc.getOOBInline();

      default:
        throw new SocketException("unsupported option ID");
    }
  }

}
