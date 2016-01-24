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

package org.tentackle.persist.rmi;

import java.io.OutputStream;
import java.rmi.RemoteException;
import org.tentackle.io.NotifyingByteArrayOutputStream;
import org.tentackle.log.Logger;
import org.tentackle.log.LoggerFactory;

/**
 *
 * @author harald
 */
public class InputStreamRemoteDelegateImpl extends RemoteDelegateImpl<OutputStream> implements InputStreamRemoteDelegate {

  /**
   * the logger for this class.
   */
  private static final Logger LOGGER = LoggerFactory.getLogger(InputStreamRemoteDelegateImpl.class);


  private final NotifyingByteArrayOutputStream out;              // current buffer to write to


  /**
   * Creates an input stream delegate.
   * <p>
   * Note that we don't extend {@link java.rmi.server.UnicastRemoteObject} to allow
   * adding interceptors via dynamic proxies. Therefore, the object
   * must be explicitly exported by
   * {@link RemoteDbSessionImpl#exportRemoteDelegate}.
   *
   * @param serverSession the RMI serverSession
   * @param servicedClass the class the delegate provides service for
   * @throws RemoteException if creating the delegate failed
   */
  public InputStreamRemoteDelegateImpl(RemoteDbSessionImpl serverSession, Class<OutputStream> servicedClass)
         throws RemoteException {
    super(serverSession, servicedClass);
    out = new NotifyingByteArrayOutputStream();
  }


  @Override
  public byte[] read() throws RemoteException {
    for(;;) {
      synchronized(out) {
        if (out.size() == 0) {
          try {
            out.wait();   // wait until data is available
          }
          catch (InterruptedException iex) {
            LOGGER.warning("interrupted -> ignored");
          }
        }
        else  {
          byte[] buf = out.toByteArray();
          out.reset();
          return buf;
        }
      }
    }
  }


  /**
   * Gets the output stream from which the client will read.
   *
   * @return the output stream to write to from the server-side
   */
  public OutputStream getOutputStream() {
    return out;
  }

}
