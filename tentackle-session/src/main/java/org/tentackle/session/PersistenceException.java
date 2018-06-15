/*
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


package org.tentackle.session;

import java.lang.StackWalker.Option;
import java.rmi.RemoteException;
import java.sql.SQLException;
import java.util.List;
import java.util.stream.Collectors;
import org.tentackle.common.ExceptionHelper;
import org.tentackle.common.TentackleRuntimeException;
import org.tentackle.misc.Identifiable;

/**
 * Database runtime exception.
 *
 * @author harald
 */
public class PersistenceException extends TentackleRuntimeException {

  private static final long serialVersionUID = -5355906838551059053L;

  /** Tag starting extra identifiable/session info in message. */
  public static final String START_INFO = "[_ ";

  /** Tag ending extra identifiable/session info in message. */
  public static final String END_INFO = " _]";


  /**
   * Creates a RuntimeException from a {@link RemoteException}.<br>
   * Returns the first RuntimeException in chain.
   * If the first non-RemoteException is not a RuntimeException, the exception
   * is wrapped by a PersistenceException.
   *
   * @param relatedObject the optional related object, only used if remote cause is not a {@link PersistenceException}
   * @param remoteException the remote exception
   * @return the runtime exception
   */
  public static RuntimeException createFromRemoteException(Object relatedObject, RemoteException remoteException) {
    // find the first cause in chain which is not a RemoteException
    Throwable remoteCause = remoteException;
    while (remoteCause instanceof RemoteException && remoteCause.getCause() != null) {
      remoteCause = remoteCause.getCause();
    }

    RuntimeException pex;

    if (remoteCause instanceof RuntimeException) {
      pex = (RuntimeException) remoteCause;
    }
    else  {
      // other exception found
      if (relatedObject instanceof Identifiable) {
        pex = new PersistenceException((Identifiable) relatedObject, remoteCause);
      }
      else if (relatedObject instanceof Session) {
        pex = new PersistenceException((Session) relatedObject, remoteCause);
      }
      else  {
        pex = new PersistenceException((relatedObject != null ? relatedObject.toString() : ""), remoteCause);
      }
    }

    StackWalker walker = StackWalker.getInstance(Option.RETAIN_CLASS_REFERENCE);
    List<StackTraceElement> stack = walker.walk(s ->
      s.filter(sf -> ExceptionHelper.isClassValuableForStackTrace(sf.getClassName())).map(sf ->
        new StackTraceElement(sf.getDeclaringClass().getName(), sf.getMethodName(), sf.getFileName(), sf.getLineNumber())).
          collect(Collectors.toList()));
    if (stack.isEmpty()) {
      // pathological case: don't filter
      walker.walk(s -> s.map(sf ->
        new StackTraceElement(sf.getDeclaringClass().getName(), sf.getMethodName(), sf.getFileName(), sf.getLineNumber())).
          collect(Collectors.toList()));
    }
    StackTraceElement[] localTrace = new StackTraceElement[stack.size()];
    stack.toArray(localTrace);
    StackTraceElement[] remoteTrace = pex.getStackTrace();
    StackTraceElement[] trace = new StackTraceElement[localTrace.length + remoteTrace.length];
    System.arraycopy(remoteTrace, 0, trace, 0, remoteTrace.length);
    trace[remoteTrace.length] = new StackTraceElement(">>>>> REMOTE METHOD INVOCATION >>>>>", "", "", 0);
    System.arraycopy(localTrace, 1, trace, remoteTrace.length + 1, localTrace.length - 1);
    pex.setStackTrace(trace);

    return pex;
  }



  /**
   * Extracts the {@link PersistenceException} from an exception.
   * <p>
   * Scans the exception chain until it finds an {@link PersistenceException}.
   *
   * @param e the exception head
   * @return the DbRuntimeException, null if none
   */
  public static PersistenceException extractPersistenceException(Throwable e) {
    while (e != null) {
      if (e instanceof PersistenceException) {
        return (PersistenceException) e;
      }
      e = e.getCause();
    }
    return null;
  }


  private transient Session session;        // the db connection this exception belongs to
  private Identifiable identifiable;        // the db object this execption belongs to
  private String lazyMessage;               // null if messsage not computed yet


  /**
   * Constructs a new database runtime exception for a given db connection
   * with <code>null</code> as its detail message.
   * The cause is not initialized, and may subsequently be
   * initialized by a call to {@link #initCause}.
   *
   * @param session the session
   */
  public PersistenceException(Session session) {
    super();
    setSession(session);
  }


