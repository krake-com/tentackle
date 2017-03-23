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

package org.tentackle.persist;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.tentackle.dbms.PreparedStatementWrapper;
import org.tentackle.pdo.Pdo;
import org.tentackle.pdo.PersistentDomainObject;
import org.tentackle.session.PersistenceException;
import org.tentackle.sql.Backend;
import org.tentackle.sql.JoinType;

/**
 * A joined select.
 *
 * @author harald
 * @param <T> the primary PDO type
 */
public class JoinedSelect<T extends PersistentDomainObject<T>> {

  /** the joins. */
  private final List<Join<? super T,?>> joins;

  /** the currently retrieved PDO. */
  private T pdo;

  /** the PDO retrieved before the current PDO. */
  private T lastPdo;

  /** retrieved list of PDOs. */
  private List<T> list;

  /** map of ID to PDOs. */
  private Map<Long,T> idMap;


  /**
   * Creates a joined select.
   */
  public JoinedSelect() {
    this.joins = new ArrayList<>();
  }

  /**
   * Creates a joined select from a list of joins.
   *
   * @param joins the joins
   */
  public JoinedSelect(List<Join<? super T,?>> joins) {
    this.joins = joins;
  }


  /**
   * Adds a join.
   *
   * @param join the join
   * @return me (to allow fluent style setup)
   */
  public JoinedSelect<T> addJoin(Join<? super T,?> join) {
    joins.add(join);
    return this;
  }

  /**
   * Gets the joins.
   *
   * @return the joins
   */
  public List<Join<? super T,?>> getJoins() {
    return joins;
  }


  /**
   * Creates the SQL-code that does the joined select.
   *
   * @param pdo the pdo proxy
   * @param selectSql the unjoined select builder
   */
  public void createJoinedSql(T pdo, StringBuilder selectSql) {
    AbstractPersistentObject<T,?> pdoPD = (AbstractPersistentObject<T,?>) pdo.getPersistenceDelegate();
    Backend backend = pdoPD.getBackend();
    for (Join<? super T,?> join: joins) {
      if (join.getType() != JoinType.INNER && join.getType() != JoinType.LEFT) {
        throw new PersistenceException(pdo, "unsupported JoinType: " + join.getType().name());
      }
      PersistentDomainObject<?> joinedPdo = Pdo.create(join.getJoinedClass(), pdo.getDomainContext());
      AbstractPersistentObject<?,?> joinPD = (AbstractPersistentObject<?,?>) joinedPdo.getPersistenceDelegate();
      String columnName = join.getColumnName();
      String joinedColumnName = join.getJoinedColumnName();
      String joinSelectIdAlias = null;
      String selectJoinedSql = joinPD.createSelectAllSql();
      if (join.isExplicitIdAliasRequired()) {
        // replace <alias>.id by <alias>_id
        joinSelectIdAlias = joinPD.getTableAlias() + ".id AS " + joinPD.getTableAlias() + "_id";
        joinedColumnName = join.getJoinAlias() + "." + joinPD.getTableAlias() + "_id";
      }
      backend.sqlJoinSelects(join.getType(), true, selectSql,
                             selectJoinedSql, joinSelectIdAlias, join.getJoinAlias(),
                             columnName + "=" + joinedColumnName);
    }
  }


  /**
   * Sets the class-ID parameters if required for the joins.
   *
   * @param st the statement
   * @param ndx the next parameter ndx
   * @return the next parameter index
   */
  public int setClassIdsInStatement(PreparedStatementWrapper st, int ndx) {
    for (Join<? super T,?> join: joins) {
      PersistentDomainObject<?> joinedPdo = Pdo.create(join.getJoinedClass());
      AbstractPersistentObject joinPD = (AbstractPersistentObject) joinedPdo.getPersistenceDelegate();
      if (joinPD.isClassIdRequiredInWhereClause()) {
        st.setInt(ndx++, joinPD.getClassId());
      }
    }
    return ndx;
  }


  /**
   * Gets the PDO retrieved before the current PDO.
   *
   * @return the last pdo
   */
  public T getLastPdo() {
    return lastPdo;
  }

  /**
   * Gets the currentl retrieved PDO.
   *
   * @return the pdo, null if no query running
   */
  public T currentPdo() {
    return pdo;
  }

  /**
   * Finishes the current pdo and switched to the next.
   *
   * @param pdo the next pdo
   * @return the pdo possibly changed if already in list
   */
  public T nextPdo(T pdo) {
    lastPdo = this.pdo;
    this.pdo = pdo;
    if (list != null && pdo != null) {
      Long pdoId = pdo.getPersistenceDelegate().getId();
      T knownPdo = idMap.get(pdoId);
      if (knownPdo != null) {
        this.pdo = knownPdo;
      }
      else {
        list.add(pdo);
        idMap.put(pdoId, pdo);
      }
    }
    return this.pdo;
  }

  /**
   * Initializes the joined select.
   *
   * @param list the list if select to list
   */
  public void initialize(List<T> list) {
    if (list != null && !list.isEmpty()) {
      throw new PersistenceException("list must be empty");
    }
    this.list = list;

    pdo = null;
    lastPdo = null;
    idMap = new HashMap<>();
    for (Join<? super T,?> join: joins) {
      join.initialize();
    }
  }

}
