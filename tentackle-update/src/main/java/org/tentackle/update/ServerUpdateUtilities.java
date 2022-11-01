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

import org.tentackle.common.Service;
import org.tentackle.common.ServiceFactory;
import org.tentackle.common.TentackleRuntimeException;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.net.URI;
import java.net.URISyntaxException;
import java.rmi.AlreadyBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.RMIClientSocketFactory;
import java.rmi.server.RMIServerSocketFactory;

interface ServerUpdateUtilitiesHolder {
  ServerUpdateUtilities INSTANCE = ServiceFactory.createService(ServerUpdateUtilities.class, ServerUpdateUtilities.class);
}

/**
 * Utility methods to implement the application update.
 */
@Service(ServerUpdateUtilities.class)
public class ServerUpdateUtilities {

  /**
   * The singleton.
   *
   * @return the singleton
   */
  public static ServerUpdateUtilities getInstance() {
    return ServerUpdateUtilitiesHolder.INSTANCE;
  }


  /**
   * Creates a server update utilities instance.
   */
  public ServerUpdateUtilities() {
    // see -Xlint:missing-explicit-ctor since Java 16
  }

  /**
   * Creates a registry and exports the update service.
   * <p>
   * Notice: either both csf and ssf must be given or none.
   *
   * @param serviceUrlName the rmi service url, ex. {@code "rmi://localhost/Update:33002"}
   * @param updateUrlName the URL base holding the downloads, ex. {@code "http://localhost/myapp/downloads"}
   * @param updateServiceImplClass the class implementing the remote {@link UpdateService}
   * @param csf the client socket factory, null if system default
   * @param ssf the server socket factory, null if system default
   */
  public void exportUpdateService(String serviceUrlName, String updateUrlName, Class<? extends UpdateService> updateServiceImplClass,
                                  RMIClientSocketFactory csf, RMIServerSocketFactory ssf) {
    try {
      URI uri = new URI(serviceUrlName);
      int port = uri.getPort();
      Registry updateRegistry = csf != null && ssf != null ?
                                LocateRegistry.createRegistry(port, csf, ssf) : LocateRegistry.createRegistry(port);
      String uriPath = uri.getPath();
      String serviceName = uriPath.startsWith("/") ? uriPath.substring(1) : uriPath;
      updateRegistry.bind(serviceName, createUpdateServiceImpl(updateServiceImplClass, port, updateUrlName, csf, ssf));
    }
    catch (URISyntaxException e) {
      throw new TentackleRuntimeException("malformed updateService URL '" + serviceUrlName + "'", e);
    }
    catch (RemoteException e) {
      throw new TentackleRuntimeException("cannot create update service", e);
    }
    catch (AlreadyBoundException e) {
      throw new TentackleRuntimeException("cannot bind update registry", e);
    }
  }

  /**
   * Creates a registry and exports the update service using default socket factories.
   *
   * @param serviceUrlName the rmi service url, ex. {@code "rmi://localhost/Update:33002"}
   * @param updateUrlName the URL base holding the downloads, ex. {@code "http://localhost/myapp/downloads"}
   * @param updateServiceImplClass the class implementing the remote {@link UpdateService}
   */
  public void exportUpdateService(String serviceUrlName, String updateUrlName, Class<? extends UpdateService> updateServiceImplClass) {
    exportUpdateService(serviceUrlName, updateUrlName, updateServiceImplClass, null, null);
  }

  /**
   * Creates the update service implementation.<br>
   * Invoked by {@link #exportUpdateService(String, String, Class, RMIClientSocketFactory, RMIServerSocketFactory)}.
   *
   * @param updateServiceImplClass the class implementing the remote {@link UpdateService}
   * @param port the ip port
   * @param updateUrlName the download base URL
   * @param csf the client socket factory, null if system default
   * @param ssf the server socket factory, null if system default
   * @return the service implementation
   */
  protected UpdateService createUpdateServiceImpl(Class<? extends UpdateService> updateServiceImplClass, int port, String updateUrlName,
                                                  RMIClientSocketFactory csf, RMIServerSocketFactory ssf) {
    try {
      if (csf != null && ssf != null) {
        Constructor<? extends UpdateService> constructor = updateServiceImplClass.getConstructor(Integer.TYPE, String.class, RMIClientSocketFactory.class, RMIServerSocketFactory.class);
        return constructor.newInstance(port, updateUrlName, csf, ssf);
      }
      else {
        Constructor<? extends UpdateService> constructor = updateServiceImplClass.getConstructor(Integer.TYPE, String.class);
        return constructor.newInstance(port, updateUrlName);
      }
    }
    catch (NoSuchMethodException | SecurityException | InstantiationException | IllegalAccessException | IllegalArgumentException | InvocationTargetException ex) {
      throw new TentackleRuntimeException("cannot create update service " + updateServiceImplClass.getName(), ex);
    }
  }

}
