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

package org.tentackle.sql;

import org.tentackle.common.EncryptedProperties;
import org.tentackle.common.Service;

/**
 * Default implementation of a backend info factory.
 */
@Service(BackendInfoFactory.class)
public class DefaultBackendInfoFactory implements BackendInfoFactory {

  /**
   * Creates a backend info factory.
   */
  public DefaultBackendInfoFactory() {
    // see -Xlint:missing-explicit-ctor since Java 16
  }

  @Override
  public BackendInfo create(Backend backend) {
    return new BackendInfo(backend);
  }

  @Override
  public BackendInfo create(String backendName) {
    return new BackendInfo(backendName);
  }

  @Override
  public BackendInfo create(String url, String user, char[] password, String[] schemas) {
    return new BackendInfo(url, user, password, schemas);
  }

  @Override
  public BackendInfo create(BackendInfo backendInfo, String user, char[] password) {
    return new BackendInfo(backendInfo, user, password);
  }

  @Override
  public BackendInfo create(EncryptedProperties backendProperties) {
    return new BackendInfo(backendProperties);
  }

}
