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
import org.tentackle.log.Logger;
import org.tentackle.trip.Transport;
import org.tentackle.trip.TransportManager;
import org.tentackle.trip.TripRuntimeException;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.net.URI;

interface ServerUpdateUtilitiesHolder {
  ServerUpdateUtilities INSTANCE = ServiceFactory.createService(ServerUpdateUtilities.class, ServerUpdateUtilities.class);
}

/**
 * Utility methods to implement the application update.
 */
@Service(ServerUpdateUtilities.class)
public class ServerUpdateUtilities {

  private static final Logger LOGGER = Logger.get(ServerUpdateUtilities.class);

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
   * Starts the update service.
   *
   * @param serviceUrlName the update service url, ex. {@code "trip://localhost/Update:33001"}
   * @param updateUrlName the URL base holding the downloads, ex. {@code "http://localhost/myapp/downloads"}
   * @param updateServiceImplClass the class implementing the remote {@link UpdateService}
   */
  public void startUpdateService(String serviceUrlName, String updateUrlName, Class<? extends UpdateService> updateServiceImplClass) {
    URI uri = URI.create(serviceUrlName);
    LOGGER.info("starting update service " + uri);
    try {
      Transport transport = TransportManager.getInstance().requestTransport(uri);
      if (!transport.isAccepting()) {
        // new transport
        transport.accept();
      }
      UpdateService updateService = createUpdateServiceImpl(updateServiceImplClass, updateUrlName);
      transport.getRegistry().register(updateService, uri.getPath());
    }
    catch (TripRuntimeException tx) {
      throw new TripRuntimeException("cannot start update service " + uri, tx);
    }
  }


  /**
   * Creates the update service implementation.<br>
   *
   * @param updateServiceImplClass the class implementing the remote {@link UpdateService}
   * @param updateUrlName the download base URL
   * @return the service implementation
   */
  protected UpdateService createUpdateServiceImpl(Class<? extends UpdateService> updateServiceImplClass, String updateUrlName) {
    try {
        Constructor<? extends UpdateService> constructor = updateServiceImplClass.getConstructor(String.class);
        return constructor.newInstance(updateUrlName);
    }
    catch (InstantiationException | IllegalAccessException | InvocationTargetException |
           NoSuchMethodException ex) {
      throw new TentackleRuntimeException("cannot create update service " + updateServiceImplClass.getName(), ex);
    }
  }

}
