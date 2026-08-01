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

package org.tentackle.dbms.dsl;

import org.tentackle.dbms.SqlSupplier;

import java.util.List;
import java.util.Objects;

/**
 * A condition given as plain SQL-code.
 * <p>
 * The escape hatch for conditions the DSL does not cover.
 * The application is responsible for providing one parameter per question mark,
 * according to the number of columns of the parameter's datatype.
 *
 * @author harald
 */
public class RawCondition implements Condition {

  private final SqlSupplier sqlSupplier;      // the SQL-code
  private final List<Object> parameters;      // the parameters, one per question mark


  /**
   * Creates a condition from backend-specific SQL-code.
   *
   * @param sqlSupplier the supplier for the SQL-code
   * @param parameters the parameters, in the order of the question marks
   */
  public RawCondition(SqlSupplier sqlSupplier, Object... parameters) {
    this.sqlSupplier = Objects.requireNonNull(sqlSupplier, "SQL-supplier must be provided");
    this.parameters = List.of(parameters);
  }

  /**
   * Creates a condition from SQL-code.
   *
   * @param sql the SQL-code
   * @param parameters the parameters, in the order of the question marks
   */
  public RawCondition(CharSequence sql, Object... parameters) {
    this(backend -> sql, parameters);
  }


  @Override
  public void render(RenderContext context) {
    context.append(sqlSupplier.get(context.getBackend()));
    for (Object parameter : parameters) {
      context.addParameter(parameter);
    }
  }

}
