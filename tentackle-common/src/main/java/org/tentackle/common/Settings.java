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

package org.tentackle.common;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

/**
 * Global settings.
 *
 * @author harald
 */
public class Settings {

  /**
   * The encoding used to read and write files.<br>
   * Defaults to "UTF-8".
   * <p>
   * Usually set by the maven plugins from "project.build.sourceEncoding".
   */
  public static Charset encodingCharset = StandardCharsets.UTF_8;

  /**
   * The service factory class name.
   * <p>
   * If this value is null, the {@link ServiceFactory} is loaded via the {@link java.util.ServiceLoader}.
   * However, if for whatever reason, the loader doesn't find the service configuration or cannot
   * be used, the class defined by {@link #SERVICE_FACTORY_CLASSNAME} can be instantiated.
   * Applications must change this at a very early stage of startup, for example in the main-method.
   */
  public static String SERVICE_FACTORY_CLASSNAME;

  /**
   * The classloader to load the service factorty.
   * <p>
   * If this value is null, the the {@link ServiceFactory} is loaded via a default classloader.
   */
  public static ClassLoader SERVICE_FACTORY_CLASSLOADER;



  private Settings() {
  }

}
