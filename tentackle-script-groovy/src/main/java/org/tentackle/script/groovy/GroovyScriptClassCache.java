/*
 * Tentackle - http://www.tentackle.org.
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

package org.tentackle.script.groovy;

import groovy.lang.Script;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Caches the compiled classes of Groovy scripts.
 *
 * @author harald
 */
public class GroovyScriptClassCache {

  private final Map<String, Class<Script>> scriptClassMap;  // the compiled classes (not serialized!)
  private final String name;                                // cache name


  /**
   * Creates a script class cache.
   *
   * @param name the name of cache
   */
  public GroovyScriptClassCache(String name) {
    if (name == null) {
      throw new NullPointerException("name must not be null");
    }
    this.name = name;
    scriptClassMap = new ConcurrentHashMap<>();
  }


  /**
   * Creates a script class cache with the name {@code "default"}.
   */
  public GroovyScriptClassCache() {
    this ("default");
  }


  /**
   * Gets the name of the cache.
   *
   * @return the name
   */
  public String getName() {
    return name;
  }


  /**
   * Gets a script class.
   *
   * @param code the script source code
   * @return the script class, null if not in cache
   */
  public Class<Script> getScriptClass(String code) {
    return scriptClassMap.get(code);
  }


  /**
   * Puts a script class into the cache.
   *
   * @param code the script source code
   * @param scriptClass the script class
   */
  public void putScriptClass(String code, Class<Script> scriptClass) {
    scriptClassMap.put(code, scriptClass);
  }

}