  /**
   * Constructs a new database runtime exception for a given db connection with the specified detail message.
   * The cause is not initialized, and may subsequently be initialized by a
   * call to {@link #initCause}.
   *
   * @param   session the session
   * @param   message   the detail message. The detail message is saved for
   *          later retrieval by the {@link #getMessage()} method.
   */
  public PersistenceException(Session session, String message) {
    super(message);
    setSession(session);
  }

  /**
   * Constructs a new database runtime exception for a given db connection with the specified detail message and
   * cause.  <p>Note that the detail message associated with
   * <code>cause</code> is <i>not</i> automatically incorporated in
   * this runtime exception's detail message.
   *
   * @param  session the session
   * @param  message the detail message (which is saved for later retrieval
   *         by the {@link #getMessage()} method).
   * @param  cause the cause (which is saved for later retrieval by the
   *         {@link #getCause()} method).  (A <tt>null</tt> value is
   *         permitted, and indicates that the cause is nonexistent or
   *         unknown.)
   * @since  1.4
   */
  public PersistenceException(Session session, String message, Throwable cause) {
    super(message, translateSQLException(cause));
    setSession(session);
  }

  /**
   * Constructs a new database runtime exception for a given db connection with the specified cause and a
   * detail message of <tt>(cause==null ? null : cause.toString())</tt>
   * (which typically contains the class and detail message of
   * <tt>cause</tt>).  This constructor is useful for runtime exceptions
   * that are little more than wrappers for other throwables.
   *
   * @param  session the session
   * @param  cause the cause (which is saved for later retrieval by the
   *         {@link #getCause()} method).  (A <tt>null</tt> value is
   *         permitted, and indicates that the cause is nonexistent or
   *         unknown.)
   * @since  1.4
   */
  public PersistenceException(Session session, Throwable cause) {
    super(translateSQLException(cause));
    setSession(session);
  }



  /**
   * Constructs a new database runtime exception for a given db object
   * with <code>null</code> as its detail message.
   * The cause is not initialized, and may subsequently be
   * initialized by a call to {@link #initCause}.
   *
   * @param identifiable the db object
   */
  public PersistenceException(Identifiable identifiable) {
    super();
    setIdentifiable(identifiable);
  }


  /**
   * Constructs a new database runtime exception for a given db connection with the specified detail message.
   * The cause is not initialized, and may subsequently be initialized by a
   * call to {@link #initCause}.
   *
   * @param   object the object
   * @param   message   the detail message. The detail message is saved for
   *          later retrieval by the {@link #getMessage()} method.
   */
  public PersistenceException(Identifiable object, String message) {
    super(message);
    setIdentifiable(object);
  }

  /**
   * Constructs a new database runtime exception for a given db connection with the specified detail message and
   * cause.  <p>Note that the detail message associated with
   * <code>cause</code> is <i>not</i> automatically incorporated in
   * this runtime exception's detail message.
   *
   * @param  object the object
   * @param  message the detail message (which is saved for later retrieval
   *         by the {@link #getMessage()} method).
   * @param  cause the cause (which is saved for later retrieval by the
   *         {@link #getCause()} method).  (A <tt>null</tt> value is
   *         permitted, and indicates that the cause is nonexistent or
   *         unknown.)
   * @since  1.4
   */
  public PersistenceException(Identifiable object, String message, Throwable cause) {
    super(message, translateSQLException(cause));
    setIdentifiable(object);
  }

  /**
   * Constructs a new database runtime exception for a given db connection with the specified cause and a
   * detail message of <tt>(cause==null ? null : cause.toString())</tt>
   * (which typically contains the class and detail message of
   * <tt>cause</tt>).  This constructor is useful for runtime exceptions
   * that are little more than wrappers for other throwables.
   *
   * @param  object the object
   * @param  cause the cause (which is saved for later retrieval by the
   *         {@link #getCause()} method).  (A <tt>null</tt> value is
   *         permitted, and indicates that the cause is nonexistent or
   *         unknown.)
   * @since  1.4
   */
  public PersistenceException(Identifiable object, Throwable cause) {
    super(translateSQLException(cause));
    setIdentifiable(object);
  }



  /**
   * Constructs a new database runtime exception without a db connection and
   * with <code>null</code> as its detail message.
   * The cause is not initialized, and may subsequently be
   * initialized by a call to {@link #initCause}.
   */
  public PersistenceException() {
    super();
  }


  /**
   * Constructs a new database runtime exception without a db connection with the specified detail message.
   * The cause is not initialized, and may subsequently be initialized by a
   * call to {@link #initCause}.
   *
   * @param   message   the detail message. The detail message is saved for
   *          later retrieval by the {@link #getMessage()} method.
   */
  public PersistenceException(String message) {
    super(message);
  }

