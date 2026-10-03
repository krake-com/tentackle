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

package org.tentackle.maven.plugin.sql;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Mojo;

/**
 * Validates the database against the model.<br>
 * Same as the {@code migrate} goal, but the build fails if anything needs to be migrated.<br>
 * Alternative to the {@code validate} configuration option of the {@code migrate} goal.
 */
@Mojo(name = "validate", aggregator = true, threadSafe = true)
public class ValidateSqlMojo extends MigrateSqlMojo {

  @Override
  protected boolean validate() throws MojoExecutionException {
    enableValidation();
    return super.validate();
  }
}
