/*
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

package org.tentackle.dbms.rmi;

import java.io.IOException;
import java.io.InputStream;
import java.rmi.RemoteException;
import org.tentackle.io.BlockingByteArrayInputStream;
import org.tentackle.io.NotifyingByteArrayOutputStream;

/**
 *
 * @author harald
 */
public class OutputStreamRemoteDelegateImpl extends RemoteDelegateImpl<InputStream> implements OutputStreamRemoteDelegate {


  private final BlockingByteArrayInputStream in;       // the input stream for the server side


  /**
   * Creates an output stream delegate.
   * <p>
   * Note that we don't extend {@link java.rmi.server.UnicastRemoteObject} to allow
   * adding interceptors via dynamic proxies. Therefore, the object
   * must be explicitly exported by
   * {@link RemoteDbSessionImpl#exportRemoteDelegate}.
   *
   * @param serverSession the RMI serverSession
   * @param servicedClass the class the delegate provides service for
   */
  public OutputStreamRemoteDelegateImpl(RemoteDbSessionImpl serverSession, Class<InputStream> servicedClass) {
    super(serverSession, servicedClass);
    in = new BlockingByteArrayInputStream(new NotifyingByteArrayOutputStream());
  }

  @Override
  public void write(byte[] bytes) throws RemoteException {
    try {
      in.getOutputStream().write(bytes);
    }
    catch (IOException iox) {
      throw createException(iox);
    }
  }


  /**
   * Gets the input stream the client will write to.
   *
   * @return the input stream to read from at the server side
   */
  public InputStream getInputStream() {
    return in;
  }

}
