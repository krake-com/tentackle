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

import java.util.function.Supplier;

/**
 * A {@link Logger} delegating everything to a {@link DefaultLogger}.
 * <p>
 * Base class for the logger fixtures of the {@link DefaultLoggerFactory} tests. The fixtures cannot
 * extend {@link DefaultLogger} directly, since its public static {@code getLogger(String)} cannot be
 * hidden by a method that is non-static or less accessible - which is exactly what some of the
 * fixtures need to declare.
 *
 * @author harald
 */
public abstract class DelegatingTestLogger implements Logger {

  private final String name;
  private final DefaultLogger delegate;

  /**
   * Creates a delegating logger.
   *
   * @param name the logger name
   */
  protected DelegatingTestLogger(String name) {
    this.name = name;
    this.delegate = new DefaultLogger(name);
  }

  /**
   * Gets the name this logger was created for.
   *
   * @return the logger name
   */
  public String getName() {
    return name;
  }

  @Override
  public boolean isLoggable(Level level) {
    return delegate.isLoggable(level);
  }

  @Override
  public boolean isFinerLoggable() {
    return delegate.isFinerLoggable();
  }

  @Override
  public boolean isFineLoggable() {
    return delegate.isFineLoggable();
  }

  @Override
  public boolean isInfoLoggable() {
    return delegate.isInfoLoggable();
  }

  @Override
  public boolean isWarningLoggable() {
    return delegate.isWarningLoggable();
  }

  @Override
  public boolean isSevereLoggable() {
    return delegate.isSevereLoggable();
  }

  @Override
  public void log(Level level, String message, Throwable cause) {
    delegate.log(level, message, cause);
  }

  @Override
  public void log(Level level, String message, Object... params) {
    delegate.log(level, message, params);
  }

  @Override
  public void log(Level level, Throwable cause, Supplier<String> messageSupplier) {
    delegate.log(level, cause, messageSupplier);
  }

  @Override
  public void finer(String message) {
    delegate.finer(message);
  }

  @Override
  public void fine(String message) {
    delegate.fine(message);
  }

  @Override
  public void info(String message) {
    delegate.info(message);
  }

  @Override
  public void warning(String message) {
    delegate.warning(message);
  }

  @Override
  public void severe(String message) {
    delegate.severe(message);
  }

  @Override
  public void finer(String message, Object... params) {
    delegate.finer(message, params);
  }

  @Override
  public void fine(String message, Object... params) {
    delegate.fine(message, params);
  }

  @Override
  public void info(String message, Object... params) {
    delegate.info(message, params);
  }

  @Override
  public void warning(String message, Object... params) {
    delegate.warning(message, params);
  }

  @Override
  public void severe(String message, Object... params) {
    delegate.severe(message, params);
  }

  @Override
  public void finer(String message, Supplier<?>... paramSuppliers) {
    delegate.finer(message, paramSuppliers);
  }

  @Override
  public void fine(String message, Supplier<?>... paramSuppliers) {
    delegate.fine(message, paramSuppliers);
  }

  @Override
  public void info(String message, Supplier<?>... paramSuppliers) {
    delegate.info(message, paramSuppliers);
  }

  @Override
  public void warning(String message, Supplier<?>... paramSuppliers) {
    delegate.warning(message, paramSuppliers);
  }

  @Override
  public void severe(String message, Supplier<?>... paramSuppliers) {
    delegate.severe(message, paramSuppliers);
  }

  @Override
  public void finer(String message, Throwable cause) {
    delegate.finer(message, cause);
  }

  @Override
  public void fine(String message, Throwable cause) {
    delegate.fine(message, cause);
  }

  @Override
  public void info(String message, Throwable cause) {
    delegate.info(message, cause);
  }

  @Override
  public void warning(String message, Throwable cause) {
    delegate.warning(message, cause);
  }

  @Override
  public void severe(String message, Throwable cause) {
    delegate.severe(message, cause);
  }

  @Override
  public void finer(Throwable cause, Supplier<String> messageSupplier) {
    delegate.finer(cause, messageSupplier);
  }

  @Override
  public void fine(Throwable cause, Supplier<String> messageSupplier) {
    delegate.fine(cause, messageSupplier);
  }

  @Override
  public void info(Throwable cause, Supplier<String> messageSupplier) {
    delegate.info(cause, messageSupplier);
  }

  @Override
  public void warning(Throwable cause, Supplier<String> messageSupplier) {
    delegate.warning(cause, messageSupplier);
  }

  @Override
  public void severe(Throwable cause, Supplier<String> messageSupplier) {
    delegate.severe(cause, messageSupplier);
  }

  @Override
  public void finer(Supplier<String> messageSupplier) {
    delegate.finer(messageSupplier);
  }

  @Override
  public void fine(Supplier<String> messageSupplier) {
    delegate.fine(messageSupplier);
  }

  @Override
  public void info(Supplier<String> messageSupplier) {
    delegate.info(messageSupplier);
  }

  @Override
  public void warning(Supplier<String> messageSupplier) {
    delegate.warning(messageSupplier);
  }

  @Override
  public void severe(Supplier<String> messageSupplier) {
    delegate.severe(messageSupplier);
  }

  @Override
  public void logStacktrace(Level level, Throwable cause) {
    delegate.logStacktrace(level, cause);
  }

  @Override
  public void logStacktrace(Throwable cause) {
    delegate.logStacktrace(cause);
  }

  @Override
  public Object getLoggerImpl() {
    return delegate.getLoggerImpl();
  }

  @Override
  public MappedDiagnosticContext getMappedDiagnosticContext() {
    return delegate.getMappedDiagnosticContext();
  }

}
