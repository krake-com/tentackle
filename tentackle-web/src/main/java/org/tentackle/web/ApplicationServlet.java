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



/**
 * This code is largely based on Sun's sample code for RMI/HTTP-tunneling (ServletHandler.java).
 * It differs from the original code in various aspects concerning the Tentackle application
 * startup and configuration.
 */


/*
 * Copyright 2002 Sun Microsystems, Inc. All  Rights Reserved.
 *
 * Redistribution and use in source and binary forms, with or
 * without modification, are permitted provided that the following
 * conditions are met:
 *
 * -Redistributions of source code must retain the above copyright
 *  notice, this list of conditions and the following disclaimer.
 *
 * -Redistribution in binary form must reproduce the above copyright
 *  notice, this list of conditions and the following disclaimer in
 *  the documentation and/or other materials provided with the
 *  distribution.
 *
 * Neither the name of Sun Microsystems, Inc. or the names of
 * contributors may be used to endorse or promote products derived
 * from this software without specific prior written permission.
 *
 * This software is provided "AS IS," without a warranty of any
 * kind. ALL EXPRESS OR IMPLIED CONDITIONS, REPRESENTATIONS AND
 * WARRANTIES, INCLUDING ANY IMPLIED WARRANTY OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE OR NON-INFRINGEMENT, ARE HEREBY
 * EXCLUDED. SUN AND ITS LICENSORS SHALL NOT BE LIABLE FOR ANY
 * DAMAGES OR LIABILITIES  SUFFERED BY LICENSEE AS A RESULT OF OR
 * RELATING TO USE, MODIFICATION OR DISTRIBUTION OF THE SOFTWARE OR
 * ITS DERIVATIVES. IN NO EVENT WILL SUN OR ITS LICENSORS BE LIABLE
 * FOR ANY LOST REVENUE, PROFIT OR DATA, OR FOR DIRECT, INDIRECT,
 * SPECIAL, CONSEQUENTIAL, INCIDENTAL OR PUNITIVE DAMAGES, HOWEVER
 * CAUSED AND REGARDLESS OF THE THEORY OF LIABILITY, ARISING OUT OF
 * THE USE OF OR INABILITY TO USE SOFTWARE, EVEN IF SUN HAS BEEN
 * ADVISED OF THE POSSIBILITY OF SUCH DAMAGES.
 *
 * You acknowledge that Software is not designed, licensed or
 * intended for use in the design, construction, operation or
 * maintenance of any nuclear facility.
 */

package org.tentackle.web;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import javax.naming.Context;
import javax.naming.InitialContext;
import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.tentackle.app.ServerApplication;
import org.tentackle.log.Logger;
import org.tentackle.log.LoggerFactory;



/**
 * RMI HTTP-tunneling servlet.
 * <p>
 * The servlet can operate in two modes:
 * <ol>
 * <li>
 * If the servlet parameter {@code "applicationServer"} is given the servlet
 * will start an {@link ServerApplication}. All servlet parameters are converted
 * to server properties, so there's no need for an extra property file.
 * However, the {@code "tentackle.properties"} is supported via {@code &lt;env-entry&gt;}
 * as in {@link WebApplication}.
 * </li>
 * <li>
 * If thers is no such servlet parameter the servlet will only forward RMI requests.
 * </li>
 * </ol>
 * The logger can be set by the servlet parameter {@code "logger"}. If not
 * set the {@link DefaultLogger} will be used.
 */
public class ApplicationServlet extends HttpServlet {

  /**
   * logger for this class.
   */
  private static final Logger LOGGER = LoggerFactory.getLogger(ApplicationServlet.class);

  private static final long serialVersionUID = -746056612270951056L;



  /**
   * Creates the logger for this servlet.
   * <p>
   * Override this method if not using the default LOGGER.
   *
   * @return the logger
   */
  protected Logger createLogger() {
    return LoggerFactory.getLogger(getClass().getName());
  }



  /**
   * RMICommandHandler is the abstraction for an object that handles
   * a particular supported command (for example the "forward"
   * command "forwards" call information to a remote server on the
   * local machine).
   *
   * The command handler is only used by the ApplicationServlet so the
   * interface is protected.
   */
  private interface RMICommandHandler {

    /**
     * Return the string form of the command to be recognized in the
     * query string.
     */
    String getName();

    /**
     * Execute the command with the given string as parameter.
     */
    void execute(HttpServletRequest req, HttpServletResponse res, String param)
         throws ServletClientException, ServletServerException, IOException;
  }

