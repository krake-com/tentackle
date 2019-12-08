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


package org.tentackle.wurblet;

import org.wurbelizer.wurbel.WurbelException;
import org.wurbelizer.wurbel.WurbelHelper;
import org.wurbelizer.wurblet.AbstractJavaWurblet;

import org.tentackle.common.StringHelper;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.StringTokenizer;


/**
 * Base class for the {@code @DTO}-wurblet.
 * <p>
 * Parses wurblet args and the model.
 *
 * @author harald
 */
public class DTOWurblet extends AbstractJavaWurblet {

  /**
   * DTO property.
   */
  protected class Property {

    private final String type;                // the java type
    private final String name;                // the property name
    private final String comment;             // the comment
    private final boolean inherited;          // true if inherited
    private final boolean mutable;            // true if with setter instead of final
    private final boolean required;           // true if attribute is required (builder mode only)
    private final List<String> annotations;   // optional annotations for getters and setters

    public Property(String type, String name, String comment) {
      if (type.startsWith("^")) {
        inherited = true;
        mutable = false;
        required = false;
        this.type = type.substring(1);
      }
      else if (type.startsWith("=")) {
        mutable = true;
        inherited = false;
        required = false;
        this.type = type.substring(1);
      }
      else if (type.startsWith("!")) {
        mutable = false;
        inherited = false;
        required = true;
        this.type = type.substring(1);
      }
      else {
        this.type = type;
        inherited = false;
        mutable = false;
        required = false;
      }
      this.name = name;
      StringBuilder buf = new StringBuilder(comment == null || comment.isEmpty() ? "the " + name + " property" : comment);
      this.annotations = parseAnnotations(buf);
      this.comment = buf.toString().trim();
    }

    public boolean isInherited() {
      return inherited;
    }

    public boolean isMutable() {
      return mutable;
    }

    public boolean isRequired() {
      return required;
    }

    public String getType() {
      return type;
    }

    public String getName() {
      return name;
    }

    public String getComment() {
      return comment;
    }

    public String getGetterName() {
      return ("boolean".equals(type) ? "is" : "get") + StringHelper.firstToUpper(name);
    }

    public String getSetterName() {
      return "set" + StringHelper.firstToUpper(name);
    }

    public List<String> getAnnotations() {
      return annotations;
    }

    public void addGlobalAnnotation(String globalAnnotation) {
      for (String annotation: annotations) {
        StringBuilder annoName = new StringBuilder();
        for (int i=0; i < annotation.length(); i++) {
          char c = annotation.charAt(i);
          if (c == '(') {
            break;
          }
          if (!Character.isWhitespace(c)) {
            annoName.append(c);
          }
        }
        if (annoName.toString().equals(globalAnnotation)) {
          return;
        }
      }
      annotations.add(globalAnnotation);
    }
  }



  protected String filename;            // filename of the here-doc holding the model (usually given as "$> .$filename")
  protected List<String> annotations;   // global annotations
  protected List<Property> properties;  // the DTO properties
  protected boolean needConstructor;    // true if constructor necessary
  protected boolean withBuilder;        // use the builder pattern
  protected String extendsWithBuilder;  // only if builder pattern: super classname (must be with builder as well)


  public DTOWurblet() {
    super();
  }

  @Override
  public void run() throws WurbelException {
    super.run();

    for (String arg: getContainer().getArgs()) {
      if ("--builder".equals(arg)) {
        withBuilder = true;
      }
      else if (filename == null && !arg.startsWith("--")) {
        filename = arg;
      }
    }

    if (filename == null) {
      throw new WurbelException("usage: @wurblet <guardname> DTO [--builder] <filename>");
    }

    if (withBuilder) {
      try {
        extendsWithBuilder = getSuperClassName();
      }
      catch (WurbelException wx) {
        // ok: no superclass
      }
    }

    properties = new ArrayList<>();
    annotations = new ArrayList<>();

    try (BufferedReader reader = new BufferedReader(WurbelHelper.openReader(filename))) {
      String line;
      while ((line = reader.readLine()) != null) {
        line = line.trim();
        if (line.startsWith("[")) {
          // global annotations
          annotations.addAll(parseAnnotations(new StringBuilder(line)));
        }
        else if (!line.isEmpty() && !line.startsWith("#")) {
          StringTokenizer stok = new StringTokenizer(line);
          String type = null;
          String name = null;
          StringBuilder comment = new StringBuilder();
          while (stok.hasMoreTokens()) {
            String token = stok.nextToken();
            if (type == null) {
              type = token;
            }
            else if (name == null) {
              name = token;
            }
            else {
              if (comment.length() > 0) {
                comment.append(' ');
              }
              comment.append(token);
            }
          }
          if (type == null || name == null) {
            throw new WurbelException("property line too short: '" + line + "'");
          }
          Property property = new Property(type, name, comment.toString());
          if (!property.isMutable()) {
            needConstructor = true;
          }
          properties.add(property);
        }
      }

      // apply global annotations but only if not already used in properties
      // for example @Bindable overridden in property line with @Bindable(MAXCOLS=50)
      for (String annotation: annotations) {
        for (Property property : properties) {
          property.addGlobalAnnotation(annotation);
        }
      }

      // validate
      for (Property property : properties) {
        if (property.getType().isEmpty()) {
          throw new WurbelException("missing property type");
        }
        if (!Character.isLetter(property.getType().charAt(0))) {
          throw new WurbelException("property '" + property.getName() + "' has a malformed Java type '" + property.getType() + "'");
        }
        if (!StringHelper.isValidJavaIdentifier(property.getName())) {
          throw new WurbelException("property '" + property.getName() + "' is not a valid Java identifier");
        }
        if (withBuilder) {
          if (property.isInherited()) {
            throw new WurbelException("property '" + property.getName() + "': inherit-option not applicable in builder mode");
          }
        }
        else if (property.isRequired()) {
          throw new WurbelException("property '" + property.getName() + "': required-option only applicable in builder mode");
        }
      }
    }
    catch (IOException ex) {
      throw new WurbelException("reading model " + filename + " failed", ex);
    }
  }


  /**
   * Extracts the annotations in square brackets from a comment string.
   *
   * @param commentBuf the updated comment string buffer
   * @return the annotations, never null
   */
  private List<String> parseAnnotations(StringBuilder commentBuf) {
    List<String> annos = new ArrayList<>();
    String cmt = commentBuf.toString();
    commentBuf.setLength(0);
    StringBuilder anno = null;
    for (int i=0; i < cmt.length(); i++) {
      char c = cmt.charAt(i);
      if (anno == null) {
        if (c == '[') {
          anno = new StringBuilder();
        }
        else {
          commentBuf.append(c);
        }
      }
      else {
        if (c == ']') {
          String an = anno.toString().trim();
          if (!an.isEmpty() && an.startsWith("@")) {
            annos.add(an);
          }
          anno = null;
        }
        else {
          anno.append(c);
        }
      }
    }
    return annos;
  }

}
