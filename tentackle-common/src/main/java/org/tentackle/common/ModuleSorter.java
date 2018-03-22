/*
 * Tentackle - a framework for java desktop applications
 * http://www.tentackle.org
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

import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * A sorter for jigsaw modules.
 *
 * @author harald
 */
public class ModuleSorter {

  private final Map<String, ModuleInfo> modules;

  /**
   * Creates a module sorter.
   */
  public ModuleSorter() {
    this.modules = new HashMap<>();
  }


  /**
   * Adds a jpms module.
   *
   * @param module the module
   */
  public void add(Module module) {
    ModuleInfo info = new ModuleInfo(module);
    modules.put(info.getName(), info);
  }

  @Override
  public String toString() {
    StringBuilder buf = new StringBuilder();
    for (ModuleInfo mod: getModules()) {
      buf.append(mod).append('\n');
    }
    return buf.toString();
  }

  /**
   * Gets the modules ordered by dependency.<br>
   * Independent modules come first, the highest dependency last.
   *
   * @return the module infos
   */
  public List<ModuleInfo> getModules() {
    List<ModuleInfo> list = new ArrayList<>();
    for (ModuleInfo module: modules.values()) {
      module.getAllRequiredModuleNames(modules);
      list.add(module);
    }
    Collections.sort(list);
    return list;
  }


  private static class IndexedModuleInfo {
    final ModuleInfo module;
    final int sequence;

    public IndexedModuleInfo(ModuleInfo module, int sequence) {
      this.module = module;
      this.sequence = sequence;
    }
  }

  private static class SortKey implements Comparable<SortKey> {
    final int sequence;
    final String name;
    final URL url;

    public SortKey(int sequence, URL url) {
      this.sequence = sequence;
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
      int rv = sequence - o.sequence;
      if (rv == 0) {
        rv = name.compareTo(o.name);
      }
      return rv;
    }
  }

  /**
   * Sorts an enumeration of urls according to the module hierarchy.<br>
   * The top of the hierarchy comes first.
   *
   * @param urls the urls, usually from ClassLoader.getResources
   * @return the sorted urls
   */
  public Collection<URL> sort(Enumeration<URL> urls) {
    List<ModuleInfo> infos = getModules();
    int sequence = infos.size();    
    Map<String, IndexedModuleInfo> moduleMap = new HashMap<>();   // module-name:indexed-info
    for (ModuleInfo module: infos) {
      moduleMap.put(module.getName(), new IndexedModuleInfo(module, sequence--));   // reverse order!
    }

    Set<SortKey> urlSet = new TreeSet<>();
    List<URL> unsortedUrls = new ArrayList<>();
    while (urls.hasMoreElements()) {
      URL url = urls.nextElement();
      if ("jrt".equals(url.getProtocol())) {
        String name = url.getPath();
        if (name.startsWith("/")) {
          name = name.substring(1);
        }
        int ndx = name.indexOf('/');
        if (ndx >= 0) {
          name = name.substring(0, ndx);
        }
        IndexedModuleInfo indexedInfo = moduleMap.get(name);
        if (indexedInfo != null) {
          urlSet.add(new SortKey(indexedInfo.sequence, url));
          continue;
        }
      }
      unsortedUrls.add(url);
    }

    List<URL> sortedUrls = urlSet.stream().map(SortKey::getUrl).collect(Collectors.toList());
    sortedUrls.addAll(unsortedUrls);
    return sortedUrls;
  }

}
