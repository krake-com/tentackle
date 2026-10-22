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

package org.tentackle.log;

import org.tentackle.common.ServiceFactory;


interface LoggerFactoryHolder {
  LoggerFactory INSTANCE = ServiceFactory.createService(LoggerFactory.class, DefaultLoggerFactory.class);
}


/**
 * Factory for pluggable loggers.
 * <p>
 * Replaceable via &#64;{@link org.tentackle.common.Service}.
 * The default implementation is {@link DefaultLoggerFactory}.
 *
 * @author harald
 */
public interface LoggerFactory {

  /**
   * Gets the singleton.
   *
   * @return the singleton
   */
  static LoggerFactory getInstance() {
    return LoggerFactoryHolder.INSTANCE;
  }


  /**
   * Gets a logger for the given name.
   *
   * @param name the logger name
   * @return the logger
   */
  static Logger getLogger(String name) {
    return LoggerFactory.getInstance().getLogger(name, null);
  }


  /**
   * Gets a logger for the given classname.
   * <p>
   * The method invokes {@link #getLogger(java.lang.String)} with the
   * name of the given class.
   *
   * @param clazz the clazz to get the logger for
   * @return the logger
   */
  static Logger getLogger(Class<?> clazz) {
    return LoggerFactory.getInstance().getLogger(clazz.getName(), null);
  }



  /**
   * Gets a logger for the given name.
   * <p>
   * The implementation first tries to invoke the static method <code>"clazz.getLogger(name)"</code>.
   * If there is no such method, <code>"new clazz(name)"</code> will be invoked.
   * If all else fails, it returns a new {@link DefaultLogger} for the given <code>name</code>.
   * <p>
   * If <code>loggerClass</code> is <code>null</code>, the configured logger class will be used.
   *
   * @param name the logger name
   * @param loggerClass the class implementing {@link Logger},
   *        null to use default class specified by {@code @Service} annotation in classpath
   * @return the logger
   */
  Logger getLogger(String name, Class<? extends Logger> loggerClass);

}
