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

import org.tentackle.model.Entity;
import org.tentackle.model.Model;
import org.tentackle.model.ModelException;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Wizard to create PDO files.
 */
@Mojo(name = "pdo", inheritByDefault = false, aggregator = true)
public class PdoMojo extends AbstractWizardMojo {

  private List<PdoProfile> pdoProfiles;

  @Override
  public void execute() throws MojoExecutionException, MojoFailureException {
    super.execute();
    updateClassIDs();
  }

  @Override
  protected void validate() throws MojoExecutionException {
    super.validate();
    pdoProfiles = getProfiles(PdoProfile.class);
    if (pdoProfiles.isEmpty()) {
      throw new MojoExecutionException("at least one PdoProfile must be configured");
    }
    Map<String, PackageInfo> packageInfoMap = createPackageMap();
    for (PdoProfile profile: pdoProfiles) {
      profile.validate(packageInfoMap);
    }
  }

  private void updateClassIDs() throws MojoExecutionException {

    // profiles sorted by classid, highest first
    Set<PdoProfile> sortedProfiles = new TreeSet<>(new Comparator<PdoProfile>() {
      @Override
      public int compare(PdoProfile o1, PdoProfile o2) {
        return o2.getClassId() - o1.getClassId();
      }
    });

    // map of profiles to next free classid
    Map<PdoProfile, Integer> inUseIds = new HashMap<>();

    for (PdoProfile profile: pdoProfiles) {
      sortedProfiles.add(profile);
      inUseIds.put(profile, profile.getClassId());
    }

    try {
      for (Entity entity: Model.getInstance().getAllEntitites()) {
        int classId = entity.getClassId();
        if (classId > 0) {
          classId++;    // next free
          for (PdoProfile profile: sortedProfiles) {
            if (classId >= profile.getClassId()) {
              if (inUseIds.get(profile) < classId) {    // must exist...
                inUseIds.put(profile, classId);
              }
              break;
            }
          }
        }
      }
    }
    catch (ModelException e) {
      throw new MojoExecutionException("cannot determine class IDs", e);
    }

    for (Map.Entry<PdoProfile,Integer> entry: inUseIds.entrySet()) {
      PdoProfile profile = entry.getKey();
      int classId = entry.getValue();
      profile.setClassId(classId);
      getLog().info("next free class-ID for " + profile.getName() + " is " + classId);
    }
  }
}
