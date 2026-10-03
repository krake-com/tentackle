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
 * Tentackle maven plugin.
 * <p>
 * The {@code analyze} and {@code test-analyze} goals process the annotations annotated with
 * {@code @Analyze} and generate the {@code META-INF} service descriptors and the analyze info
 * for the wurblets. The other goals derive maven properties via converters ({@code properties}),
 * create a JavaBeans manifest ({@code beaninfo}) and list the versions of the 3rd-party dependencies
 * ({@code versions}) and maven plugins ({@code plugin-versions}) as pom properties.
 */
package org.tentackle.maven.plugin;
