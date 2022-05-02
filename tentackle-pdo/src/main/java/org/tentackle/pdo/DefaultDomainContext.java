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

package org.tentackle.pdo;

import org.tentackle.common.StringHelper;
import org.tentackle.common.TentackleRuntimeException;
import org.tentackle.session.PersistenceException;
import org.tentackle.session.Session;
import org.tentackle.session.SessionInfo;

import java.io.ObjectStreamException;
import java.io.Serial;
import java.util.Arrays;
import java.util.List;


/**
 * The default application's domain context.
 *
 * @author harald
 */
public class DefaultDomainContext implements DomainContext {

  @Serial
  private static final long serialVersionUID = 1L;

  /** the unnamed context name chain. */
  private static final List<String> UNNAMED_LIST = List.of("");


  // attributes are transient because session could be remote (is serializable)

  /**
   * the session.
   */
  private transient Session session;
  private transient boolean sessionValid;     // false if not created by constructor, i.e. sent over wire

  /**
   * session immutable flag.
   */
  private transient boolean sessionImmutable;

  /**
   * the root entity, null if none.
   * <p>
   * Notice that the root-entity is transient!
   * For remote sessions the rootId and rootClassId will be transferred.
   */
  private transient PersistentDomainObject<?> rootEntity;

  /**
   * Lazily created chained context name.<br>
   * Defaults to {@code "<unnamed>"}.
   */
  private transient String chainedName;


  // non transient

  /**
   * ID of the root entity.<br>
   * Necessary in remote sessions.
   */
  private long rootId;

  /**
   * Class-ID of the root entity.<br>
   * Necessary in remote sessions.
   */
  private int rootClassId;

  /**
   * thread local session clonePdo of this domain context.
   */
  private transient DefaultDomainContext tlsContext;

  /**
   * the non-root context.
   */
  private DefaultDomainContext nonRootContext;

  /**
   * The context name chain.<br>
   * Array is faster to serialize/deserialize than List.
   */
  private String[] names;                     // null if unnamed (internal only)
  private transient List<String> namesList;   // lazy unmodifiable list for external API



  /**
   * Creates a default context.
   *
   * @param session the session, null if thread-local
   * @param sessionImmutable true if session cannot be changed anymore
   */
  public DefaultDomainContext(Session session, boolean sessionImmutable) {
    this.session = session;     // don't use setSession() as this will also assertPermissions
    this.sessionValid = true;
    this.sessionImmutable = sessionImmutable;
    if (session == null) {
      Session.assertCurrentSessionValid();
    }
  }

  /**
   * Creates a mutable default context.
   *
   * @param session the session, null if thread-local
   */
  public DefaultDomainContext(Session session) {
    this(session, false);
  }


  @Override
  public List<String> getNames() {
    if (namesList == null) {
      namesList = names == null ? UNNAMED_LIST : List.of(names);
    }
    return namesList;
  }

  @Override
  public boolean isSessionThreadLocal() {
    return session == null;
  }

  /**
   * {@inheritDoc}<br>
   * If the context's session is null the thread's local
   * session is returned.
   *
   * @see Session#getCurrentSession()
   */
  @Override
  public Session getSession() {
    if (isSessionThreadLocal()) {
      return Session.getCurrentSession();
    }
    return session;
  }


  @Override
  public void setSession(Session session) {
    if (sessionValid) {
      if (isSessionThreadLocal()) {
        if (session != null && session != getSession()) {
          // A thread-local session cannot be changed to another fixed session.
          // To do so, a new context is necessary.
          // Same session as thread-local is ok, however.
          throw new PersistenceException("illegal attempt to change the thread-local session of " +
                                         getClass().getSimpleName() + " '" + this +
                                         "' from " + getSession() + " to " + session);
        }
        // else no change: requested session is null or the same as the thread-local session
      }
      else {    // this.session != null
        if (isSessionImmutable() && this.session != session) {
          throw new PersistenceException(this.session, "illegal attempt to change the immutable session of " +
                                                       getClass().getSimpleName() + " '" + this +
                                                       "' from " + this.session + " to " + session);
        }
        this.session = session;
        if (nonRootContext != null) {
          nonRootContext.session = session;
        }
      }
    }
    else {
      // first setting after deserialization (DomainContext was not created by constructor!)
      this.session = session;
      if (session == null) {
        Session.assertCurrentSessionValid();
      }
      sessionValid = true;
      assertPermissions();
    }
  }


