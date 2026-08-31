/*
 * Tentackle - https://tentackle.org.
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

import org.tentackle.dbms.ResultSetWrapper;
import org.tentackle.log.Logger;
import org.tentackle.misc.ScrollableResource;
import org.tentackle.pdo.DomainContext;
import org.tentackle.pdo.Pdo;
import org.tentackle.pdo.PersistentDomainObject;
import org.tentackle.persist.trip.RemoteResultSetCursor;
import org.tentackle.session.PersistenceException;
import org.tentackle.session.Session;

import java.io.Serial;
import java.lang.ref.Cleaner;
import java.lang.ref.Cleaner.Cleanable;
import java.lang.ref.Reference;
import java.util.ArrayList;
import java.util.List;

/**
 * A cursor for a {@link ResultSetWrapper}.
 *
 * @author harald
 * @param <T> the retrieved type
 */
public class ResultSetCursor<T extends PersistentDomainObject<T>> implements ScrollableResource<T> {

  private static final Logger LOGGER = Logger.get(ResultSetCursor.class);

  private static final Cleaner CLEANER = Cleaner.create();        // instead of deprecated finalize()

  /**
   * Holds the resources to be cleaned up.
   * <p>
   * All fields are volatile: {@link #run()} is executed either by the closing application thread or,
   * for a cursor that became unreachable without being closed, by the cleaner thread. Both write
   * them, and {@link ResultSetCursor#isClosed()} reads them from the application thread.
   */
  private static class Resources<T extends PersistentDomainObject<T>> implements Runnable {

    private volatile ResultSetWrapper rs;                   // the result set (only for local sessions, null if remote)
    private volatile RemoteResultSetCursor<T> remoteCursor; // the remote cursor (only for remote sessions, null if local)
    private volatile ResultSetCursor<T> me;                 // != null if programmatic close

    Resources(ResultSetWrapper rs, RemoteResultSetCursor<T> remoteCursor) {
      this.rs = rs;
      this.remoteCursor = remoteCursor;
    }

    @Override
    public void run() {
      try {
        if (rs != null) {
          if (me == null) {
            LOGGER.warning("closing unreferenced local ResultSetCursor {0}", rs);
          }
          rs.close();
        }
        else if (remoteCursor != null) {
          if (me == null) {
            LOGGER.warning("closing unreferenced remote ResultSetCursor {0}", remoteCursor);
          }
          try {
            remoteCursor.close();
          }
          catch (RuntimeException ex) {
            LOGGER.severe("cleanup " + remoteCursor + " failed", ex);
          }
        }
      }
      finally {
        me = null;
        rs = null;
        remoteCursor = null;
      }
    }
  }


  private final Resources<T> resources;                   // resources for cleanup
  private final Cleanable cleanable;                      // the cleanable to close
  private final T pdoProxy;                               // the pdo proxy (only for local sessions)
  private final JoinedSelect<T> js;                       // the join configuration (only for local sessions)
  private final Session session;                          // the session
  private final DomainContext context;                    // the domain context

  private int row;                                        // current row, 0 = before first
  private boolean firstInvoked;                           // true = first() has been invoked
  private boolean noNext;                                 // true = no next() in joined get
  private boolean eof;                                    // true = end of result set in joined get


  /**
   * Creates a cursor for a local session.
   *
   * @param pdo the PDO proxy
   * @param rs the result set
   * @param js the join configuration, null if no joins
   */
  public ResultSetCursor(T pdo, ResultSetWrapper rs, JoinedSelect<T> js) {
    this.pdoProxy = pdo;
    this.js = js;

    if (js != null) {
      js.initialize(null);
    }
    context = pdo.getPersistenceDelegate().getDomainContext();
    session = context.getSession();
    resources = new Resources<>(rs, null);

    assertSessionIsLocal();
    ((AbstractPersistentObject<T,?>) pdo.getPersistenceDelegate()).assertRootContextIsAccepted();
    cleanable = CLEANER.register(this, resources);
  }

  /**
   * Creates a cursor for a local session.
   *
   * @param pdo the PDO proxy
   * @param rs the result set
   */
  public ResultSetCursor(T pdo, ResultSetWrapper rs) {
    this(pdo, rs, null);
  }


