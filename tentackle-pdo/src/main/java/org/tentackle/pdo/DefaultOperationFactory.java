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

package org.tentackle.pdo;

import org.tentackle.common.Constants;
import org.tentackle.common.Service;
import org.tentackle.common.ServiceFactory;
import org.tentackle.common.ServiceFinder;
import org.tentackle.reflect.ClassMapper;
import org.tentackle.reflect.DefaultClassMapper;

/**
 * The default operation factory.
 *
 * @author harald
 */
@Service(OperationFactory.class)
public class DefaultOperationFactory extends AbstractOperationFactory {


  private final DefaultClassMapper domainClassMapper;
  private final DefaultClassMapper persistenceClassMapper;


  /**
   * Creates an operation factory.
   */
  public DefaultOperationFactory() {
    final ServiceFinder finder = ServiceFactory.getServiceFinder();

    domainClassMapper = new DefaultClassMapper(
        "operation domain",
        ServiceFactory.INSTANCE.getClassLoader(Constants.MAPPED_SERVICE_PATH, DomainOperation.class.getName()),
        finder.createNameMap(DomainOperation.class.getName()), null);

    persistenceClassMapper = new DefaultClassMapper(
        "operation persistence",
        ServiceFactory.INSTANCE.getClassLoader(Constants.MAPPED_SERVICE_PATH, PersistentOperation.class.getName()),
        finder.createNameMap(PersistentOperation.class.getName()), null);
    
    // if there is no persistence delegate (which is allowed for operations in contrast to PDOs),
    // map to a dummy delegate
    persistenceClassMapper.setDefaultClass(DummyPersistentOperation.class);
  }


  @Override
  public ClassMapper getPersistenceMapper() {
    return persistenceClassMapper;
  }

  @Override
  public ClassMapper getDomainMapper() {
    return domainClassMapper;
  }
}
