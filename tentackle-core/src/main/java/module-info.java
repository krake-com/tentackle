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

module org.tentackle.core {

  requires transitive org.tentackle.common;

  requires java.prefs;
  requires java.logging;
  requires java.management;
  requires java.rmi;
  requires java.compiler;
  requires java.sql;

  exports org.tentackle.apt;
  exports org.tentackle.apt.visitor;
  exports org.tentackle.bind;
  exports org.tentackle.daemon;
  exports org.tentackle.io;
  exports org.tentackle.log;
  exports org.tentackle.misc;
  exports org.tentackle.prefs;
  exports org.tentackle.reflect;
  exports org.tentackle.script;
  exports org.tentackle.task;
  exports org.tentackle.validate;
  exports org.tentackle.validate.scope;
  exports org.tentackle.validate.validator;
  exports org.tentackle.validate.severity;

  provides org.tentackle.common.ModuleHook with org.tentackle.service.Hook;

  // Providers for Logger and ScriptingLanguage exist, that are not modularized yet and therefore automatic modules.
  // If tentackle-core is loaded from a jimage file, the META-INFs of such dependencies may be invisible (sometimes,
  // not always...; the real cause is unknown, but that's the observation at least up to JDK 16).
  // The following workaround makes them visible:
  uses org.tentackle.log.Logger;
  uses org.tentackle.script.ScriptingLanguage;

}