  /**
   * Creates a cursor for a remote session.<br>
   * Constructor for pdo objects.
   *
   * @param context the domain context
   * @param remoteCursor the remote cursor
   */
  public ResultSetCursor(DomainContext context, RemoteResultSetCursor<T> remoteCursor) {
    this.context = context;

    session = context.getSession();
    pdoProxy = null;
    js = null;
    resources = new Resources<>(null, remoteCursor);

    assertSessionIsRemote();
    cleanable = CLEANER.register(this, resources);
  }

  /**
   * Gets the result set.
   *
   * @return the result set
   */
  public ResultSetWrapper getResultSet() {
    assertSessionIsLocal();
    return resources.rs;
  }

  /**
   * Gets the join configuration.<br>
   *
   * @return the join configuration, null if no joins
   * @throws PersistenceException if session is remote
   */
  public JoinedSelect<T> getJoinedSelect() {
    assertSessionIsLocal();
    return js;
  }

  /**
   * Gets the remote cursor.
   *
   * @return the remote cursor
   * @throws PersistenceException if session is local
   */
  public RemoteResultSetCursor<T> getRemoteCursor() {
    assertSessionIsRemote();
    return resources.remoteCursor;
  }

  /**
   * Gets the session.
   *
   * @return the session
   */
  public Session getSession() {
    return session;
  }

  /**
   * Gets the domain context.
   *
   * @return the context
   */
  public DomainContext getDomainContext() {
    return context;
  }


  /**
   * Closes the cursor.<br>
   * The cursor is opened in its constructor.
   * Closing an already closed cursor is allowed.
   */
  @Override
  public void close() {
    try {
      resources.me = this;    // mark as programmatic close, before the cleaner may run
      cleanable.clean();
    }
    finally {
      /*
       * Keep this cursor reachable until the cleanup is done. Without the fence the JIT may treat
       * it as unreachable as soon as 'resources' and 'cleanable' have been loaded, letting the
       * cleaner thread run the cleanup concurrently -- which would log the cursor as unreferenced
       * even though it is being closed properly right here.
       */
      Reference.reachabilityFence(this);
    }
  }


  /**
   * Returns whether the cursor is closed.
   *
   * @return true if closed
   */
  @Override
  public boolean isClosed() {
    return resources.rs == null && resources.remoteCursor == null;
  }


  /**
   * Gets the current row.<br>
   * Row numbers start at 1.
   *
   * @return the current row, 0 if before first row
   */
  @Override
  public int getRow() {
    return row;
  }

  /**
   * Sets the cursor to a given row.<br>
   * Row numbers start at 1.
   *
   * @param row the row number (must be &ge; 0)
   * @return true if done, false if no such row and the cursor is now before the first row or after the last row
   */
  @Override
  public boolean setRow (int row) {
    if (row < 0) {
      throw new PersistenceException(session, "invalid row " + row);
    }

    if (session.isRemote()) {
      try {
        this.row = resources.remoteCursor.setRow(row);
        return this.row == row;
      }
      catch (RuntimeException ex) {
        throw new PersistenceException(session, ex);
      }
    }

    assertNoJoins();
    boolean rv = resources.rs.absolute(row);
    if (rv) {
      this.row = row;
    }
    else  {
      this.row = resources.rs.getRow();
    }
    firstInvoked = false;
    return rv;
  }

  /**
   * Rewinds the cursor to the first row.
   *
   * @return true if rewound, false if cursor is empty
   */
  @Override
  public boolean first() {

    if (session.isRemote()) {
      try {
        boolean rv = resources.remoteCursor.first();
        row = rv ? 1 : 0;
        return rv;
      }
      catch (RuntimeException ex) {
        throw new PersistenceException(session, ex);
      }
    }

    boolean rv = false;

    if (js != null) {
      js.initialize(null);
      eof = false;
      /*
       * Not noNext = true: the joined select has just been re-initialized, so there is no row
       * that has already been read and that the next next() would have to skip over. Worse, the
       * next() a few lines below would consume the flag itself and return true without advancing,
       * leaving the cursor before the first row with row == 0 -- which get() reads as "no row".
       */
      noNext = false;
    }

    /*
     * some drivers don't allow positioning in result-sets that are not TYPE_FORWARD_ONLY. However, first() is a
     * convenient way to test whether the result set contains some data at all. If the cursor is at the beginning or no
     * access made at all, we will do nothing or perform a next() instead.
     */
    if (row == 0) {
      // no access made yet
      rv = next();
    }
    else if (row == 1) {
      rv = true;    // already at first record
    }
    // rewind
    else if (resources.rs.first()) {
      row = 1;
      rv = true;
    }
    else {
      // else not on a valid row means: no rows
      row = 0;
    }

    firstInvoked = rv;    // last positioning of row was through first()
    return rv;
  }

