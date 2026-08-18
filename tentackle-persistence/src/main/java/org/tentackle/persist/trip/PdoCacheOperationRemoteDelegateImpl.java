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

package org.tentackle.persist.trip;

import org.tentackle.dbms.DbUtilities;
import org.tentackle.dbms.trip.RemoteDbSessionImpl;
import org.tentackle.log.Logger;
import org.tentackle.pdo.DomainContext;
import org.tentackle.pdo.PdoCache;
import org.tentackle.pdo.PdoCacheOperation;
import org.tentackle.pdo.PdoCachePreloadContent;
import org.tentackle.pdo.PersistentDomainObject;
import org.tentackle.persist.PdoCacheOperationPersistenceImpl;
import org.tentackle.persist.PdoModificationTracker;
import org.tentackle.persist.PersistenceUtilities;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Remote delegate implementation for {@link PdoModificationTracker}.
 *
 * @author  harald
 */
public class PdoCacheOperationRemoteDelegateImpl
  extends AbstractPersistentOperationRemoteDelegateImpl<PdoCacheOperation, PdoCacheOperationPersistenceImpl>
  implements PdoCacheOperationRemoteDelegate {

  private static final Logger LOGGER = Logger.get(PdoCacheOperationRemoteDelegateImpl.class);

  //<editor-fold defaultstate="collapsed" desc="constructors">

  /**
   * Creates the remote delegate for {@link PdoCacheOperationPersistenceImpl}.
   *
   * @param session the RMI session
   * @param persistenceClass the persistence implementation class
   * @param operationClass the operation interface class
   */
  public PdoCacheOperationRemoteDelegateImpl(RemoteDbSessionImpl session,
                                             Class<PdoCacheOperationPersistenceImpl> persistenceClass,
                                             Class<PdoCacheOperation> operationClass) {
    super(session, persistenceClass, operationClass);
  }

  //</editor-fold>


  @Override
  public Collection<PdoCachePreloadContent<?>> getPreloadingCachesContent(DomainContext context, String[] classNames) {
    context.setSession(getSession());
    List<PdoCache<? extends PersistentDomainObject<?>>> caches =
      ((PersistenceUtilities) DbUtilities.getInstance()).getPreloadingCaches();

    Collection<PdoCachePreloadContent<?>> preloadContents = new ArrayList<>();
    for (PdoCache<? extends PersistentDomainObject<?>> cache : caches) {
      if (cache.isPreloading() && cache.isReadOnly() && cache.isEnabled()) {
        preloadContents.add(cache.getPreloadContent(context));
      }
      else {
        LOGGER.info("PdoCache " + cache + " is not a preloading, read-only cache or it is disabled -> skipped");
      }
    }
    return preloadContents;
  }

}