  /**
   * Constructs a new database runtime exception without a db connection with the specified detail message and
   * cause.  <p>Note that the detail message associated with
   * <code>cause</code> is <i>not</i> automatically incorporated in
   * this runtime exception's detail message.
   *
   * @param  message the detail message (which is saved for later retrieval
   *         by the {@link #getMessage()} method).
   * @param  cause the cause (which is saved for later retrieval by the
   *         {@link #getCause()} method).  (A <tt>null</tt> value is
   *         permitted, and indicates that the cause is nonexistent or
   *         unknown.)
   * @since  1.4
   */
  public PersistenceException(String message, Throwable cause) {
    super(message, translateSQLException(cause));
  }

  /**
   * Constructs a new database runtime exception without a db connection with the specified cause and a
   * detail message of <tt>(cause==null ? null : cause.toString())</tt>
   * (which typically contains the class and detail message of
   * <tt>cause</tt>).  This constructor is useful for runtime exceptions
   * that are little more than wrappers for other throwables.
   *
   * @param  cause the cause (which is saved for later retrieval by the
   *         {@link #getCause()} method).  (A <tt>null</tt> value is
   *         permitted, and indicates that the cause is nonexistent or
   *         unknown.)
   */
  public PersistenceException(Throwable cause) {
    super(translateSQLException(cause));
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
   * Gets the persistent object.
   *
   * @return the object, null if exception is not related to an object
   */
  public Identifiable getIdentifiable() {
    return identifiable;
  }


  @Override
  public String getMessage() {
    if (lazyMessage == null) {
      StringBuilder buf = new StringBuilder();
      String msg = super.getMessage();
      if (msg == null) {
        msg = "";
      }
      boolean sqlEx = getCause() instanceof SQLException;
      boolean multiline = msg.length() > 80 || sqlEx || msg.contains("\n");
      buf.append(msg);

      // append optional extra identifiable and/or session info
      String identStr = null;
      if (identifiable != null) {
        identStr = identifiable.toGenericString();    // toString may fail!
        if (msg.contains(identStr)) {
          identStr = null;
        }
      }
      String sessionStr = null;
      if (session != null) {
        sessionStr = session.toString();
        if (msg.contains(sessionStr)) {
          sessionStr = null;
        }
      }
      if (identStr != null || sessionStr != null) {
        if (multiline) {
          buf.append('\n');
        }
        else {
          buf.append(' ');
        }
        buf.append(START_INFO);
        if (identStr != null) {
          buf.append(identStr);
        }
        if (sessionStr != null) {
          if (identStr != null) {
            buf.append(" -- ");
          }
          buf.append(sessionStr);
        }
        buf.append(END_INFO);
      }

      if (sqlEx) {
        SQLException sx = (SQLException) getCause();
        buf.append("\nSQL-Message: ").append(sx.getMessage())
           .append("\nSQL-Code:    ").append(sx.getErrorCode())
           .append("\nSQL-State:   ").append(sx.getSQLState());
      }
      lazyMessage = buf.toString();
    }
    return lazyMessage;
  }


  /**
   * Updates the pdo if not set so far.<br>
   * Used to add more info for exceptions thrown in a context where
   * the pdo isn't known.
   *
   * @param object the persistent object
   */
  public void updateDbObject(Identifiable object) {
    if (this.identifiable == null) {
      setIdentifiable(object);
    }
  }


  /**
   * Sets the pdo.
   * @param object the persistent object
   */
  protected void setIdentifiable(Identifiable object) {
    this.identifiable = object;
    if (object instanceof SessionProvider) {
      setSession(((SessionProvider) object).getSession());
    }
  }


  /**
   * Sets the db.<br>
   *
   * @param session the session
   */
  protected void setSession(Session session) {
    this.session = session;
  }


  /**
   * Replaces SQLExceptions probably not in the remote client's classpath.
   *
   * @param t the throwable
   * @return the filtered throwable
   */
  private static Throwable translateSQLException(Throwable t) {
    if (t instanceof SQLException) {
      StackTraceElement[] trace = t.getStackTrace();
      SQLException sx = (SQLException) t;
      // replace by java.sql.SQLException which definitely is in the classpath!
      t = new SQLException(sx.getMessage() + " (" + t.getClass().getName() + ")", sx.getSQLState(), sx.getErrorCode());
      t.setStackTrace(trace);
    }
    return t;
  }

}
