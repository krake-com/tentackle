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

package org.tentackle.dbms.trip;

import org.tentackle.session.SessionInfo;
import org.tentackle.session.VersionIncompatibleException;
import org.tentackle.trip.Transport;

/**
 * Server side TRIP-connection implementation.
 * <p>
 * Overview of what happens in a Tentackle TRIP application server:
 * <ul>
 * <li>client gets a reference to a {@link RemoteDbConnection} via a {@link org.tentackle.trip.Registry#lookup(String)}</li>
 * <li>the client invokes {@link RemoteDbConnection#login(SessionInfo)} and gets a reference to a {@link RemoteDbSession}.</li>
 * <li>the session verifies the credentials carried by the session info: {@link RemoteDbSessionImpl#verifySessionInfo(SessionInfo)}
 *     hands them to the {@link org.tentackle.session.auth.AuthenticationManager}, which drives the chain of configured
 *     {@link org.tentackle.session.auth.AuthenticationProvider}s. A refused login throws a
 *     {@link org.tentackle.session.LoginFailedException}.</li>
 * <li>all further client requests work through the session object and the remote delegates created within the session</li>
 * <li>the client invokes {@link RemoteDbSession#close()} to close the session and all its registered delegates.</li>
 * <li>in case the client terminates abnormally, the {@link RemoteDbSessionCleanupThread} will close the session.</li>
 * </ul>
 * @author harald
 */
public abstract class RemoteDbConnectionImpl implements RemoteDbConnection {

  private final transient TripServer server;
  private final transient Transport transport;              // the transport


  /**
   * Creates a connection.<br>
   *
   * @param server the server
   * @param transport the transport
   */
  public RemoteDbConnectionImpl(TripServer server, Transport transport) {
    this.server = server;
    this.transport = transport;
  }


  /**
   * Gets the server.
   *
   * @return the server
   */
  public TripServer getServer() {
    return server;
  }

  /**
   * Gets the transport.
   *
   * @return the transport
   */
  public Transport getTransport() {
    return transport;
  }

  /**
   * Checks the client's version.
   * <p>
   * The default implementation does nothing.
   * It is invoked only for remote connections.
   * <p>
   * Throws {@link VersionIncompatibleException} if versions are not compatible.
   *
   * @param clientVersion the client version
   */
  public void checkClientVersion(String clientVersion) {
    // default is ok
  }

  /**
   * Creates the session.<br>
   * Needs to be implemented by the application.
   *
   * @param clientInfo the client info (login info)
   * @return the created session
   */
  public abstract RemoteDbSession createSession(SessionInfo clientInfo);


  // ------------------ implements RemoteDbConnection -----------------

  @Override
  public String getServerVersion() {
    return null;
  }


  @Override
  public RemoteDbSession login(SessionInfo clientInfo) {
    // check client compatibility
    checkClientVersion(clientInfo.getClientVersion());
    // create the session
    return createSession(clientInfo);
  }

}
