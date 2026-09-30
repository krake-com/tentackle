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

package org.tentackle.common;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * A dependency sorter for jigsaw modules.
 *
 * @author harald
 */
public final class ModuleSorter {

  /**
   * The singleton for the boot layer.
   */
  public static final ModuleSorter INSTANCE = new ModuleSorter();

  /**
   * Cache of per-layer sorters, see {@link #forLayer(ModuleLayer)}.
   */
  private static final Map<ModuleLayer, ModuleSorter> LAYER_SORTERS = new ConcurrentHashMap<>();


  private final Map<String, ModuleInfo> nameToModules;  // name:info
  private final Map<String, ModuleInfo> urlToModules;   // urlPath:info
  private final List<ModuleInfo> sortedModules;         // modules sorted by dependency
  private final Set<String> classpathFirstServices;     // services to override from classpath

  /**
   * Creates a module sorter and loads the module hierarchy of the boot layer.
   * <p>
   * Finds all named modules providing a {@link ModuleHook} via module-info and sorts them
   * according to their dependencies.
   */
  public ModuleSorter() {
    this(null);
  }

  /**
   * Creates a module sorter and loads the module hierarchy of a given layer.
   * <p>
   * Finds all named modules reachable from the given layer (i.e. its own modules plus all
   * ancestor layers) providing a {@link ModuleHook} via module-info and sorts them according
   * to their dependencies.
   *
   * @param layer the module layer, null for the boot layer
   */
  public ModuleSorter(ModuleLayer layer) {
    nameToModules = new HashMap<>();
    urlToModules = new HashMap<>();
    (layer == null ? ServiceLoader.load(ModuleHook.class) : ServiceLoader.load(layer, ModuleHook.class))
            .stream().map(ServiceLoader.Provider::get).forEach(hook -> {
      ModuleInfo info = new ModuleInfo(hook);
      nameToModules.put(info.getModule().getName(), info);
      urlToModules.put(info.getUrlPath(), info);
    });

    // depth-first sort (possible because jigsaw does not allow cyclic dependencies)
    sortedModules = new ArrayList<>();
    for (ModuleInfo info: nameToModules.values()) {
      if (info.getOrdinal() == 0) {
        visit(info, sortedModules);
      }
      // else: module already sorted
    }

    classpathFirstServices = new HashSet<>();
    // we can't use ServiceLoader because we're in modular mode, and we can't use ServiceFinder yet
    // because of chicken egg. Hence, we must do that really low level...
    try {
      Enumeration<URL> urls = getClass().getClassLoader().getResources(Constants.META_SERVICE_PATH + ClasspathFirst.class.getName());
      while (urls.hasMoreElements()) {
        URL url = urls.nextElement();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(FileHelper.openStreamUncached(url), StandardCharsets.UTF_8))) {
          String line;
          while ((line = reader.readLine()) != null) {
            classpathFirstServices.add(line);
          }
        }
      }
    }
    catch (IOException io) {
      throw new TentackleRuntimeException("can't determine classpathFirst services", io);
    }
  }

  @Override
  public String toString() {
    StringBuilder buf = new StringBuilder();
    for (ModuleInfo info: sortedModules) {
      buf.append(info).append('\n');
    }
    return buf.toString();
  }

  /**
   * Gets the modules-infos ordered by dependency.<br>
   * Modules at the top of the dependency hierarchy come last,
   * modules without further dependencies come first.
   *
   * @return the module infos
   */
  public List<ModuleInfo> getModuleInfos() {
    return sortedModules;
  }

  /**
   * Gets (and caches) the module sorter for a given module layer.
   *
   * @param layer the module layer, never null
   * @return the sorter for that layer
   */
  public static ModuleSorter forLayer(ModuleLayer layer) {
    Objects.requireNonNull(layer, "layer");
    return LAYER_SORTERS.computeIfAbsent(layer, ModuleSorter::new);
  }

  /**
   * Returns whether the application is running in modular mode.
   *
   * @return true if modular
   */
  public boolean isModular() {
    return !sortedModules.isEmpty();
  }

  /**
   * Gets the module info for a module name.
   *
   * @param name the module name
   * @return the module info, null if no such (application) module or module has no {@link ModuleHook}.
   */
  public ModuleInfo getModuleInfo(String name) {
    return nameToModules.get(name);
  }

  /**
   * Gets the module info for a given URL.
   *
   * @param url the resource url
   * @return the module info, null if application not modularized, not from a module or
   *         module does not provide a {@link ModuleHook}.
   */
  public ModuleInfo getModuleInfo(URL url) {
    ModuleInfo info = null;
    String path = url.getPath();
    switch(url.getProtocol()) {
      case "jrt":
        String name = url.getPath();
        if (name.startsWith("/")) {
          name = name.substring(1);
        }
        int ndx = name.indexOf('/');
        if (ndx >= 0) {
          name = name.substring(0, ndx);
        }
        info = nameToModules.get(name);
        break;

      case "jar":
        int lndx = path.lastIndexOf('!');
        if (lndx > 7) {
          String urlPath = path.substring(7, lndx);        // skip "file://"
          info = urlToModules.get(urlPath);
          break;
        }
        else {
          throw new TentackleRuntimeException("malformed jar url: " + url);
        }

      case "file":
        int fndx = path.indexOf("/META-INF/");
        if (fndx > 0) {
          String urlPath = path.substring(0, fndx + 1);
          info = urlToModules.get(urlPath);
          break;
        }
        else {
          throw new TentackleRuntimeException("malformed file url: " + url);
        }
    }
    return info;
  }

  /**
   * Sorts an enumeration of urls according to the module hierarchy.<br>
   * The highest module ordinal comes first.
   *
   * @param urls the urls, usually from ClassLoader.getResources
   * @param serviceName the service name
   * @return the sorted urls
   */
  public Collection<URL> sort(Enumeration<URL> urls, String serviceName) {
    Set<SortKey> urlSet = new TreeSet<>();
    List<URL> unsortedUrls = new ArrayList<>();

    while (urls.hasMoreElements()) {
      URL url = urls.nextElement();
      ModuleInfo info = getModuleInfo(url);
      if (info != null) {
        urlSet.add(new SortKey(info.getOrdinal(), url));
      }
      else {
        unsortedUrls.add(url);
      }
    }

    // URLs of named modules sorted by dependency
    List<URL> sortedUrls = urlSet.stream().map(SortKey::getUrl).collect(Collectors.toList());

    if (classpathFirstServices.contains(serviceName)) {
      // prepend URLs of the unnamed or automatic module
      unsortedUrls.addAll(sortedUrls);
      sortedUrls = unsortedUrls;
    }
    else {
      // append URLs of the unnamed or automatic module
      sortedUrls.addAll(unsortedUrls);
    }

    return sortedUrls;
  }


  /**
   * Depth-first search visitor.
   *
   * @param info the module info
   * @param sortedList the sorted list to add modules to
   */
  private void visit(ModuleInfo info, List<ModuleInfo> sortedList) {
    if (info.getOrdinal() == 0) {   // not sorted yet
      if (info.isVisited()) {
        // cannot happen in jigsaw but one never knows...
        throw new TentackleRuntimeException("unsortable module hierarchy (cyclic dependency?)");
      }
      info.setVisited(true);
      for (Iterator<String> iter = info.getRequiredModuleNames().iterator(); iter.hasNext(); ) {
        String name = iter.next();
        ModuleInfo subInfo = nameToModules.get(name);
        if (subInfo != null) {
          visit(subInfo, sortedList);
        }
        else {    // not an application module
          iter.remove();
        }
      }
      ModuleOrdinal moduleOrdinal = info.getModule().getAnnotation(ModuleOrdinal.class);
      if (moduleOrdinal != null) {
        info.setOrdinal(moduleOrdinal.value());
      }
      else {
        info.setOrdinal((sortedList.size() + 1) * 100);   // give room for insertions viw @ModuleOrdinal
      }
      sortedList.add(info);
    }
  }


  /**
   * Key to sort URLs according to the module dependency hierarchy.
   */
  private static class SortKey implements Comparable<SortKey> {

    final int ordinal;
    final String name;
    final URL url;

    public SortKey(int ordinal, URL url) {
      this.ordinal = ordinal;
      this.url = url;
      this.name = url.toExternalForm();
    }

    public URL getUrl() {
      return url;
    }

    @Override
    public int hashCode() {
      int hash = 7;
      hash = 31 * hash + Objects.hashCode(this.url);
      return hash;
    }

    @Override
    public boolean equals(Object obj) {
      if (this == obj) {
        return true;
      }
      if (obj == null) {
        return false;
      }
      if (getClass() != obj.getClass()) {
        return false;
      }
      final SortKey other = (SortKey) obj;
      return Objects.equals(this.url, other.url);
    }

    @Override
    public int compareTo(SortKey o) {
      int rv = Integer.compare(o.ordinal, ordinal);   // the higher sequence comes first
      if (rv == 0) {
        rv = name.compareTo(o.name);
      }
      return rv;
    }
  }

}
