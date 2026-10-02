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

import org.tentackle.common.Constants;
import org.tentackle.pdo.Pdo;
import org.tentackle.pdo.PdoFactory;
import org.tentackle.pdo.PersistentDomainObject;
import org.tentackle.sql.JoinType;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

/**
 * PDO join.
 * <p>
 * Joins two PDOs within the same select.<br>
 * Used by eager joins and explicit domain-driven joins.<br>
 * Works for composite and non-composite relations.
 * <p>
 * A join is a description of <em>how</em> to join, not of a running query, and is not modified
 * once set up: the eager joins of an entity are created once in the static class variables of its
 * persistence class and are therefore shared by all threads. The PDOs joined while a result set is read are consequently
 * kept in the per-query {@link JoinState} owned by the {@link JoinedSelect}, never in the join
 * itself.
 *
 * @author harald
 * @param <T> the PDO type holding the relation to the joined PDO
 * @param <J> the joined PDO type
 */
public class Join<T extends PersistentDomainObject<T>, J extends PersistentDomainObject<J>> {

  private static final String ENDS_WITH_ID = "." + Constants.CN_ID;

  /**
   * The join type.<br>
   * Only INNER and LEFT are currently supported.
   */
  private final JoinType type;

  /**
   * The column name of the table.
   * For object joins this is the alias of the table followed by a dot and a column name ending with "id".
   * For list joins this is the alias of the table followed by a dot and the ID column name.
   */
  private final String columnName;

  /**
   * The column name of the joined table.
   * For object joins this is the alias of the joined table followed by a dot and the ID column name.
   * For list joins this is the alias of the joined table followed by a dot and a column name ending with "id".
   */
  private final String joinedColumnName;

  /**
   * The joined PDO class.
   */
  private final Class<J> joinedClass;

  /**
   * The alias of the joined table.
   */
  private final String joinAlias;

  /**
   * The joiner implementing the join for the model.
   */
  private final BiConsumer<T,J> joiner;

  /**
   * Cascaded joins.
   */
  private final List<Join<J,?>> joins;

  /**
   * True if this is a list join.
   */
  private final boolean listJoin;

  /**
   * True if the joined table gets an extra ID added due to multiple IDs within its select.
   */
  private final boolean explicitIdAliasRequired;

  /**
   * Optional SQL code.
   */
  private final String extraSql;


  /**
   * Creates a join.
   *
   * @param type the join type
   * @param columnName the column name of the PDO (including table alias!)
   * @param joinedColumnName the column name of the joined PDO (including table alias!)
   * @param joinedClass the joined class
   * @param joinAlias the alias of the joined PDO
   * @param extraSql optional SQL code (usually a WHERE clause)
   * @param joiner the joiner
   */
  @SuppressWarnings("rawtypes")
  public Join(JoinType type, String columnName, String joinedColumnName, Class<J> joinedClass,
              String joinAlias, String extraSql, BiConsumer<T, J> joiner) {

    this.type = type;
    this.columnName = columnName;
    this.joinedColumnName = joinedColumnName;
    this.joinedClass = joinedClass;
    this.joinAlias = joinAlias;
    this.joiner = joiner;
    this.extraSql = extraSql;

    joins = new ArrayList<>();
    listJoin = columnName.endsWith(ENDS_WITH_ID);
    explicitIdAliasRequired = !listJoin &&
                              ((AbstractPersistentObject) PdoFactory.getInstance().createPersistenceDelegate(joinedClass.getName())).isExplicitIdAliasRequiredInJoins();
  }

  /**
   * Creates a join.
   *
   * @param type the join type
   * @param columnName the column name of the PDO (including table alias!)
   * @param joinedColumnName the column name of the joined PDO (including table alias!)
   * @param joinedClass the joined class
   * @param joinAlias the alias of the joined PDO
   * @param joiner the joiner
   */
  public Join(JoinType type, String columnName, String joinedColumnName, Class<J> joinedClass,
              String joinAlias, BiConsumer<T, J> joiner) {
    this(type, columnName, joinedColumnName, joinedClass, joinAlias, null, joiner);
  }


  /**
   * Gets the join type.
   *
   * @return the type
   */
  public JoinType getType() {
    return type;
  }

  /**
   * Returns whether joined to a list or an object.
   *
   * @return true if to list, false if to object
   */
  public boolean isListJoin() {
    return listJoin;
  }

  /**
   * Returns whether an extra ID alias must be added to the joined select statement.
   * This is true for multi-table inherited object relations.
   *
   * @return true if ID alias necessary
   */
  public boolean isExplicitIdAliasRequired() {
    return explicitIdAliasRequired;
  }

  /**
   * Gets the column name of the PDO.
   *
   * @return the column name including the table alias
   */
  public String getColumnName() {
    return columnName;
  }

  /**
   * Gets the joined column name.
   *
   * @return the column name including the table alias: the ID column for object joins,
   *         the column referencing the PDO for list joins
   */
  public String getJoinedColumnName() {
    return joinedColumnName;
  }

  /**
   * Gets the joined PDO class.
   *
   * @return the class
   */
  public Class<J> getJoinedClass() {
    return joinedClass;
  }

  /**
   * Gets the alias of the joined PDO.
   *
   * @return the alias
   */
  public String getJoinAlias() {
    return joinAlias;
  }

  /**
   * Gets the joiner.
   *
   * @return the joiner
   */
  public BiConsumer<T, J> getJoiner() {
    return joiner;
  }

  /**
   * Gets optional SQL code.
   *
   * @return SQL code, null or empty if none
   */
  public String getExtraSql() {
    return extraSql;
  }

  /**
   * Adds a cascaded join.
   *
   * @param join the cascaded join
   * @return me (to allow fluent style setup)
   */
  public Join<T,J> addJoin(Join<J,?> join) {
    joins.add(join);
    return this;
  }

  /**
   * Gets the cascaded joins.
   *
   * @return the cascaded joins, never null
   */
  public List<Join<J,?>> getJoins() {
    return joins;
  }

  /**
   * Creates a new PDO to join to the given PDO.<br>
   * The new PDO is created in the domain context of the given PDO.
   *
   * @param pdo the PDO to join to
   * @return the PDO to join
   */
  public J createJoinedPdo(T pdo) {
    return Pdo.create(joinedClass, pdo.getPersistenceDelegate().getDomainContext());
  }

  /**
   * Joins a PDO with another PDO.<br>
   * The PDOs joined so far are tracked in the given per-query state; hence the same join
   * can be used by concurrently running selects. If a PDO with the same ID has already been joined,
   * the joiner is not invoked again.
   *
   * @param joinState the state of the running joined select
   * @param pdo the PDO that gets the join
   * @param pdoToJoin the PDO to join (must not be null)
   * @return the new or already joined PDO
   */
  public J join(JoinState joinState, T pdo, J pdoToJoin) {
    Map<Long, J> lastJoined = joinState.getJoinedPdos(this, pdo);
    J joinedPdo = lastJoined.putIfAbsent(pdoToJoin.getId(), pdoToJoin);
    if (joinedPdo == null) {
      // if not already added
      joiner.accept(pdo, pdoToJoin);
      joinedPdo = pdoToJoin;
    }
    return joinedPdo;
  }

}
