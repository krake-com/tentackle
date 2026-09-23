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
import org.apache.maven.project.MavenProject;

import org.tentackle.i18n.pdo.StoredBundle;
import org.tentackle.i18n.pdo.StoredBundleKey;
import org.tentackle.session.Session;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Base class for the "verify"- and "cleanup"-mojos.
 *
 * @author harald
 */
public class AbstractVerifyMojo extends AbstractI18nMojo {

  static class Translation implements Comparable<Translation> {
    final StoredBundle bundle;
    final String key;

    Translation(StoredBundle bundle, String key) {
      this.bundle = bundle;
      this.key = key;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }
      Translation that = (Translation) o;
      if (!bundle.equals(that.bundle)) {
        return false;
      }
      return key.equals(that.key);
    }

    @Override
    public int hashCode() {
      int result = bundle.hashCode();
      result = 31 * result + key.hashCode();
      return result;
    }

    @Override
    public int compareTo(Translation o) {
      int rv = bundle.getName().compareTo(o.bundle.getName());
      if (rv == 0) {
        rv = nullSafe(bundle.getLocale()).compareTo(nullSafe(o.bundle.getLocale()));
        if (rv == 0) {
          rv = key.compareTo(o.key);
        }
      }
      return rv;
    }
  }


  Set<StoredBundle> obsoleteBundles;                // obsolete bundles
  Set<StoredBundleKey> obsoleteKeys;                // obsolete bundle keys
  Set<Translation> missingTranslations;             // missing bundle keys
  Set<Translation> differingTranslations;           // differing translations
  Map<String, StoredBundle> resourceBundleMap;      // resource name -> stored bundle
  Map<String, Properties> propertiesMap;            // bundlename -> default properties for the null-locale


  @Override
  protected boolean validate() throws MojoExecutionException {
    if (super.validate()) {
      missingTranslations = new TreeSet<>();
      differingTranslations = new TreeSet<>();
      obsoleteKeys = new HashSet<>();
      resourceBundleMap = new TreeMap<>();
      propertiesMap = new HashMap<>();
      return true;
    }
    return false;
  }

  @Override
  protected void processBundles(Session session, List<MavenProject> projects) throws MojoExecutionException {
    obsoleteBundles = new HashSet<>(on(StoredBundle.class).selectAll());    // check all languages!

    super.processBundles(session, projects);

    // filter obsolete bundle names
    for (Iterator<StoredBundle> iterator=obsoleteBundles.iterator(); iterator.hasNext(); ) {
      StoredBundle bundle = iterator.next();
      Properties props = propertiesMap.get(bundle.getName());
      if (props != null) {
        iterator.remove();    // not obsolete, just for another language
        findObsoleteKeys(props, bundle);    // we're checking only for obsolete keys, not missing translations
      }
      else {
        // the whole bundle is obsolete: remove all obsolete keys for that bundle
        obsoleteKeys.removeIf(bundleKey -> bundleKey.getBundleId() == bundle.getId());
      }
    }
  }

  @Override
  public void processBundle(ClassLoader classLoader, String bundleName, String locale, String resourceName) {
    resourceBundleMap.computeIfAbsent(resourceName, resName -> {
      bundleCount++;
      StoredBundle bundle = null;
      try {
        Properties props = loadProperties(classLoader, resName);
        if (props != null) {
          bundle = verify(bundleName, locale, props);
        }
        else {
          getLogger().warn("no such properties file: {}", resName);
          warnings++;
        }
      }
      catch (IOException ex) {
        getLogger().error("could not load {}", resName, ex);
        errors++;
      }
      if (bundle == null) {
        bundle = on(StoredBundle.class);    // new if missing
      }
      return bundle;
    });
  }

  /**
   * Sorts stores bundles by name and locale.
   *
   * @param bundles the bundles
   * @return the copied sorted bundles
   */
  Collection<StoredBundle> sortBundles(Collection<StoredBundle> bundles) {
    List<StoredBundle> list = new ArrayList<>(bundles);
    list.sort((o1, o2) -> {
      int rv = o1.getName().compareTo(o2.getName());
      if (rv == 0) {
        rv = nullSafe(o1.getLocale()).compareTo(nullSafe(o2.getLocale()));
      }
      return rv;
    });
    return list;
  }

  /**
   * Sorts stored bundle keys by bundlename, locale and key.
   *
   * @param bundlesKeys the keys
   * @return the copied sorted keys
   */
  Collection<StoredBundleKey> sortBundleKeys(Collection<StoredBundleKey> bundlesKeys) {
    List<StoredBundleKey> list = new ArrayList<>(bundlesKeys);
    list.sort((o1, o2) -> {
      int rv = o1.getBundle().getName().compareTo(o2.getBundle().getName());
      if (rv == 0) {
        rv = nullSafe(o1.getBundle().getLocale()).compareTo(nullSafe(o2.getBundle().getLocale()));
        if (rv == 0) {
          rv = o1.getKey().compareTo(o2.getKey());
        }
      }
      return rv;
    });
    return list;
  }


  private StoredBundle verify(String bundleName, String locale, Properties props) {
    StoredBundle bundle = on(StoredBundle.class).findByNameAndLocale(bundleName, locale);
    if (bundle != null) {
      obsoleteBundles.remove(bundle);
      if (locale == null) {
        propertiesMap.put(bundle.getName(), props);
      }
      findDivergingTranslations(props, bundle);
      findObsoleteKeys(props, bundle);
    }
    return bundle;
  }

  private void findDivergingTranslations(Properties props, StoredBundle bundle) {
    for (String key : props.stringPropertyNames()) {
      String propValue = props.getProperty(key);
      String dbValue = bundle.getTranslation(key);
      if (dbValue == null) {
        missingTranslations.add(new Translation(bundle, key));
      }
      else if (!Objects.equals(propValue, dbValue)) {
        differingTranslations.add(new Translation(bundle, key));
      }
    }
  }

  private void findObsoleteKeys(Properties props, StoredBundle bundle) {
    for (StoredBundleKey bundleKey : bundle.getKeys()) {
      String propValue = props.getProperty(bundleKey.getKey());
      if (propValue == null) {
        obsoleteKeys.add(bundleKey);
      }
    }
  }

  private static String nullSafe(String s) {
    return s == null ? "" : s;
  }

}
