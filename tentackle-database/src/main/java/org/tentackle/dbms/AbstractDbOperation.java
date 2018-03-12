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


package org.tentackle.dbms;

import java.io.Serializable;
import org.tentackle.dbms.rmi.AbstractDbOperationRemoteDelegate;
import org.tentackle.session.PersistenceException;
import org.tentackle.session.Session;
import org.tentackle.session.SessionDependable;
import org.tentackle.session.SessionHolder;

/**
 * A {@code AbstractDbOperation} provides methods that are not part of {@link AbstractDbObject}s
 * and is associated to a {@link Db}-session. Complex transactions are usually
 * {@code AbstractDbOperation}s. {@code AbstractDbOperation}s are remoting capabable.
 *
 * @param <P> the operation class type
 * @author harald
 */
public abstract class AbstractDbOperation<P extends AbstractDbOperation<P>>
                implements SessionDependable, Serializable {

  private static final long serialVersionUID = 1L;

  private transient Db db;                                // the bound db connection
  private transient boolean sessionImmutable;             // true if Db is immutable
  private transient SessionHolder sessionHolder;          // a redirector to retrieve the Db


  /**
   * Creates an operation object.
   *
   * @param session the session
   */
  public AbstractDbOperation(Db session) {
    setSession(session);
  }


  /**
   * Creates an operation object not associated to a session.<br>
   * The session must be set via {@link #setSession} in order to use it.
   */
  public AbstractDbOperation() {
    // nothing to do
  }


  /**
   * Gets the some attributes and variables common to all objects of the same class.
   * Class variables for classes derived from {@link AbstractDbOperation} are kept in an
   * instance of {@link DbOperationClassVariables}.
   *
   * @return the class variables
   */
  public DbOperationClassVariables<P> getClassVariables() {
    throw new PersistenceException("classvariables not initialized for " + getClass());
  }


  /**
   * Sets the session holder for this object.
   * <p>
   * If a holder is set, getSession() will return the session from the holder.
   *
   * @param sessionHolder the session holder
   */
  public void setSessionHolder(SessionHolder sessionHolder) {
    this.sessionHolder = sessionHolder;
  }

  /**
   * Gets the session holder.
   *
   * @return the holder, null if none
   */
  public SessionHolder getSessionHolder() {
    return sessionHolder;
  }


  /**
   * Sets the db to immutable.
   *
   * @param sessionImmutable true if db cannot be changed anymore
   */
  @Override
  public void setSessionImmutable(boolean sessionImmutable) {
    this.sessionImmutable = sessionImmutable;
  }

  /**
   * Returns whether the db is immutable.
   *
   * @return true if immutable
   */
  @Override
  public boolean isSessionImmutable() {
    SessionHolder holder = getSessionHolder();
    if (holder != null) {
      return holder.isSessionImmutable();
    }
    return sessionImmutable;
  }


  /**
   * Sets the logical db connection for this object.
   *
   * @param session the session
   */
  @Override
  public void setSession(Session session) {
    SessionHolder holder = getSessionHolder();
    if (holder != null) {
      holder.setSession(session);
    }
    else {
      if (isSessionImmutable() && this.db != session) {
        throw new PersistenceException(this.db, "illegal attempt to change the immutable Db of " + this +
                                                " from " + this.db + " to " + session);
      }
      this.db = (Db) session;
    }
  }


  /**
   * Get the logical db connection for this object.
   *
   * @return the db connection
   */
  @Override
  public Db getSession() {
    SessionHolder holder = getSessionHolder();
    if (holder != null) {
      return (Db) holder.getSession();
    }
    return db;
  }


  /**
   * Gets the delegate for remote connections.<br>
   * Each class has its own delegate.
   *
   * @return the delegate for this object
   */
  @SuppressWarnings("unchecked")
  public AbstractDbOperationRemoteDelegate<P> getRemoteDelegate()  {
    return getClassVariables().getRemoteDelegate(getSession());
  }

}
