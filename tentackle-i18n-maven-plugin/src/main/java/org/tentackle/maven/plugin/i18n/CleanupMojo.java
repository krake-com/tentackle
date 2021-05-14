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

package org.tentackle.maven.plugin.i18n;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;

import org.tentackle.i18n.pdo.StoredBundle;
import org.tentackle.i18n.pdo.StoredBundleKey;
import org.tentackle.session.Session;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Removes obsolete bundles and keys.<br>
 * Does nothing if there are errors or warnings to make sure that there are no pending refactorings to be resolved
 * by the <code>BundleMonkey</code> tool.
 * Consider running <code>verify</code> first to check for such warnings.
 */
@Mojo(name = "cleanup", aggregator = true, inheritByDefault = false,
      requiresDependencyResolution = ResolutionScope.COMPILE)
public class CleanupMojo extends AbstractVerifyMojo {

  @Override
  protected void processBundles(Session session, List<MavenProject> projects) throws MojoExecutionException {
    super.processBundles(session, projects);

    warnings += resourceBundleMap.entrySet().stream().filter(entry -> entry.getValue().isNew()).map(entry -> entry.getKey())
                                 .collect(Collectors.toList()).size();
    warnings += missingTranslations.stream().filter(translation -> translation.bundle.getLocale() != null)
                                   .collect(Collectors.toList()).size();
    errors += missingTranslations.stream().filter(translation -> translation.bundle.getLocale() == null)
                                 .collect(Collectors.toList()).size();

    if (errors > 0) {
      getLog().error(errors + " error" + (errors > 1 ? "s" : "") + " found -> please run tentackle-i18n:verify and fix the errors");
    }
    else if (warnings > 0) {
      getLog().warn(warnings + " warning" + (warnings > 1 ? "s" : "") + " found -> please run tentackle-i18n:verify and fix the warnings");
    }
    else {
      updates += session.transaction(() -> {
        int count = 0;
        StringBuilder buf = new StringBuilder();

        Set<StoredBundle> bundles = new HashSet<>();    // bundles to update
        for (StoredBundleKey bundleKey : sortBundleKeys(obsoleteKeys)) {
          StoredBundle bundle = bundleKey.getBundle();
          buf.append("\ntranslation ").append(bundle).append(" '").append(bundleKey.getKey());
          bundle.setTranslation(bundleKey.getKey(), null);
          bundles.add(bundle);
        }
        bundles.forEach(bundle -> bundle.save());
        count += obsoleteKeys.size();

        for (StoredBundle bundle : sortBundles(obsoleteBundles)) {
          buf.append("\nbundle ").append(bundle).append(" with translations:");
          for (StoredBundleKey key : bundle.getKeys()) {
            buf.append("\n    '").append(key.getKey()).append("'");
            count++;
          }
          bundle.delete();
        }
        count += obsoleteBundles.size();

        if (count > 0) {
          getLog().warn(count + " objects removed from " + url + ":" + buf);
        }
        return count;
      });
    }
  }

}
