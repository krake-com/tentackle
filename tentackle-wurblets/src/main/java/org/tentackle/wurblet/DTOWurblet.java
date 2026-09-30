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

import org.wurbelizer.misc.Constants;
import org.wurbelizer.wurbel.WurbelDiscardException;
import org.wurbelizer.wurbel.WurbelException;
import org.wurbelizer.wurbel.WurbelHelper;
import org.wurbelizer.wurblet.AbstractJavaWurblet;

import org.tentackle.buildsupport.AnalyzeProcessor;
import org.tentackle.buildsupport.RecordDTOInfo;
import org.tentackle.buildsupport.RecordDTOInfoParameter;
import org.tentackle.common.StringHelper;
import org.tentackle.model.AccessScope;
import org.tentackle.model.parse.OptionParser;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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

    private final String type;                        // the java type
    private final String name;                        // the property name
    private final String comment;                     // the comment
    private final int lineNo;                         // line number in source file
    private final boolean inherited;                  // true if inherited
    private final boolean mutable;                    // true if with setter instead of final
    private final boolean unserialized;               // true if transient (transient is a java keyword, hence "unserialized")
    private final boolean required;                   // true if attribute is required (builder mode only)
    private final boolean withFrom;                   // true if generate fromX method as well
    private final List<String> annotations;           // optional annotations for getters and setters
    private final AccessScope scope;                  // optional access scope (defaults to PUBLIC)

    public Property(String type, String name, String comment, int lineNo) throws WurbelException {
      StringBuilder buf = new StringBuilder();
      int i = 0;
      while (i < type.length()) {
        char c = type.charAt(i);
        if (Character.isJavaIdentifierStart(c)) {
          break;
        }
        buf.append(c);
        i++;
      }
      this.type = type.substring(i);
      this.lineNo = lineNo;
      String options = buf.toString();

      if (options.contains("^")) {
        inherited = true;
        mutable = false;
        required = false;
        unserialized = false;
      }
      else if (options.contains("=")) {
        mutable = true;
        inherited = false;
        required = false;
        unserialized = false;
      }
      else if (options.contains("~")) {
        mutable = true;
        inherited = false;
        required = false;
        unserialized = true;
      }
      else if (options.contains("!")) {
        required = true;
        mutable = false;
        inherited = false;
        unserialized = false;
      }
      else {
        inherited = false;
        mutable = false;
        unserialized = false;
        required = false;
      }
      this.withFrom = options.contains("+");
      this.name = name;
      buf = new StringBuilder(comment == null || comment.isEmpty() ? "the " + name + " property" : comment);
      ParseResult parseResult = parseComment(buf);
      this.annotations = parseResult.annotations;
      this.scope = parseResult.scope;
      this.comment = buf.toString().trim();
    }

    public Property(RecordDTOInfoParameter recordParameter) throws WurbelException {
      this((DTOWurblet.this.withFrom ? "+" : "") + recordParameter.getType(), recordParameter.getName(), null, 0);
    }

    public boolean isPrimitive() {
      return Character.isLowerCase(type.charAt(0));
    }

    public String getHashCodeInvocation() {
      return isPrimitive() ?
               switch (type) {
                 case "double" -> "Double.hashCode(" + name + ")";
                 case "float" -> "Float.hashCode(" + name + ")";
                 case "long" -> "Long.hashCode(" + name + ")";
                 default -> name;
               } :
               "Objects.hashCode(" + name + ")";
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

    public boolean isTransient() {
      return unserialized;
    }

    public boolean isWithFrom() {
      return withFrom;
    }

    public String getType() {
      return type;
    }

    public String getName() {
      return name;
    }

    public int getLineNo() {
      return lineNo;
    }

    public String getConstant() {
      return "PN_" + name.toUpperCase(Locale.ROOT);
    }

    public String getComment() {
      return comment;
    }

    public String getGetterName() {
      return ("boolean".equals(type) ? "is" : "get") + StringHelper.firstToUpper(name);
    }

    public String getFromName() {
      return (asWithers ? "with" : "from") + StringHelper.firstToUpper(name);
    }

    public String getSetterName() {
      return "set" + StringHelper.firstToUpper(name);
    }

    public String getBuilderName() {
      return builderPrefix == null ? name : builderPrefix + StringHelper.firstToUpper(name);
    }

    public List<String> getAnnotations() {
      return annotations;
    }

    public AccessScope getScope() {
      return scope;
    }

    public String getScopeAsKeyword() {
      String keyword = scope.toString();
      if (!keyword.isEmpty()) {
        keyword += " ";
      }
      return keyword;
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
        String anno = annoName.toString();
        if (anno.equals(globalAnnotation) || anno.substring(1).equals(globalAnnotation)) {
          // redefined or explicitly removed via -@ or !@
          return;
        }
      }
      annotations.add(globalAnnotation);
    }

    public void cleanupAnnotations() {
      annotations.removeIf(anno -> anno.startsWith("-@") || anno.startsWith("!@"));
    }
  }



  protected String filename;            // filename of the here-doc holding the model (usually given as "$> .$filename")
  protected List<String> annotations;   // global annotations
  protected List<Property> properties;  // the DTO properties
  protected boolean needConstructor;    // true if constructor necessary
  protected boolean withBuilder;        // use the builder pattern
  protected String builderAnnotation;   // optional builder annotation
  protected AccessScope builderScope;   // access scope for builder's constructor
  protected String builderPrefix;       // optional prefix for builder methods
  protected boolean withFrom;           // true if at least one property has the "from"-option set (or --from/--with forced)
  protected boolean asWithers;          // true if withFrom is set and from-methods should be generated as withers
  protected boolean withEquals;         // true if generate equals method
  protected boolean withHashCode;       // true if generate hashCode method
  protected String validate;            // != null if validate after construction (java code)
  protected boolean withNames;          // true if generate name constants
  protected boolean nott;               // if true, don't use any Tentackle-specific dependencies
  protected boolean canonical;          // if true, add @Canonical... annotations
  protected boolean noFinal;            // if true, generate serialization-friendly code (JEP-500)
  protected String superClass;          // != null for subclass: super classname
  protected String modelSourceName;     // the name of the file containing the model
  protected int modelSourceLine;        // the first line number of the model


  public DTOWurblet() {
    super();
  }

  /**
   * {@inheritDoc}
   * <p>
   * Thread-safe if loaded by the wurblet classloader of the current wurbel run.
   *
   * @see WurbletThreadSafety#isIsolated(Class)
   */
  @Override
  public boolean isThreadSafe() {
    return WurbletThreadSafety.isIsolated(getClass());
  }

  @Override
  public void run() throws WurbelException {
    super.run();

    for (String arg: getContainer().getArgs()) {
      String lArg = arg.toLowerCase(Locale.ROOT);
      if ("--from".equals(lArg)) {
        withFrom = true;
      }
      else if ("--with".equals(lArg)) {
        withFrom = true;
        asWithers = true;
      }
      else if ("--equals".equals(lArg)) {
        if (isRecord()) {
          throw new WurbelException("--equals option not allowed for records");
        }
        withEquals = true;
      }
      else if ("--hashcode".equals(lArg)) {
        if (isRecord()) {
          throw new WurbelException("--hashCode option not allowed for records");
        }
        withHashCode = true;
      }
      else if ("--names".equals(lArg)) {
        withNames = true;
      }
      else if ("--nott".equals(lArg)) {
        nott = true;
      }
      else if ("--canonical".equals(lArg)) {
        canonical = true;
      }
      else if ("--nofinal".equals(lArg)) {
        noFinal = true;
      }
      else if (lArg.startsWith("--builderprefix=")) {
        builderPrefix = arg.substring(16);
        withBuilder = true;
      }
      else if (lArg.startsWith("--builderscope=")) {
        builderScope = AccessScope.valueOf(arg.substring(15).toUpperCase(Locale.ROOT));
        withBuilder = true;
      }
      else if (lArg.startsWith("--builder")) {
        withBuilder = true;
        if (arg.length() > 10 && arg.charAt(9) == '[' && arg.charAt(10) == '@' &&
            arg.charAt(arg.length() - 1) == ']') {
          builderAnnotation = arg.substring(10, arg.length() - 1);
        }
      }
      else if (lArg.startsWith("--validate")) {
        if (isRecord()) {
          throw new WurbelException("--validate option not allowed for records");
        }
        if (arg.length() > 11 && arg.charAt(10) == '=') {
          validate = arg.substring(11);
        }
        else {
          validate = "ValidationUtilities.getInstance().validate(this)";
        }
      }
      else if (filename == null && !arg.startsWith("--")) {
        filename = arg;
      }
    }

    if (nott && canonical) {
      throw new WurbelException("--nott cannot be used with --canonical");
    }

    if (builderScope == null) {
      builderScope = AccessScope.PROTECTED;
    }

    properties = new ArrayList<>();
    annotations = new ArrayList<>();

    if (isRecord()) {
      // load the info file
      String infoName = (getPackageName() + "." + getClassName()).replace('.', '/') +
                        "/" + RecordDTOInfo.INFO_FILE_NAME;
      try {
        RecordDTOInfo info = RecordDTOInfo.readInfo(getContainer().getAnalyzeFile(infoName));
        for (RecordDTOInfoParameter parameter : info.getParameters()) {
          properties.add(new Property(parameter));
        }
      }
      catch (IOException ex) {
        if (getContainer().getAnalyzeFile(AnalyzeProcessor.COMPILE_ERROR_LOG).exists()) {
          throw new WurbelDiscardException("reading info '" + infoName + "' failed", ex);
        }
        else {
          throw new WurbelException("reading info '" + infoName + "' failed (@RecordDTO annotation missing?)", ex);
        }
      }
    }
    else {
      if (filename == null) {
        throw new WurbelException("usage: @wurblet <guardname> DTO [--builder] <filename>");
      }

      try {
        superClass = getSuperClassName();
      }
      catch (WurbelException wx) {
        // ok: no superclass
      }

      int lineNo = 0;
      try (BufferedReader reader = new BufferedReader(WurbelHelper.openReader(filename))) {
        String line;
        while ((line = reader.readLine()) != null) {
          line = line.trim();
          if (line.startsWith(Constants.ORIGIN_INFO_LEAD)) {
            int lineNdx = line.lastIndexOf(':');
            if (lineNdx >= Constants.ORIGIN_INFO_LEAD.length()) {
              modelSourceName = line.substring(Constants.ORIGIN_INFO_LEAD.length(), lineNdx);
              modelSourceLine = Integer.parseInt(line.substring(lineNdx + 1));
            }
            else {
              modelSourceName = line.substring(Constants.ORIGIN_INFO_LEAD.length());
            }
          }
          else if (line.startsWith("[")) {
            // global annotations
            annotations.addAll(parseComment(new StringBuilder(line)).annotations);
          }
          else if (!line.isEmpty() && !line.startsWith("#")) {
            StringTokenizer stok = new StringTokenizer(line);
            StringBuilder type = new StringBuilder(); // concat words for types like Map<Long, String>
            int genericLevel = 0;   // open < ... >
            String name = null;
            StringBuilder comment = new StringBuilder();
            while (stok.hasMoreTokens()) {
              String token = stok.nextToken();
              if (type.isEmpty() || genericLevel > 0) {
                if (!type.isEmpty()) {
                  type.append(' ');
                }
                type.append(token);
                genericLevel = updateGenericLevel(genericLevel, token);
              }
              else if (name == null) {
                name = token;
              }
              else {
                if (!comment.isEmpty()) {
                  comment.append(' ');
                }
                comment.append(token);
              }
            }
            if (type.isEmpty() || name == null) {
              throw new WurbelException("property line too short: '" + line + "'");
            }
            Property property = new Property(type.toString(), name, comment.toString(), modelSourceLine + lineNo);
            if (!property.isMutable()) {
              needConstructor = true;
            }
            if (property.isWithFrom()) {
              withFrom = true;
            }
            properties.add(property);
          }
          lineNo++;
        }

        // apply global annotations but only if not already used in properties
        // for example @Bindable overridden in property line with @Bindable(MAXCOLS=50)
        for (Property property : properties) {
          for (String annotation : annotations) {
            property.addGlobalAnnotation(annotation);
          }
          property.cleanupAnnotations();
        }

        // validate
        Map<String, Property> nameMap = new HashMap<>();
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
          Property existingProperty = nameMap.put(property.getName(), property);
          if (existingProperty != null) {
            throw new WurbelException("property '" + property.getName() + "' defined more than once in " + modelSourceName +
                                      ":" + property.lineNo);
          }
        }
      }
      catch (IOException ex) {
        throw new WurbelException("reading model " + filename + " failed", ex);
      }
    }
  }


  private record ParseResult(List<String> annotations, AccessScope scope) {}

  /**
   * Extracts the annotations and other options in square brackets from a comment string.
   *
   * @param commentBuf the updated comment string buffer
   * @return the annotations, never null
   */
  private ParseResult parseComment(StringBuilder commentBuf) throws WurbelException {
    List<String> annos = new ArrayList<>();
    AccessScope scope = AccessScope.PUBLIC;
    String cmt = commentBuf.toString();
    commentBuf.setLength(0);
    OptionParser parser = new OptionParser(cmt, commentBuf);
    String option;
    while ((option = parser.nextOption()) != null) {
      if (option.startsWith("@") || option.startsWith("-@") || option.startsWith("!@")) {
        annos.add(option);
      }
      else {
        scope = switch (option.toLowerCase(Locale.ROOT)) {
          case "private" -> AccessScope.PRIVATE;
          case "protected" -> AccessScope.PROTECTED;
          case "public" -> AccessScope.PUBLIC;
          case "package" -> AccessScope.PACKAGE;
          default -> throw new WurbelException("invalid access scope '" + option + "' (valid: public, protected, package, private)");
        };
      }
    }
    return new ParseResult(annos, scope);
  }

  private int updateGenericLevel(int oldLevel, String token) {
    return oldLevel +
           StringHelper.countOccurrences(token, '<') -
           StringHelper.countOccurrences(token, '>');
  }

}
