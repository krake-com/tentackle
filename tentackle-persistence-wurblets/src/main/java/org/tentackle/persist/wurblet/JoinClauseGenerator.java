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

package org.tentackle.persist.wurblet;

import org.wurbelizer.wurbel.WurbelException;

import org.tentackle.model.Attribute;
import org.tentackle.sql.Backend;
import org.tentackle.wurblet.CodeGenerator;
import org.tentackle.wurblet.Join;
import org.tentackle.wurblet.WurbletArgument;
import org.tentackle.wurblet.WurbletArgumentExpression;
import org.tentackle.wurblet.WurbletArgumentOperator;

/**
 * Generates the SQL code for load joins with filters.
 */
public class JoinClauseGenerator implements CodeGenerator<Object> {

  private final DbModelWurblet wurblet;
  private final Join join;

  /**
   * Creates the generator.
   *
   * @param wurblet the model wurblet
   * @param join the load join with filter
   */
  public JoinClauseGenerator(DbModelWurblet wurblet, Join join) {
    this.wurblet = wurblet;
    this.join = join;
  }

  public String generate() throws WurbelException {
    return Backend.SQL_AND + generate(join.getWurbletRelation().getParser().getExpression());
  }

  @Override
  public String generate(Object t) throws WurbelException {

    if (t instanceof WurbletArgumentOperator operator) {
      return ' ' + operator.getText() + ' ';
    }

    if (t instanceof WurbletArgumentExpression expression) {
      return "(" + expression.toCode(this) + ")";
    }

    if (t instanceof WurbletArgument argument) {
      StringBuilder buf = new StringBuilder();
      Attribute attribute = argument.getAttribute();

      for (int columnIndex = 0; columnIndex < argument.getDataType().getColumnCount(null); columnIndex++) {
        if (columnIndex > 0 && argument.getColumnIndex() == null) {
          buf.append(Backend.SQL_AND);
        }
        if (argument.getColumnIndex() == null || columnIndex == argument.getColumnIndex()) {
          buf.append(join.getName()).append('.').append(wurblet.getColumnName(attribute, columnIndex))
             .append(wurblet.createRelopSql(argument));
        }
      }
      return buf.toString();
    }

    return " " + t + " ";
  }

}
