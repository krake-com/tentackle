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

package org.tentackle.persist;

import org.tentackle.app.Application;
import org.tentackle.dbms.DbUtilities;
import org.tentackle.log.Logger;
import org.tentackle.pdo.DomainContext;
import org.tentackle.pdo.Pdo;
import org.tentackle.pdo.PdoCache;
import org.tentackle.pdo.PdoCacheOperation;
import org.tentackle.pdo.PdoCacheOperationPersistence;
import org.tentackle.pdo.PdoCachePreloadContent;
import org.tentackle.pdo.PdoCachePreloadTableInfo;
import org.tentackle.pdo.PersistentDomainObject;
import org.tentackle.pdo.PersistentOperationService;
import org.tentackle.persist.trip.PdoCacheOperationRemoteDelegate;
import org.tentackle.session.ModificationTracker;
import org.tentackle.session.Session;

import java.io.Serial;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.function.Consumer;

/**
 * Persistence delegate for {@link PdoCacheOperation}.
 */
@PersistentOperationService(PdoCacheOperation.class)
public class PdoCacheOperationPersistenceImpl
       extends AbstractPersistentOperation<PdoCacheOperation, PdoCacheOperationPersistenceImpl>
       implements PdoCacheOperationPersistence {

  @Serial
  private static final long serialVersionUID = 1L;

  private static final Logger LOGGER = Logger.get();

  //<editor-fold defaultstate="collapsed" desc="classvariables">

  private static final PersistentOperationClassVariables<PdoCacheOperation, PdoCacheOperationPersistenceImpl> CLASSVARIABLES =
    new PersistentOperationClassVariables<>(PdoCacheOperation.class, PdoCacheOperationPersistenceImpl.class);

  @Override
  public PersistentOperationClassVariables<PdoCacheOperation, PdoCacheOperationPersistenceImpl> getClassVariables() {
    return CLASSVARIABLES;
  }

  //</editor-fold>

  //<editor-fold defaultstate="collapsed" desc="constructors">

  /**
   * Creates an operation object.
   *
   * @param operation the operation object this is a delegate for
   * @param context the database context
   */
  public PdoCacheOperationPersistenceImpl(PdoCacheOperation operation, DomainContext context) {
    super(operation, context);
  }

  /**
   * Creates an operation object without a domain context
   * for a given connection.<p>
   * Note: the application must set the context.
   *
   * @param operation the operation object this is a delegate for
   * @param session the session (must be an instance of {@link Session}).
   */
  public PdoCacheOperationPersistenceImpl(PdoCacheOperation operation, Session session) {
    super(operation, session);
  }

  /**
   * Creates an operation object without a database context.<p>
   * Note: the application must set the context.
   *
   * @param operation the persistent operation object this is a delegate for
   */
  public PdoCacheOperationPersistenceImpl(PdoCacheOperation operation) {
    super(operation);
  }

  /**
   * Creates an operation object without a database context.
   */
  public PdoCacheOperationPersistenceImpl() {
    super();
  }

  //</editor-fold>

  //<editor-fold defaultstate="collapsed" desc="remote">

  @Override
  public PdoCacheOperationRemoteDelegate getRemoteDelegate() {
    return (PdoCacheOperationRemoteDelegate) super.getRemoteDelegate();
  }

  //</editor-fold>

  @Override
  public CountDownLatch preloadPdoCaches() {
    Application application = Application.getInstance();
    boolean isInteractive = application != null && application.isInteractive();
    DomainContext context = getDomainContext();
    if (context.getSession().isRemote()) {
      // always run in a separate thread to avoid session timeout if preloading takes too long
      return preloadInClonedSession(context, this::preloadCachesFromRemote);
    }
    else {
      // directly connected to the database
      if (isInteractive) {
        // preload in a separate thread for fast client startup
        return preloadInClonedSession(context, this::preloadCachesFromDatabase);
      }
      else {
        preloadCachesFromDatabase(context);
      }
    }
    return null;
  }

  /**
   * Preloads the PDO caches in a separate thread on a temporary cloned session.
   *
   * @param context the domain context
   * @param loader the content loader
   * @return the latch to monitor when the preload is finished
   */
  protected CountDownLatch preloadInClonedSession(DomainContext context, Consumer<DomainContext> loader) {
    CountDownLatch latch = new CountDownLatch(1);
    Thread thread = new Thread("preload PDO caches") {
      @Override
      public void run() {
        Session plSession = context.getSession().clone("PL");
        plSession.groupWith(context.getSession().getSessionId());
        try {
          DomainContext plContext = context.clone(plSession);
          loader.accept(plContext);
        }
        finally {
          plSession.close();
          latch.countDown();
        }
      }
    };
    thread.setDaemon(true);
    thread.start();
    return latch;
  }

  /**
   * Preloads the PDO caches from the remote server.
   *
   * @param context the domain context
   */
  @SuppressWarnings({ "rawtypes", "unchecked" })
  protected void preloadCachesFromRemote(DomainContext context) {
    LOGGER.info("preloading PDO caches from remote ...");
    Collection<PdoCachePreloadContent<?>> cachesContent =
      getRemoteDelegate().getPreloadingCachesContent(context, getPreloadCacheClassNames());
    for (PdoCachePreloadContent<?> cacheContent : cachesContent) {
      PersistentDomainObject<?> pdo = Pdo.create(cacheContent.pdoClass(), context);
      PdoCache cache = pdo.getPersistenceDelegate().getCache();
      if (cache.isInitialized()) {
        LOGGER.warning("PdoCache {0} is already initialized in the meantime -> skipped", cache);
      }
      else {
        int count = cache.applyPreloadContent(context, cacheContent);
        for (PdoCachePreloadTableInfo<?> tableInfo : cacheContent.tableInfo()) {
          ((PdoModificationTracker) ModificationTracker.getInstance()).setIdSerialForName(tableInfo.tableName(), tableInfo.idSerial());
        }
        LOGGER.info("{0} PDOs preloaded into {1} from remote", count, cache);
      }
    }
  }

  /**
   * Preloads the PDO caches from the database.
   *
   * @param context the domain context
   */
  protected void preloadCachesFromDatabase(DomainContext context) {
    getPreloadingCaches().forEach(cache -> {
      List<? extends PersistentDomainObject<?>> pdoList = cache.selectAll(context);
      LOGGER.info("{0} PDOs preloaded into {1} from database", pdoList.size(), cache);
    });
  }

  /**
   * Gets the class names of the PDO persistence implementations with preloading caches.
   *
   * @return the class names
   */
  protected String[] getPreloadCacheClassNames() {
    return ((PersistenceUtilities) DbUtilities.getInstance()).getPreloadCacheClassNames();
  }

  /**
   * Gets the preloading caches.
   *
   * @return the caches
   */
  protected List<PdoCache<? extends PersistentDomainObject<?>>> getPreloadingCaches() {
    return ((PersistenceUtilities) DbUtilities.getInstance()).getPreloadingCaches();
  }

}
