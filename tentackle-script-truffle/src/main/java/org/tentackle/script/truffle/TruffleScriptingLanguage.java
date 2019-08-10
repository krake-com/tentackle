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

package org.tentackle.script.truffle;

import org.graalvm.polyglot.Source;
import org.tentackle.script.AbstractScriptingLanguage;
import org.tentackle.script.Script;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Language;

import java.io.IOException;
import java.net.URL;
import java.util.Locale;


/**
 * A scripting language based on GraalVM Truffle
 *
 * @author bei
 */
public class TruffleScriptingLanguage extends AbstractScriptingLanguage {

  /**
   * Describes the naming of a scripting language.
   */
  public enum Naming {

    /** name is a url. */
    URL,

    /** name is a shortname. */
    SHORTNAME,

    /** name is an mimetype. */
    MIMETYPE,

  }

  /** the scripting engine. */
  private Context context;

  /** the scripting engine. */
  private Language language = null;

  /**
   * Creates a scripting language by name
   *
   * @param languageName the name of the language
   * @param allowAllAccess flag to allow all access
   */
  public TruffleScriptingLanguage(String languageName, boolean allowAllAccess) {

    context = Context.newBuilder().allowAllAccess(allowAllAccess).build();
    language = context.getEngine().getLanguages().get(languageName);
  }

  /**
   * Creates a scripting language by mime type.
   *
   * @param name the mimetype, shortname or url
   * @param naming one of NamingAbstractJSRScriptingLanguage
   * @throws IllegalArgumentException if url is malformed or language is not found
   * @param allowAllAccess flag to allow all access
   */
  public TruffleScriptingLanguage(String name, Naming naming, boolean allowAllAccess) throws IllegalArgumentException {
    String languageName = null;
    switch (naming) {
      case URL:
        try {
          languageName = Source.findLanguage(new URL(name));
        } catch (IOException e) {
          throw new IllegalArgumentException("url malformed");
        }
        break;
      case MIMETYPE:
        languageName = Source.findLanguage(name);
        break;
      default:
        languageName = name;
        break;
    }
    if ( languageName == null ) {
      throw new IllegalArgumentException("language not found by name " + (languageName == null ? "" : languageName));
    }

    context = Context.newBuilder(languageName).allowAllAccess(allowAllAccess).build();
    language = context.getEngine().getLanguages().get(languageName);
  }

  @Override
  public String getName() {
    return language.getName();
  }

  @Override
  public String[] getAbbreviations() {
    String[] abbrv = {};
    if ( "JavaScript".equals(getName())) {
      String[]a={"javascript","JavaScript","js", "trufflejs", "ECMAScript","ecmascript"};
      abbrv=a;
    } else if ( "Ruby".equals(getName())) {
      String[]a={"ruby","truffleruby","Ruby","truffleRuby"};
      abbrv=a;
    } else if ( "R".equals(getName())) {
      String[]a={"r","R","fastr", "FASTR"};
      abbrv=a;
    } else {
      String[]a={language.getId(),language.getId().toLowerCase(Locale.US),language.getName(),language.getName().toLowerCase(Locale.US)};
      abbrv=a;
    }
    return abbrv;
  }

  @Override
  public Script createScript(boolean cached) {
    return new TruffleScript(this, cached);
  }

  public Script createScript(boolean cached, boolean interactive) {
    return new TruffleScript(this, cached, interactive);
  }

  public Context getContext() {
    return context;
  }

  public Language getLanguage() {
    return language;
  }

}