  /**
   * Sets the session to immutable.
   *
   * @param sessionImmutable true if session cannot be changed anymore
   */
  @Override
  public void setSessionImmutable(boolean sessionImmutable) {
    this.sessionImmutable = sessionImmutable;
  }

  /**
   * Returns whether the session is immutable.
   *
   * @return true if immutable
   */
  @Override
  public boolean isSessionImmutable() {
    return sessionImmutable;
  }



  @Override
  public int getSessionInstanceNumber() {
    // the instance-no must be 0 for dynamic thread-local session (due to sorting, e.g. in caches)
    return session == null ? 0 : session.getInstanceNumber();
  }

  @Override
  public SessionInfo getSessionInfo() {
    Session s = getSession();
    if (s == null) {
      throw new PdoRuntimeException("no session for context " + this);
    }
    return s.getSessionInfo();
  }


  @Override
  public void assertPermissions() {
  }


  @Override
  public PersistentDomainObject<?> getContextPdo()  {
    return null;
  }


  @Override
  public long getContextId()  {
    return 0;
  }


  @Override
  public boolean isWithinContext(long contextId, int contextClassId) {
    return contextId < 0 || contextId == getContextId();
  }

  @Override
  public boolean isWithinContext(String name) {
    for (String n : getNames()) {   // getNames will always begin with the empty string!
      if (n.equals(name)) {
        return true;
      }
    }
    return false;
  }


  /**
   * Gets the string representation of this context.
   * The default implementation returns the string of the context object,
   * or the empty string (not the null-string!), if no such object,
   * which is the case for the plain context.
   *
   * @return the string
   */
  @Override
  public String toString()  {
    // context does not include Session.toString() because DomainContext.toString() is
    // heavily used in GUIs to show the user's context.
    return getContextId() == 0 ? "" : getContextPdo().toString();
  }


  @Override
  public String toGenericString() {
    if (chainedName == null) {
      chainedName = createChainedName();
    }
    long contextId = getContextId();
    if (contextId != 0) {
      return chainedName + "[" + getContextId() + "]";
    }
    return chainedName;
  }


  @Override
  public String toDiagnosticString() {
    StringBuilder buf = new StringBuilder();
    if (isRootContext()) {
      buf.append("root ");
    }
    buf.append("context '").append(toGenericString()).append("' using ");
    if (isSessionThreadLocal()) {
      buf.append("thread-local ");
    } // thread-local implicitly means immutable
    else if (isSessionImmutable()) {
      buf.append("immutable ");
    }
    buf.append("session ");
    Session session = getSession();
    if (session != null) {
      buf.append(session.getName());
    }
    else {
      buf.append("<none>");
    }
    return buf.toString();
  }


  /**
   * Compares this domain context with another domain context.<br>
   * The default implementation just compares the class, the contextId
   * and the session.<br>
   * Checking against the null context returns 1.
   *
   * @param otherContext the context to compare this context to
   */
  @Override
  public int compareTo(DomainContext otherContext) {
    if (otherContext == null) {
      return 1;
    }
    int rv = getClass().hashCode() - otherContext.getClass().hashCode();
    if (rv == 0) {
      rv = Long.compare(getContextId(), otherContext.getContextId());
      if (rv == 0) {
        rv = getSessionInstanceNumber() - otherContext.getSessionInstanceNumber();
      }
    }
    return rv;
  }


  /**
   * {@inheritDoc}
   * <p>
   * Overridden to check whether contexts are equal.<br>
   * The default implementation checks the class, the contextId and session for equality.
   * Checking against the null context returns false.
   */
  @Override
  public boolean equals(Object obj) {
    return obj != null &&
           getClass() == obj.getClass() &&
           getContextId() == ((DomainContext) obj).getContextId() &&
           getSessionInstanceNumber() == ((DomainContext) obj).getSessionInstanceNumber();
  }

  @Override
  public int hashCode() {
    int hash = 7 + (int) getContextId() + (getClass().hashCode() & 0xffff);
    hash = 53 * hash + (session != null ? session.hashCode() : 0);    // not getSession() bec. of thread-local!
    return hash;
  }



