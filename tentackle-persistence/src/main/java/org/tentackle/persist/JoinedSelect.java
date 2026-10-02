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
 *
 */

package org.tentackle.persist;

import org.tentackle.pdo.Pdo;
import org.tentackle.pdo.PersistentDomainObject;
import org.tentackle.session.PersistenceException;
import org.tentackle.sql.Backend;
import org.tentackle.sql.JoinType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A joined {@code SELECT}.
 * <p>
 * Holds the {@link Join}s of a query along with the state of the running query,
 * i.e., the PDOs retrieved so far. Hence, a joined select is created per query and
 * must not be shared between threads.
 *
 * @author harald
 * @param <T> the primary PDO type
 */
public class JoinedSelect<T extends PersistentDomainObject<T>> {

  /** the joins. */
  private final List<Join<? super T,?>> joins;

  /** the PDOs joined so far (the joins themselves are not modified and are shared). */
  private final JoinState joinState;

  /** the currently retrieved PDO. */
  private T pdo;

  /** the PDO retrieved before the current PDO. */
  private T lastPdo;

  /** the retrieved list of PDOs. */
  private List<T> list;

  /** map of ID to PDOs. */
  private Map<Long,T> idMap;


  /**
   * Creates a joined {@code SELECT}.
   */
  public JoinedSelect() {
    this(new ArrayList<>());
  }

  /**
   * Creates a joined {@code SELECT} from a list of joins.
   *
   * @param joins the joins
   */
  public JoinedSelect(List<Join<? super T,?>> joins) {
    this.joins = joins;
    this.joinState = new JoinState();
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
   * Gets the state of the joins for this select.
   *
   * @return the join state, never null
   */
  public JoinState getJoinState() {
    return joinState;
  }

  /**
   * Appends the joins to the SQL code of the select.
   *
   * @param pdo the PDO proxy
   * @param selectSql the select builder without joins, appended with the joins on return
   * @throws PersistenceException if a join type is neither INNER nor LEFT
   */
  public void createJoinedSql(T pdo, StringBuilder selectSql) {
    AbstractPersistentObject<T,?> pdoPD = (AbstractPersistentObject<T,?>) pdo.getPersistenceDelegate();
    Backend backend = pdoPD.getBackend();
    for (Join<? super T,?> join: joins) {
      createJoinedSql(pdo, backend, join, selectSql);
    }
  }

  /**
   * Gets the PDO retrieved before the current PDO.
   *
   * @return the last PDO, null if none
   */
  public T getLastPdo() {
    return lastPdo;
  }

  /**
   * Gets the currently retrieved PDO.
   *
   * @return the PDO, null if no query running
   */
  public T currentPdo() {
    return pdo;
  }

  /**
   * Finishes the current PDO and switches to the next.<br>
   * When selecting into a list, a PDO already in the list is reused.
   *
   * @param pdo the next PDO
   * @return the next PDO, or the PDO already in the list with the same ID
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
   * Initializes the joined select.<br>
   * Must be invoked before each query.
   *
   * @param list the list to fill when selecting into a list, null for cursors and single PDOs
   * @throws PersistenceException if the list is not empty
   */
  public void initialize(List<T> list) {
    if (list != null && !list.isEmpty()) {
      throw new PersistenceException("list must be empty");
    }
    this.list = list;

    pdo = null;
    lastPdo = null;
    idMap = new HashMap<>();
    joinState.clear();
  }


  private void createJoinedSql(PersistentDomainObject<?> pdo, Backend backend, Join<?,?> join, StringBuilder selectSql) {
    if (join.getType() != JoinType.INNER && join.getType() != JoinType.LEFT) {
      throw new PersistenceException(pdo, "unsupported JoinType: " + join.getType().name());
    }
    PersistentDomainObject<?> joinedPdo = Pdo.create(join.getJoinedClass(), pdo.getPersistenceDelegate().getDomainContext());
    AbstractPersistentObject<?,?> joinPD = (AbstractPersistentObject<?,?>) joinedPdo.getPersistenceDelegate();
    String columnName = join.getColumnName();
    String joinedColumnName = join.getJoinedColumnName();
    String joinSelectIdAlias = null;
    String selectJoinedSql = joinPD.createSelectAllSql(backend);
    if (join.isExplicitIdAliasRequired()) {
      // replace <alias>.id by <alias>_id
      joinSelectIdAlias = joinPD.getTableAlias() + ".id AS " + joinPD.getTableAlias() + "_id";
      joinedColumnName = join.getJoinAlias() + "." + joinPD.getTableAlias() + "_id";
    }
    String joinSql = columnName + "=" + joinedColumnName;
    if (join.getExtraSql() != null) {
      joinSql += join.getExtraSql();
    }
    backend.sqlJoinSelects(join.getType(), true, selectSql,
                           selectJoinedSql, joinSelectIdAlias, join.getJoinAlias(),
                           joinSql);

    for (Join<?,?> subJoin: join.getJoins()) {
      createJoinedSql(joinedPdo, backend, subJoin, selectSql);
    }
  }

}
