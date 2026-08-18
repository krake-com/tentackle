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

/**
 * A type-safe DSL for SQL projection queries.
 * <p>
 * Projection queries deliver plain data, not PDOs. They are executed on the low level
 * of the database layer, i.e. without a security manager, and their results are mapped
 * to DTOs by {@link org.tentackle.dbms.ResultSetWrapper#toList(java.lang.Class)}.
 * <p>
 * The DSL builds the SQL-code and the parameter set of a {@link org.tentackle.dbms.Query}
 * from typed expressions instead of hand-written SQL-strings. It knows about the
 * {@link org.tentackle.sql.DataType}s and thus about the columns a value is mapped to,
 * and it lets the {@link org.tentackle.sql.Backend} generate everything that is dialect-specific.
 *
 * @see org.tentackle.dbms.dsl.DSL
 */
package org.tentackle.dbms.dsl;
