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
 * The SQL backend layer.
 * <p>
 * Isolates the upper layers from the dialect, quirks and capabilities of the concrete database.
 * The central abstraction is the {@link org.tentackle.sql.Backend}, one per kind of database,
 * located via the {@link org.tentackle.sql.BackendFactory}. A {@link org.tentackle.sql.BackendInfo}
 * describes a concrete connection target. The {@link org.tentackle.sql.DataType}s map the Java types
 * of the model to one or more columns of a {@link org.tentackle.sql.SqlType}.
 * Furthermore, the package provides the {@link org.tentackle.sql.ScriptRunner} to execute SQL scripts
 * and the {@link org.tentackle.sql.DynamicDriver} to load JDBC drivers at runtime.
 */
package org.tentackle.sql;
