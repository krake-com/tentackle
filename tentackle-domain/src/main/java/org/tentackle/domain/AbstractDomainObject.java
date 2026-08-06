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


package org.tentackle.domain;

import org.tentackle.pdo.DomainContext;
import org.tentackle.pdo.DomainException;
import org.tentackle.pdo.DomainObject;
import org.tentackle.pdo.PdoUtilities;
import org.tentackle.pdo.PersistenceDelegate;
import org.tentackle.pdo.PersistentDomainObject;
import org.tentackle.reflect.EffectiveClassProvider;
import org.tentackle.session.Session;
import org.tentackle.trip.TripReferencable;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * Base domain implementation of a PDO.
 *
 * @param <T> the PDO class
 * @param <D> the domain object class
 * @author harald
 */
@TripReferencable(TripReferencable.NEVER)  // only proxies are referenced, not implementations
public abstract class AbstractDomainObject<T extends PersistentDomainObject<T>, D extends AbstractDomainObject<T,D>>
       implements DomainObject<T>, EffectiveClassProvider<T>, Serializable {

  @Serial
  private static final long serialVersionUID = 1L;


  private T pdo;   // instance of the PDO this is a delegate for



  /**
   * Creates an application domain object.
   *
   * @param pdo the persistent domain object this is a delegate for
   */
  public AbstractDomainObject(T pdo) {
    this.pdo = pdo;
  }


  /**
   * Creates an application domain object.
   */
  public AbstractDomainObject() {
  }

  @Override
  public PersistenceDelegate<T> getPersistenceDelegate() {
    return pdo.getPersistenceDelegate();
  }


  @Override
  public T getPdo() {
    return pdo;
  }

  @Override
  public T me() {
    return pdo;
  }

  /**
   * Sets the PDO.<br>
   *
   * @param pdo the pdo
   */
  public void setPdo(T pdo) {
    this.pdo = pdo;
  }

  @Override
  public Class<T> getEffectiveClass() {
    return pdo.getEffectiveClass();
  }

  @Override
  public List<Class<? super T>> getEffectiveSuperClasses() {
    return pdo.getEffectiveSuperClasses();
  }

  @Override
  public DomainContext getDomainContext() {
    return pdo.getPersistenceDelegate().getDomainContext();
  }

  @Override
  public Session getSession() {
    return pdo.getPersistenceDelegate().getSession();
  }

  /**
   * {@inheritDoc}
   * <p>
   * The generic string is the diagnostic fallback used whenever {@link #toString()} may fail
   * (see {@code DomainException}, for example), so it must stay printable even for a delegate
   * that is not linked to its PDO yet &mdash; which is exactly the state it tends to be logged in.
   */
  @Override
  public String toGenericString() {
    T thisPdo = me();
    return  getClass().getName() +
            '[' +
            (thisPdo == null ? "unlinked"
                             : String.valueOf(thisPdo.getId()) + '/' + thisPdo.getSerial()) +
            ']';
  }


  /**
   * Gets the string representation of this domain object.
   * <p>
   * The default implementation returns the domain key if this is a root entity.
   * If a component, its single-name will be returned.
   * If evaluating the domain key failed, the generic string is returned
   * along with the exception in parentheses.
   * This is because {@code toString()} should never throw
   * an exception as it is used for logging and debugging.
   *
   * @return the string representation
   */
  @Override
  public String toString() {
    StringBuilder buf = new StringBuilder();
    try {
      Object key = me().getPersistenceDelegate().isRootEntity() ? getUniqueDomainKey() : getSingular();
      if (key != null) {
        buf.append(key);
      }
    }
    catch (RuntimeException re) {
      /*
       * The fallback dereferences the PDO once more, so it can fail for the very same reason the
       * attempt above did (an unlinked delegate, a missing persistence delegate). Guard it: this
       * method is used for logging and debugging, where throwing would mask the actual problem.
       */
      try {
        buf.append(toGenericString());
      }
      catch (RuntimeException ignored) {
        buf.append(getClass().getName());
      }
      buf.append("(");
      buf.append(re);
      buf.append(")");
    }
    return buf.toString();
  }


  @Override
  public boolean isUniqueDomainKeyProvided() {
    return false;
  }

  @Override
  public Class<?> getUniqueDomainKeyType() {
    assertRootEntity();
    throw new DomainException(pdo, "method getUniqueDomainKeyType not implemented in " + getClass());
  }

  @Override
  public Object getUniqueDomainKey() {
    assertRootEntity();
    throw new DomainException(pdo, "method getUniqueDomainKey not implemented in " + getClass());
  }

  @Override
  public void setUniqueDomainKey(Object domainKey) {
    assertRootEntity();
    throw new DomainException(pdo, "method setUniqueDomainKey not implemented in " + getClass());
  }

  @Override
  public T findByUniqueDomainKey(Object domainKey) {
    assertRootEntity();
    throw new DomainException(pdo, "method findByUniqueDomainKey not implemented in " + getClass());
  }

  @Override
  public String getSingular() {
    String name = PdoUtilities.getInstance().getSingular(me().getEffectiveClass());
    if (name == null) {
      name = me().getEffectiveClass().getSimpleName();
    }
    return name;
  }

  @Override
  public String getPlural() {
    String name = PdoUtilities.getInstance().getPlural(me().getEffectiveClass());
    if (name == null) {   // default to EN rule
      name = getSingular();
      name += name.endsWith("s") ? "es" : "s";
    }
    return name;
  }

  /**
   * Checks if the PDO is a root entity.
   */
  public void assertRootEntity() {
    if (!me().getPersistenceDelegate().isRootEntity()) {
      throw new DomainException(me(), "not a root-entity");
    }
  }

}