  /**
   * Positions the cursor on the last row.
   *
   * @return true if positioned, false if cursor is empty
   */
  @Override
  public boolean last() {

    if (session.isRemote()) {
      try {
        row = resources.remoteCursor.last();
        return row > 0;
      }
      catch (RuntimeException ex) {
        throw new PersistenceException(session, ex);
      }
    }

    assertNoJoins();
    boolean rv = false;
    if (resources.rs.last()) {
      row = resources.rs.getRow();
      rv = true;
    }
    else {
      // else not on a valid row means: no rows
      row = 0;
    }

    firstInvoked = false;
    return rv;
  }

  /**
   * Moves the cursor to the next row.<br>
   * If there are no more rows the current row remains unchanged.
   *
   * @return true if moved, false if no more rows
   */
  @Override
  public boolean next() {

    if (session.isRemote()) {
      try {
        boolean rv = resources.remoteCursor.next();
        if (rv) {
          row++;
        }
        return rv;
      }
      catch (RuntimeException ex) {
        throw new PersistenceException(session, ex);
      }
    }

    boolean rv;

    if (eof) {
      rv = false;
    }
    else if (noNext) {
      noNext = false;
      rv = true;
    }
    else {
      rv = resources.rs.next();
      if (rv) {
        row++;
      }
    }

    firstInvoked = false;
    return rv;
  }

  /**
   * Moves the cursor to the previous row.<br>
   * If we are already at the beginning, the cursor remains unchanged.
   *
   * @return true if advanced, false if already at the beginning
   */
  @Override
  public boolean previous() {

    if (session.isRemote()) {
      try {
        boolean rv = resources.remoteCursor.previous();
        if (rv) {
          row--;
        }
        return rv;
      }
      catch (RuntimeException ex) {
        throw new PersistenceException(session, ex);
      }
    }

    assertNoJoins();
    boolean rv = resources.rs.previous();
    /*
     * Take the row from the result set instead of decrementing the counter, just like last() and
     * setRow() do. ResultSet.getRow() is 0 whenever the cursor is not on a row, so afterLast()
     * leaves this cursor's counter at 0 -- decrementing would yield -1, which get() reads as
     * "no row" although the result set is sitting on the last one.
     */
    row = rv ? resources.rs.getRow() : 0;
    firstInvoked = false;
    return rv;
  }


  /**
   * Moves the cursor a given number of rows.
   *
   * @param rows the number of rows to move, negative to move backwards
   * @return true if moved, false if no such row and the cursor is now positioned before the first row or after the last row
   */
  @Override
  public boolean scroll(int rows) {
    if (session.isRemote()) {
      try {
        int expectedRow = row + rows;
        row = resources.remoteCursor.scroll(rows);
        return row == expectedRow;
      }
      catch (RuntimeException ex) {
        throw new PersistenceException(session, ex);
      }
    }

    return setRow(row + rows);
  }



  /**
   * Positions the cursor before the first row.<br>
   * Works even for empty cursors.
   */
  @Override
  public void beforeFirst() {
    if (session.isRemote()) {
      try {
        resources.remoteCursor.beforeFirst();
        row = 0;
        return;
      }
      catch (RuntimeException ex) {
        throw new PersistenceException(session, ex);
      }
    }

    if (js != null) {
      js.initialize(null);
      eof = false;
      noNext = true;
    }
    resources.rs.beforeFirst();
    row = 0;
    firstInvoked = false;
  }

  /**
   * Positions the cursor after the last row.<br>
   * Works even for empty cursors.
   */
  @Override
  public void afterLast() {
    if (session.isRemote()) {
      try {
        row = resources.remoteCursor.afterLast();
        return;
      }
      catch (RuntimeException ex) {
        throw new PersistenceException(session, ex);
      }
    }

    assertNoJoins();
    resources.rs.afterLast();
    row = resources.rs.getRow();
    firstInvoked = false;
  }