  /**
   * List of handlers for supported commands. A new command will be
   * created for every service request
   */
  private final static RMICommandHandler commands[] = {
    new ServletForwardCommand(),
    new ServletGethostnameCommand(),
    new ServletPingCommand(),
    new ServletTryHostnameCommand()
  };

  /* construct table mapping command strings to handlers */
  private static final Map<String,RMICommandHandler> commandLookup = new HashMap<>();

  static {
    for (RMICommandHandler command: commands) {
      commandLookup.put(command.getName(), command);
    }
  }


  /**
   * Initializes the servlet.
   *
   * @param config Standard configuration object for an http servlet.
   *
   * @exception ServletException Calling
   *           <code>super.init(config)</code> may cause a servlet
   *           exception to be thrown.
   */
  @Override
  public void init(ServletConfig config) throws ServletException {

    super.init(config);

    try {
      // Server properties
      final Properties props = new Properties();

      try {
        // first try to load the property file "tentackle.properties" (as in WebApplication)
        Context env = (Context) new InitialContext().lookup("java:comp/env");
        // find <env-entry> for tentackle.properties in web.xml
        String filename = (String) env.lookup("tentackle.properties");
        try (InputStream is = Thread.currentThread().getContextClassLoader().getResourceAsStream(filename)) {
          Properties extraProps = new Properties();
          extraProps.load(is);
          // set the properties
          for (String key: extraProps.stringPropertyNames()) {
            props.setProperty(key, extraProps.getProperty(key));
          }
        }
      }
      catch (Exception ex) {
        // no such env entries or no such property file
      }

      // transform all servlet parameters to properties
      for (Enumeration keys = config.getInitParameterNames(); keys.hasMoreElements(); ) {
        String key = (String) keys.nextElement();
        props.setProperty(key, config.getInitParameter(key));
      }
      String serverClassName = props.getProperty("applicationServer");
      if (serverClassName != null) {
        // Tentackle application server class given: load it
        @SuppressWarnings("unchecked")
        final Class<? extends ServerApplication> serverClass =
                (Class<? extends ServerApplication>) Class.forName(serverClassName);
        // create an instance and start up.
        try {
          ServerApplication server = serverClass.newInstance();
          server.setProperties(props);
          server.start();
          LOGGER.info("Tentackle application server started");
        }
        catch (Exception ex) {
          LOGGER.logStacktrace(ex);
        }
      }
    }
    catch (Exception ex) {
      LOGGER.logStacktrace(ex);
    }
  }



  /**
   * Execute the command given in the servlet request query string.
   * The string before the first '=' in the queryString is
   * interpreted as the command name, and the string after the first
   * '=' is the parameters to the command.
   *
   * @param req  HTTP servlet request, contains incoming command and
   *             arguments
   * @param res  HTTP servlet response
   * @exception  ServletException and IOException when invoking
   *             methods of <code>req<code> or <code>res<code>.
   * @throws IOException
   */
  @Override
  public void doPost(HttpServletRequest req, HttpServletResponse res)
         throws ServletException, IOException {

    try {

      // Command and parameter for this POST request.
      String queryString = req.getQueryString();
      String command, param;
      int delim = queryString.indexOf('=');
      if (delim == -1) {
          command = queryString;
          param = "";
      }
      else {
          command = queryString.substring(0, delim);
          param = queryString.substring(delim + 1);
      }

      LOGGER.info("command: {0}, param: {1}", command, param);

      // lookup command to execute on the client's behalf
      RMICommandHandler handler = commandLookup.get(command);

      // execute the command
      if (handler != null) {
        try {
          handler.execute(req, res, param);
        }
        catch (ServletClientException e) {
          returnClientError(res, "client error: " + e.getMessage());
          LOGGER.logStacktrace(e);
        }
        catch (ServletServerException e) {
          returnServerError(res, "internal server error: " + e.getMessage());
          LOGGER.logStacktrace(e);
        }
      }
      else {
        returnClientError(res, "invalid command: " + command);
      }
    }
    catch (Exception e) {
      returnServerError(res, "internal error: " + e.getMessage());
      LOGGER.logStacktrace(e);
    }
  }


  @Override
  public void doGet(HttpServletRequest req, HttpServletResponse res)
         throws ServletException, IOException {

    returnClientError(res,
                      "GET Operation not supported: " +
                      "Can only forward POST requests.");
  }


