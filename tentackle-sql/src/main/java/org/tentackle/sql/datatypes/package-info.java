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
 * The predefined {@link org.tentackle.sql.DataType} implementations.
 * <p>
 * Covers the primitives and their wrappers, {@code String} and {@code Character}, the {@code java.time} and
 * legacy {@code java.sql} date and time types, {@code BigDecimal}, {@code UUID},
 * and the Tentackle types {@code BMoney}, {@code DMoney}, {@code I18NText}, {@code Binary} and {@code TBinary}.
 * The {@code String} type comes in the variants {@code LargeString} (CLOB), {@code Sqlxml} (XML) and {@code Json}.
 * Some types map to more than one column, such as the money types and the zoned or offset date and time types.
 * {@link org.tentackle.sql.datatypes.ConvertibleType} is a pseudo type for application-specific wrapper types
 * implementing {@code org.tentackle.misc.Convertible}.
 * <p>
 * Each type is registered as {@code @Service(DataType.class)} and located via the
 * {@link org.tentackle.sql.DataTypeFactory}. The abstract base classes help to implement application-specific types.
 */
package org.tentackle.sql.datatypes;
