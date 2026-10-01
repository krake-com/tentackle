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
 * The {@link org.tentackle.sql.Backend} implementations.
 * <p>
 * The backends are organized by the level of the SQL standard they implement:
 * {@link org.tentackle.sql.backends.AbstractBackend} is extended by
 * {@link org.tentackle.sql.backends.AbstractSql92Backend}, which is extended by
 * {@link org.tentackle.sql.backends.AbstractSql2003Backend} and
 * {@link org.tentackle.sql.backends.AbstractSql2008Backend}.
 * Each concrete backend extends the closest level and overrides only its peculiarities.
 * The backends are registered as {@code @Service(Backend.class)} and located via the
 * {@link org.tentackle.sql.BackendFactory}.
 */
package org.tentackle.sql.backends;