  @Override
  public void doPut(HttpServletRequest req, HttpServletResponse res)
      throws ServletException, IOException {

    returnClientError(res,
                      "PUT Operation not supported: " +
                      "Can only forward POST requests.");
  }


  @Override
  public String getServletInfo() {
      return "RMI Call Forwarding Servlet Servlet.<br>\n";
  }


  /**
   * Return an HTML error message indicating there was error in
   * the client's request.
   *
   * @param res Servlet response object through which <code>message</code>
   *            will be written to the client which invoked one of
   *            this servlet's methods.
   * @param message Error message to be written to client.
   */
  private void returnClientError(HttpServletResponse res, String message)
          throws IOException {

    res.sendError(HttpServletResponse.SC_BAD_REQUEST,
                  "<HTML><HEAD>" +
                  "<TITLE>Java RMI Client Error</TITLE>" +
                  "</HEAD>" +
                  "<BODY>" +
                  "<H1>Java RMI Client Error</H1>" +
                  message +
                  "</BODY></HTML>");

   LOGGER.severe(HttpServletResponse.SC_BAD_REQUEST +
                 "Java RMI Client Error" + message);
  }


  /**
   * Return an HTML error message indicating an internal error
   * occurred here on the server.
   *
   * @param res Servlet response object through which <code>message</code>
   *            will be written to the servlet client.
   * @param message Error message to be written to servlet client.
   */
  private void returnServerError(HttpServletResponse res, String message)
          throws IOException {

    res.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                  "<HTML><HEAD>" +
                  "<TITLE>Java RMI Server Error</TITLE>" +
                  "</HEAD>" +
                  "<BODY>" +
                  "<H1>Java RMI Server Error</H1>" +
                  message + "</BODY></HTML>");