  /**
   * Checks whether the cursor is before the first row.
   *
   * @return true if before first
   */
  @Override
  public boolean isBeforeFirst() {
    if (session.isRemote()) {
      return row == 0;
    }
    return resources.rs.isBeforeFirst();
  }

  /**
   * Checks whether the cursor is after the last row.
   *
   * @return true if after last
   */
  @Override
  public boolean isAfterLast() {
    if (session.isRemote()) {
      try {
        return resources.remoteCursor.isAfterLast();
      }
      catch (RuntimeException ex) {
        throw new PersistenceException(session, ex);
      }
    }
    return resources.rs.isAfterLast();
  }

  /**
   * Gets the data object of the current row.
   *
   * @return the object, null if invalid row or no such object
   */
  @Override
  @SuppressWarnings({ "unchecked", "rawtypes" })
  public T get() {
    if (row > 0) {
      if (session.isRemote()) {
        try {
          T obj = resources.remoteCursor.get();
          configureRemoteObject(obj);
          return obj;
        }
        catch (RuntimeException ex) {
          throw new PersistenceException(session, ex);
        }
      }
      // local:
      if (js == null) {
        T pdo = Pdo.create(pdoProxy);
        AbstractPersistentObject po = (AbstractPersistentObject) pdo.getPersistenceDelegate();
        return (T) po.derivePdoFromPo(pdo, po.readFromResultSetWrapper(resources.rs));
      }
      else {
        T pdo = js.currentPdo();
        for (;;) {
          if (firstInvoked) {
            /*
             * first() has positioned the cursor on a row without anyone reading it yet, so this
             * iteration reads that row instead of advancing. The flag must be cleared here: it is
             * otherwise only reset by next(), which this branch skips, so the loop would keep
             * reading the very same row.
             */
            firstInvoked = false;
          }
          else if (!next()) {
            eof = true;
            break;    // EOF
          }
          noNext = false;
          ((AbstractPersistentObject) pdoProxy.getPersistenceDelegate()).readJoinedRow(resources.rs, js);
          if (pdo == null) {
            // first access
            pdo = js.currentPdo();
          }
          else if (pdo != js.currentPdo()) {
            // new PDO detected (pDo contains current PDO, js.currentPdo is the next one)
            noNext = true;
            break;
          }
        }
        return pdo;
      }
    }
    return null;
  }

  /**
   * Returns the objects of this cursor as a list.
   *
   * @return the list
   */
  @Override
  public List<T> toList() {
    List<T> list;
    if (session.isRemote()) {
      // this is faster than a getObject()-loop because it saves TRIP roundtrips
      list = resources.remoteCursor.toList();    // unchecked
      if (list != null) {
        for (T obj : list) {
          configureRemoteObject(obj);
        }
      }
    }
    else {
      list = new ArrayList<>();
      toList(list);
    }
    return list;
  }

  /**
   * Returns the objects of this cursor as a list and closes this cursor.
   *
   * @return the list
   */
  @Override
  public List<T> toListAndClose() {
    List<T> list = toList();
    close();
    return list;
  }

  /**
   * Sets the fetch size.<br>
   * This is the number of rows the cursor will fetch
   * from the server in one batch.
   * A fetch size of 0 means server default.
   *
   * @param rows the fetch size
   */
  @Override
  public void setFetchSize(int rows) {
    if (session.isRemote()) {
      resources.remoteCursor.setFetchSize(rows);
    }
    else {
      resources.rs.setFetchSize(rows);
    }
  }

  /**
   * Gets the fetch size.
   *
   * @return the fetch size
   */
  @Override
  public int getFetchSize() {
    if (session.isRemote()) {
      return resources.remoteCursor.getFetchSize();
    }
    return resources.rs.getFetchSize();
  }

  /**
   * Sets the fetch direction.
   *
   * @param direction the direction, see {@link java.sql.ResultSet#setFetchDirection}.
   */
  public void setFetchDirection(int direction) {
    resources.rs.setFetchDirection(direction);
  }

