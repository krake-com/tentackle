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

  private final Socket socket;    // the wrapped socket


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
    this.socket = socket;
  }




  // ------------------- overrides SocketImpl ------------------------------

  @Override
  protected void shutdownInput() throws IOException {
    socket.shutdownInput();
  }

  @Override
  protected void shutdownOutput() throws IOException {
    socket.shutdownOutput();
  }

  @Override
  protected FileDescriptor getFileDescriptor() {
    throw new UnsupportedOperationException();
  }

  @Override
  protected InetAddress getInetAddress() {
    return socket.getInetAddress();
  }

  @Override
  protected int getPort() {
    return socket.getPort();
  }

  @Override
  protected int getLocalPort() {
    return socket.getLocalPort();
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
    return socket.getInputStream();
  }

  @Override
  protected OutputStream getOutputStream() throws IOException {
    return socket.getOutputStream();
  }

  @Override
  protected int available() throws IOException {
    return getInputStream().available();
  }

  @Override
  protected void close() throws IOException {
    socket.close();
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
        socket.setTcpNoDelay(bval);
        break;

      case SocketOptions.SO_REUSEADDR:
        socket.setReuseAddress(bval);
        break;

      // case SocketOptions.SO_BROADCAST:  ?? -> not supported
      // case SocketOptions.IP_MULTICAST_IF:
      // case SocketOptions.IP_MULTICAST_IF2:
      // case SocketOptions.IP_MULTICAST_LOOP:

      case SocketOptions.IP_TOS:
        socket.setTrafficClass(ival);
        break;

      case SocketOptions.SO_LINGER:
        socket.setSoLinger(bval, ival);
        break;

      case SocketOptions.SO_TIMEOUT:
        socket.setSoTimeout(ival);
        break;

      case SocketOptions.SO_SNDBUF:
        socket.setSendBufferSize(ival);
        break;

      case SocketOptions.SO_RCVBUF:
        socket.setReceiveBufferSize(ival);
        break;

      case SocketOptions.SO_KEEPALIVE:
        socket.setKeepAlive(bval);
        break;

      case SocketOptions.SO_OOBINLINE:
        socket.setOOBInline(bval);
        break;

      default:
          throw new SocketException("unsupported option ID");
    }
  }


  @Override
  public Object getOption(int optID) throws SocketException {
    switch (optID) {
      case SocketOptions.TCP_NODELAY:
        return socket.getTcpNoDelay();

      case SocketOptions.SO_BINDADDR:
        return socket.getLocalAddress();

      case SocketOptions.SO_REUSEADDR:
        return socket.getReuseAddress();

      // case SocketOptions.SO_BROADCAST:  ?? -> not supported
      // case SocketOptions.IP_MULTICAST_IF:
      // case SocketOptions.IP_MULTICAST_IF2:
      // case SocketOptions.IP_MULTICAST_LOOP:

      case SocketOptions.IP_TOS:
        return socket.getTrafficClass();

      case SocketOptions.SO_LINGER:
        return socket.getSoLinger();

      case SocketOptions.SO_TIMEOUT:
        return socket.getSoTimeout();

      case SocketOptions.SO_SNDBUF:
        return socket.getSendBufferSize();

      case SocketOptions.SO_RCVBUF:
        return socket.getReceiveBufferSize();

      case SocketOptions.SO_KEEPALIVE:
        return socket.getKeepAlive();
        
      case SocketOptions.SO_OOBINLINE:
        return socket.getOOBInline();

      default:
        throw new SocketException("unsupported option ID");
    }
  }

}
