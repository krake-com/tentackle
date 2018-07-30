/*
 * Tentackle - http://www.tentackle.org
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
package org.tentackle.maven.wizard;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;

import java.util.List;
import java.util.Map;

/**
 * Wizard to create operation files.
 */
@Mojo(name = "operation", inheritByDefault = false, aggregator = true)
public class OperationMojo extends AbstractWizardMojo {

  private List<OperationProfile> operationProfiles;

  @Override
  public void execute() throws MojoExecutionException, MojoFailureException {
    super.execute();
  }

  @Override
  protected void validate() throws MojoExecutionException {
    super.validate();
    operationProfiles = getProfiles(OperationProfile.class);
    if (operationProfiles.isEmpty()) {
      throw new MojoExecutionException("at least one OperationProfile must be configured");
    }
    Map<String, PackageInfo> packageInfoMap = createPackageMap();
    for (OperationProfile profile: operationProfiles) {
      profile.validate(packageInfoMap);
    }
  }
}
