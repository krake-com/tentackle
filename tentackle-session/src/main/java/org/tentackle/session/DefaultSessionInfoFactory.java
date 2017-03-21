/*
 * Tentackle - http://www.tentackle.org.
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
 *
 */

package org.tentackle.session;

import java.util.Properties;
import org.tentackle.common.Service;

/**
 * Default session info factory.
 *
 * @author harald
 */
@Service(SessionInfoFactory.class)
public class DefaultSessionInfoFactory implements SessionInfoFactory {

  @Override
  public SessionInfo create(String username, char[] password, String propertiesName) {
    return new DefaultSessionInfo(username, password, propertiesName);
  }

  @Override
  public SessionInfo create(String propertiesName) {
    return new DefaultSessionInfo(propertiesName);
  }

  @Override
  public SessionInfo create(Properties properties) {
    return new DefaultSessionInfo(properties);
  }

  @Override
  public SessionInfo create() {
    return new DefaultSessionInfo();
  }

}
