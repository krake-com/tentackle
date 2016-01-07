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


package org.tentackle.persist;

import java.io.Serializable;
import org.tentackle.pdo.PersistenceException;
import org.tentackle.pdo.Session;
import org.tentackle.pdo.SessionDependable;
import org.tentackle.pdo.SessionHolder;
import org.tentackle.persist.rmi.AbstractDbOperationRemoteDelegate;

/**
 * A {@code AbstractDbOperation} provides methods that are not part of {@link AbstractDbObject}s
 * and is associated to a {@link Db}-session. Complex transactions are usually
 * {@code AbstractDbOperation}s. {@code AbstractDbOperation}s are remote capabable.
 *
 * @param <P> the operation class type
 * @author harald
 */
public abstract class AbstractDbOperation<P extends AbstractDbOperation<P>>
                implements SessionDependable, Cloneable, Serializable {

  private static final long serialVersionUID = 1L;

  private Db db;                                          // the bound db connection
  private transient boolean sessionImmutable;             // true if Db is immutable
  private transient SessionHolder sessionHolder;          // a redirector to retrieve the Db


  /**
   * Creates a database object.
   *
   * @param db the logical {@link Db}-connection
   */
  public AbstractDbOperation(Db db) {
    setSession(db);
  }


  /**
   * Creates a database object not associated to a logical {@link Db}-connection.
   * The connection must be set via {@link #setDb} in order to use it.
   */
  public AbstractDbOperation() {
    // nothing to do
  }


  /**
   * Clones the operation object.<p>
   *
   * @return a clone of this object
   */
  @Override
  @SuppressWarnings("unchecked")
  public P clone() {
    P obj;
    try {
      obj = (P) super.clone();
    }
    catch (CloneNotSupportedException ex) {
      throw new InternalError();    // should never happen
    }
    return obj;
  }


  /**
   * Gets the some attributes and variables common to all objects of the same class.
   * Class variables for classes derived from {@link AbstractDbOperation} are kept in an
   * instance of {@link DbOperationClassVariable}.
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
   * @param db the db connection
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