  /**
   * Gets the fetch direction.
   *
   * @return the direction
   */
  public int getFetchDirection() {
    return resources.rs.getFetchDirection();
  }

  /**
   * Fetches the next objects up to the fetch size.<br>
   * This method is provided to minimize the number of
   * roundtrips especially for remote cursors.
   * The cursor is closed at the end of the cursor.
   *
   * @return the list of objects, null if no more objects found
   */
  @Override
  public FetchList<T> fetch() {

    FetchList<T> list = null;

    if (!isClosed()) {
      if (session.isRemote()) {
        // this is faster than a get()-loop because it saves TRIP roundtrips
        list = resources.remoteCursor.fetch();    // unchecked
        if (list != null) {
          for (T obj : list) {
            configureRemoteObject(obj);
          }
          row += list.size();
          if (firstInvoked) {
            firstInvoked = false;
            row--;    // subtract cause row started at 1, not 0
          }
          if (list.isClosed()) {
            resources.remoteCursor = null;    // already closed on remote side
          }
        }
        else {
          resources.remoteCursor = null;    // already closed on remote side
        }
      }
      else {

        int fetchMax = getFetchSize();

        if (fetchMax < 1) {
          // fallback
          list = new FetchList<>();
          toList(list);
          list.setClosed(true);
          close();
        }
        else {
          // load next fetch block
          list = new FetchList<>();
          // special optimization if first() has been invoked before (to prevent beforeFirst()
          // called on FORWARD_ONLY cursors, some databases don't support that, i.e. Oracle)
          if (firstInvoked && row == 1) {
            T obj = get();
            if (obj != null) {
              list.add(obj);
            }
            firstInvoked = false;
          }
          // load the rest
          fetchMax = ((row / fetchMax) * fetchMax) + fetchMax;
          while (row < fetchMax) {
            if (next()) {
              T obj = get();
              if (obj != null) {
                list.add(obj);
              }
            }
            else {
              // end reached: close cursor
              close();
              list.setClosed(true);
              if (list.isEmpty()) {
                list = null;
              }
              break;
            }
          }
        }
      }
    }

    return list;
  }


  /**
   * Adds the rest of the cursor to a list.
   *
   * @param list the list
   */
  protected void toList(List<T> list) {
    boolean exists = first();
    while (exists)  {
      T obj = get();
      if (obj != null) {
        list.add(obj);
      }
      exists = next();
    }
  }


  /**
   * Configures the remotely retrieved PDO.
   *
   * @param pdo the PDO
   */
  @SuppressWarnings("unchecked")
  protected void configureRemoteObject(T pdo) {
    if (context != null) {
      AbstractPersistentObject<T, ?> po = (AbstractPersistentObject<T, ?>) pdo.getPersistenceDelegate();
      po.configureRemoteObject(context, pdo);
    }
    else if (pdo != null) {
      session.applyTo(pdo);
    }
  }


  /**
   * Asserts that session is remote.
   */
  protected void assertSessionIsRemote() {
    if (!session.isRemote()) {
      throw new PersistenceException(session, "operation not allowed for local sessions");
    }
  }

  /**
   * Asserts that session is local.
   */
  protected void assertSessionIsLocal() {
    if (session.isRemote()) {
      throw new PersistenceException(session, "operation not allowed for remote sessions");
    }
  }

  /**
   * Asserts that there are no joins in the query.
   */
  protected void assertNoJoins() {
    if (js != null) {
      throw new PersistenceException(session, "operation not allowed for cursors with joins");
    }
  }


  /**
   * Adds a closed flag to saveObject a roundtrip in remote sessions.
   */
  public static class FetchList<T> extends ArrayList<T> {

    @Serial
    private static final long serialVersionUID = 1L;

    private boolean closed;

    /**
     * Creates an empty non-closed fetch list.
     */
    public FetchList() {
      // see -Xlint:missing-explicit-ctor since Java 16
    }

    /**
     * Returns whether list is closed.
     *
     * @return true if this is the last fetch
     */
    public boolean isClosed() {
      return closed;
    }

    /**
     * Sets the list to be closed.
     *
     * @param closed true if this is the last fetch
     */
    public void setClosed(boolean closed) {
      this.closed = closed;
    }
  }

}
