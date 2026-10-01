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
 * The metadata of a live database schema.
 * <p>
 * A {@link org.tentackle.sql.metadata.ModelMetaData} holds the {@link org.tentackle.sql.metadata.TableMetaData}
 * of the tables in the model, each with its {@link org.tentackle.sql.metadata.ColumnMetaData},
 * {@link org.tentackle.sql.metadata.IndexMetaData} and {@link org.tentackle.sql.metadata.ForeignKeyMetaData}.
 * Backend-specific subclasses compensate for the peculiarities of the JDBC drivers.
 * The metadata is used by the migrator and the schema verifier of the SQL Maven plugin.
 * {@link org.tentackle.sql.metadata.MetaDataUtilities} provides some replaceable helper methods.
 */
package org.tentackle.sql.metadata;