  @Override
  public DefaultDomainContext clone() {
    try {
      DefaultDomainContext context = (DefaultDomainContext) super.clone();
      context.nonRootContext = nonRootContext;
      context.rootEntity = null;
      context.rootId = 0;
      context.rootClassId = 0;
      context.sessionImmutable = false;
      context.tlsContext = null;
      return context;
    }
    catch (CloneNotSupportedException ex) {
      throw new TentackleRuntimeException(ex);    // this shouldn't happen, since we are Cloneable
    }
  }

  @Override
  public DefaultDomainContext clone(String name) {
    if (StringHelper.isAllWhitespace(name) || name.contains(":")) {
      throw new DomainException("illegal context name: '" + name + "'");
    }
    if (isWithinContext(name)) {
      throw new DomainException("context name '" + name + "' already within " + toDiagnosticString());
    }
    DefaultDomainContext context = clone();
    if (names == null) {
      context.names = new String[]{"", name};
    }
    else {
      context.names = Arrays.copyOf(names, names.length + 1);
      context.names[names.length] = name;
    }
    return context;
  }

  @Override
  public DomainContext getThreadLocalSessionContext() {
    if (tlsContext == null) {
      tlsContext = clone();
      tlsContext.session = null;
      tlsContext.sessionImmutable = true;
      tlsContext.tlsContext = tlsContext;
    }
    return tlsContext;
  }

  @Override
  public void clearThreadLocalSessionContext() {
    tlsContext = null;
  }

  @Override
  public DefaultDomainContext getNonRootContext() {
    DefaultDomainContext context = nonRootContext != null ? nonRootContext : this;
    if (context.isRootContext()) {
      throw new PersistenceException(context + " is a root context");
    }
    return context;
  }


  @Override
  public boolean isRootContext() {
    return getRootClassId() != 0;
  }


  @Override
  public DomainContext getRootContext(PersistentDomainObject<?> rootEntity) {
    if (rootEntity == null) {
      throw new PersistenceException("rootEntity must not be null");
    }
    if (this.rootEntity == rootEntity) {
      return this;
    }

    /*
     * Important:
     * we cannot invoke methods on rootEntity if we're in PDO creation because the delegate
     * being created is not yet available by the proxy's mixin.
     * Hence, we load the rootClassId and rootId when invoked the first time
     * or when serialized (see writeReplace()).
     */
    PersistentObject<?> po = rootEntity.getPersistenceDelegate();
    if (po != null) {
      // not in object creation: we can invoke methods
      if (!po.isRootEntity()) {
        throw new PersistenceException(rootEntity.toGenericString() + " is not a root entity");
      }
      if (po.getId() == rootId && po.getRootClassId() == rootClassId) {
        // just update the link
        this.rootEntity = rootEntity;
        return this;
      }
    }

    // create a new root context
    DefaultDomainContext rootContext = clone();
    rootContext.rootEntity = rootEntity;
    rootContext.nonRootContext = getNonRootContext();
    return rootContext;
  }

  /**
   * This does the trick to set up the non-transient rootId and rootClassId when sent via rmi the first time.
   *
   * @return me
   * @throws ObjectStreamException to fulfill the signature only
   * @see ObjectStreamException
   */
  @Serial
  // public! Otherwise, writeReplace would not be invoked in subclasses!
  public Object writeReplace() throws ObjectStreamException {
    if (rootEntity != null && rootClassId == 0) {
      rootClassId = rootEntity.getPersistenceDelegate().getClassId();
      rootId = rootEntity.getPersistenceDelegate().getId();
    }
    return this;
  }

  @Override
  public PersistentDomainObject<?> getRootEntity() {
    return rootEntity;
  }

  @Override
  public int getRootClassId() {
    return rootEntity == null ? rootClassId : rootEntity.getPersistenceDelegate().getClassId();
  }

  @Override
  public long getRootId() {
    return rootEntity == null ? rootId : rootEntity.getPersistenceDelegate().getId();
  }


  private String createChainedName() {
    if (names == null) {
      return "";
    }
    StringBuilder buf = new StringBuilder();
    boolean needColon = false;
    for (String name : names) {
      if (!name.isEmpty()) {    // skip first ""
        if (needColon) {
          buf.append(':');
        }
        else {
          needColon = true;
        }
        buf.append(name);
      }
    }
    return buf.toString();
  }

}