    LOGGER.severe(HttpServletResponse.SC_INTERNAL_SERVER_ERROR +
                  "Java RMI Server Error: " + message);
  }




  /*
   * The ApplicationServlet class is the only object that needs to access the
   * CommandHandler subclasses, so we write the commands internal to the
   * servlet handler.
   */

  /**
   * Class that has an execute command to forward request body to
   * local port on the server and send server reponse back to client.
   */
  private static class ServletForwardCommand implements RMICommandHandler {

    @Override
    public String getName() {
      return "forward";
    }

    /**
     * Execute the forward command.  Forwards data from incoming servlet
     * request to a port on the local machine.  Presumably, an RMI server
     * will be reading the data that this method sends.
     *
     * @param req   The servlet request.
     * @param res   The servlet response.
     * @param param Port to which data will be sent.
     */
    @SuppressWarnings("deprecation")
    @Override
    public void execute(HttpServletRequest req, HttpServletResponse res, String param)
           throws ServletClientException, ServletServerException, IOException {

      int port;
      try {
        port = Integer.parseInt(param);
      }
      catch (NumberFormatException e) {
        throw new ServletClientException("invalid port number: " + param);
      }
      if (port <= 0 || port > 0xFFFF) {
        throw new ServletClientException("invalid port: " + port);
      }
      if (port < 1024) {
        throw new ServletClientException("permission denied for port: " + port);
      }

      byte buffer[];
      Socket socket;
      try {
        socket = new Socket(InetAddress.getLocalHost(), port);
      }
      catch (IOException e) {
        throw new ServletServerException("could not connect to local port");
      }

      // read client's request body
      DataInputStream clientIn =
              new DataInputStream(req.getInputStream());
      buffer = new byte[req.getContentLength()];
      try {
        clientIn.readFully(buffer);
      }
      catch (EOFException e) {
        throw new ServletClientException("unexpected EOF reading request body");
      }
      catch (IOException e) {
        throw new ServletClientException("error reading request body");
      }

      DataOutputStream socketOut = null;
      // send to local server in HTTP
      try {
        socketOut =
                new DataOutputStream(socket.getOutputStream());
        socketOut.writeBytes("POST / HTTP/1.0\r\n");
        socketOut.writeBytes("Content-length: " +
                req.getContentLength() + "\r\n\r\n");
        socketOut.write(buffer);
        socketOut.flush();
      }
      catch (IOException e) {
        throw new ServletServerException("error writing to server");
      }

      // read response from local server
      DataInputStream socketIn;
      try {
        socketIn = new DataInputStream(socket.getInputStream());
      }
      catch (IOException e) {
        throw new ServletServerException("error reading from server");
      }
      String key = "Content-length:".toLowerCase();
      boolean contentLengthFound = false;
      String line;
      int responseContentLength = -1;
      do {
        try {
          line = socketIn.readLine();
        }
        catch (IOException e) {
          throw new ServletServerException("error reading from server");
        }
        if (line == null) {
          throw new ServletServerException("unexpected EOF reading server response");
        }
        if (line.toLowerCase().startsWith(key)) {
          responseContentLength = Integer.parseInt(line.substring(key.length()).trim());
          contentLengthFound = true;
        }
      } while ((line.length() != 0) &&
              (line.charAt(0) != '\r') && (line.charAt(0) != '\n'));

      if (!contentLengthFound || responseContentLength < 0) {
        throw new ServletServerException("missing or invalid content length in server response");
      }
      buffer = new byte[responseContentLength];
      try {
        socketIn.readFully(buffer);
      }
      catch (EOFException e) {
        throw new ServletServerException("unexpected EOF reading server response");
      }
      catch (IOException e) {
        throw new ServletServerException("error reading from server");
      }

      // send local server response back to servlet client
      res.setStatus(HttpServletResponse.SC_OK);
      res.setContentType("application/octet-stream");
      res.setContentLength(buffer.length);

      try {
        OutputStream out = res.getOutputStream();
        out.write(buffer);
        out.flush();
      }
      catch (IOException e) {
        throw new ServletServerException("error writing response");
      }
      finally {
        socketOut.close();
        socketIn.close();
      }
    }
  }

  /**
   * Class that has an execute method to return the host name of the
   * server as the response body.
   */
  private static class ServletGethostnameCommand
          implements RMICommandHandler {

    @Override
    public String getName() {
      return "gethostname";
    }

    @Override
    public void execute(HttpServletRequest req, HttpServletResponse res,
            String param)
            throws ServletClientException, ServletServerException, IOException {

      byte[] getHostStringBytes = req.getServerName().getBytes();

      res.setStatus(HttpServletResponse.SC_OK);
      res.setContentType("application/octet-stream");
      res.setContentLength(getHostStringBytes.length);

      OutputStream out = res.getOutputStream();
      out.write(getHostStringBytes);
      out.flush();
    }
  }

  /**
   * Class that has an execute method to return an OK status to
   * indicate that connection was successful.
   */
  private static class ServletPingCommand implements RMICommandHandler {

    @Override
    public String getName() {
      return "ping";
    }

    @Override
    public void execute(HttpServletRequest req, HttpServletResponse res,
            String param)
            throws ServletClientException, ServletServerException, IOException {

      res.setStatus(HttpServletResponse.SC_OK);
      res.setContentType("application/octet-stream");
      res.setContentLength(0);
    }
  }



  /**
   * Class that has an execute method to return a human readable
   * message describing which host name is available to local Java
   * VMs.
   */
  private static class ServletTryHostnameCommand implements RMICommandHandler {

    @Override
    public String getName() {
      return "hostname";
    }

    @Override
    public void execute(HttpServletRequest req, HttpServletResponse res,
            String param)
            throws ServletClientException, ServletServerException, IOException {

      PrintWriter pw = res.getWriter();

      pw.println("");
      pw.println("<HTML>" +
              "<HEAD><TITLE>Java RMI Server Hostname Info" +
              "</TITLE></HEAD>" +
              "<BODY>");
      pw.println("<H1>Java RMI Server Hostname Info</H1>");
      pw.println("<H2>Local host name available to Java VM:</H2>");
      pw.print("<P>InetAddress.getLocalHost().getHostName()");
      try {
        String localHostName = InetAddress.getLocalHost().getHostName();

        pw.println(" = " + localHostName);
      }
      catch (UnknownHostException e) {
        pw.println(" threw java.net.UnknownHostException");
      }

      pw.println("<H2>Server host information obtained through Servlet " +
              "interface from HTTP server:</H2>");
      pw.println("<P>SERVER_NAME = " + req.getServerName());
      pw.println("<P>SERVER_PORT = " + req.getServerPort());
      pw.println("</BODY></HTML>");
    }
  }

  /**
   * ServletClientException is thrown when an error is detected
   * in a client's request.
   */
  private static class ServletClientException extends Exception {

    private static final long serialVersionUID = -8749500232249233431L;

    public ServletClientException(String s) {
      super(s);
    }
  }

  /**
   * ServletServerException is thrown when an error occurs here on the server.
   */
  private static class ServletServerException extends Exception {

    private static final long serialVersionUID = 4055847163720192584L;

    public ServletServerException(String s) {
      super(s);
    }
  }
}
