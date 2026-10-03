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
 * Tentackle SQL maven plugin.<br>
 * Turns the Tentackle model into backend-specific DDL ({@code create}), migration scripts
 * ({@code migrate}, {@code validate}) and cross-linked documentation ({@code document}),
 * runs SQL scripts ({@code run}), and reverse-engineers existing tables into model source ({@code reveng}).
 */
package org.tentackle.maven.plugin.sql;
