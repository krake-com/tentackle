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

package org.tentackle.update;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A minimal HTTP/1.1 server for the update tests.
 * <p>
 * The update module talks plain HTTP for everything but the {@code UpdateService} call itself:
 * {@code FileHelper.download} fetches the {@code .sha256} file, an HTTP {@code HEAD} yields the
 * download size, and the ZIP is streamed via {@code URL.openStream()}. All of that is exercised
 * against this server rather than against mocks, so the real code paths are covered.
 * <p>
 * {@code com.sun.net.httpserver} is not an option here because the tests are patched into the
 * {@code org.tentackle.update} module, which does not read {@code jdk.httpserver}. Hand-rolling
 * the handful of bytes an HTTP response consists of is cheaper than widening the module graph.
 * <p>
 * Every request is counted per path so the tests can prove that the service caches its metadata.
 */
public final class TestHttpServer implements AutoCloseable {

  private final ServerSocket serverSocket;
  private final Map<String, byte[]> resources = new ConcurrentHashMap<>();
  private final Map<String, AtomicInteger> requestCounts = new ConcurrentHashMap<>();
  private final Thread acceptThread;

  /** Paths served without a content-length header. */
  private final Map<String, Boolean> withoutContentLength = new ConcurrentHashMap<>();

  /** Paths answered with an error status instead of 200. */
  private final Map<String, String> errorStatus = new ConcurrentHashMap<>();

  /**
   * Creates and starts a server bound to an ephemeral port on the loopback interface.
   *
   * @throws IOException if the server socket could not be opened
   */
  public TestHttpServer() throws IOException {
    serverSocket = new ServerSocket(0, 10, InetAddress.getLoopbackAddress());
    acceptThread = Thread.ofVirtual().name("test-http-server").start(this::acceptLoop);
  }

  /**
   * Publishes a resource.
   *
   * @param path the absolute request path, e.g. {@code "/downloads/app.zip"}
   * @param content the content to serve
   * @return this server
   */
  public TestHttpServer serve(String path, byte[] content) {
    resources.put(path, content);
    errorStatus.remove(path);       // a previously failing path is published now
    withoutContentLength.remove(path);
    return this;
  }

  /**
   * Publishes a textual resource.
   *
   * @param path the absolute request path
   * @param content the content to serve
   * @return this server
   */
  public TestHttpServer serve(String path, String content) {
    return serve(path, content.getBytes(StandardCharsets.UTF_8));
  }

  /**
   * Makes the given path answer without a {@code content-length} header.<br>
   * This is what a server doing chunked transfer or a misbehaving proxy looks like to
   * {@code AbstractUpdateServiceImpl.determineDownloadSize}.
   *
   * @param path the absolute request path
   * @return this server
   */
  public TestHttpServer withoutContentLength(String path) {
    withoutContentLength.put(path, Boolean.TRUE);
    return this;
  }

  /**
   * Answers the given path with an error status and a body.<br>
   * This is what a CDN or a reverse proxy looks like when the artifact is not published (yet):
   * the response carries a perfectly valid {@code content-length} - of the error page.
   *
   * @param path the absolute request path
   * @param status the status line, e.g. {@code "404 Not Found"}
   * @param body the error page
   * @return this server
   */
  public TestHttpServer serveError(String path, String status, String body) {
    resources.put(path, body.getBytes(StandardCharsets.UTF_8));
    errorStatus.put(path, status);
    return this;
  }

  /**
   * Gets the number of requests received for the given path.
   *
   * @param path the absolute request path
   * @return the request count
   */
  public int getRequestCount(String path) {
    AtomicInteger count = requestCounts.get(path);
    return count == null ? 0 : count.get();
  }

  /**
   * Gets the port the server is bound to.
   *
   * @return the port
   */
  public int getPort() {
    return serverSocket.getLocalPort();
  }

  /**
   * Gets the base URL of this server, without a trailing slash.
   *
   * @return the base URL, e.g. {@code "http://localhost:4711"}
   */
  public String getBaseUrl() {
    return "http://localhost:" + getPort();
  }

  /**
   * Builds the URL of a served path.
   *
   * @param path the absolute request path
   * @return the URL
   * @throws IOException if the URL is malformed
   */
  public URL url(String path) throws IOException {
    try {
      return new java.net.URI(getBaseUrl() + path).toURL();
    }
    catch (java.net.URISyntaxException ex) {
      throw new IOException(ex);
    }
  }

  @Override
  public void close() throws IOException {
    serverSocket.close();
    acceptThread.interrupt();
  }


  private void acceptLoop() {
    while (!serverSocket.isClosed()) {
      try {
        Socket socket = serverSocket.accept();
        Thread.ofVirtual().start(() -> handle(socket));
      }
      catch (IOException ex) {
        return;   // socket closed -> done
      }
    }
  }

  private void handle(Socket socket) {
    try (socket) {
      InputStream in = socket.getInputStream();
      String requestLine = readLine(in);
      if (requestLine == null) {
        return;
      }
      while (true) {   // skip the headers, the tests never send a request body
        String header = readLine(in);
        if (header == null || header.isEmpty()) {
          break;
        }
      }

      String[] parts = requestLine.split(" ");
      String method = parts[0];
      String path = parts.length > 1 ? parts[1] : "/";
      requestCounts.computeIfAbsent(path, _ -> new AtomicInteger()).incrementAndGet();

      byte[] content = resources.get(path);
      OutputStream out = new BufferedOutputStream(socket.getOutputStream());
      if (content == null) {
        writeHeader(out, "404 Not Found", 0, false);
      }
      else {
        String status = errorStatus.get(path);
        writeHeader(out, status == null ? "200 OK" : status, content.length, withoutContentLength.containsKey(path));
        if (!"HEAD".equals(method)) {
          out.write(content);
        }
      }
      out.flush();
    }
    catch (IOException ex) {
      // client vanished - nothing to do
    }
  }

  private void writeHeader(OutputStream out, String status, int length, boolean omitContentLength) throws IOException {
    StringBuilder buf = new StringBuilder("HTTP/1.1 ").append(status).append("\r\n");
    if (!omitContentLength) {
      buf.append("Content-Length: ").append(length).append("\r\n");
    }
    buf.append("Content-Type: application/octet-stream\r\n")
       .append("Connection: close\r\n\r\n");
    out.write(buf.toString().getBytes(StandardCharsets.ISO_8859_1));
  }

  private String readLine(InputStream in) throws IOException {
    StringBuilder buf = new StringBuilder();
    int c;
    while ((c = in.read()) != -1) {
      if (c == '\n') {
        int len = buf.length();
        if (len > 0 && buf.charAt(len - 1) == '\r') {
          buf.setLength(len - 1);
        }
        return buf.toString();
      }
      buf.append((char) c);
    }
    return buf.isEmpty() ? null : buf.toString();
  }

}
