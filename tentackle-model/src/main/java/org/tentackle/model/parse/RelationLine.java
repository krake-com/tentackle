/*
 * Tentackle - https://tentackle.org.
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

package org.tentackle.model.parse;

import org.tentackle.model.ModelException;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Describes the relation to other entities.
 * <p>
 * Syntax:<br>
 * <pre>
 * &lt;class&gt;: &lt;property&gt;=&lt;value&gt;[ &lt;value&gt; &lt;value&gt;], &lt;property&gt;=..., ...
 * </pre>
 *
 * Properties:<p>
 *
 * relation = &lt;object|list|reversed&gt; [composite|component] [tracked] [referenced] [processed] [readonly|writeonly|nomethod]
 *            [serialized] [remoteclear] [reversed] [shallow] [immutable] [normtext] [blunt]
 * <ul>
 *    <li>object: single object association [1:1]. This is the default.</li>
 *    <li>reversed: non-composite list relation that contains max. one object (reversed 1:1 object relation)</li>
 *    <li>list: list of objects [1:N]. ([N:M] see below nm = ...)</li>
 *    <li>composite/component: the relation points to a component (default if the related entity's type is embedded)</li>
 *    <li>tracked: modification state of the object or list is tracked</li>
 *    <li>referenced: set the parent reference on select and construction</li>
 *    <li>processed: invokes a processXXX-method on initialization, after select and before save</li>
 *    <li>readonly: don't generate set-method</li>
 *    <li>writeonly: don't generate get-method</li>
 *    <li>nomethod: don't generate set- and get-method (shorthand for readonly writeonly)</li>
 *    <li>serialized (only if lazy and not composite): lazy non-composite references are transient by default. "serialized" makes them non-transient.</li>
 *    <li>remoteclear: clear serialized relation before save (i.e. sending to server)</li>
 *    <li>shallow: exclude this relation in snapshots or copies</li>
 *    <li>immutable: set this relation immutable after the initial load</li>
 *    <li>normtext: add the toString value of this relation to the normtext. If this is a list, add toStrings of all PDOs.</li>
 *    <li>blunt: the blunt-method becomes part of the persistence interface.</li>
 * </ul>
 * If the relation is associated to an attribute the readonly and writeonly flags are overwritten
 * by the attribute.
 * <p>
 *
 * select = &lt;always|eager[:depth]|lazy|embedded&gt; [cached] [| wurbletargs]
 * <ul>
 *    <li>always: fetched on every get (default if cached)</li>
 *    <li>eager: fetched in advance whenever the main class is fetched</li>
 *    <li>depth: number of relation hops the eager join cascade may span, at least 1 (the default).<br>
 *        A depth &gt; 1 also joins the eager relations of the related entity, and so on.<br>
 *        Example: <code>select = eager:2</code></li>
 *    <li>lazy: fetched within the first get only (default if not cached)</li>
 *    <li>embedded: fetched by parent (default if the related entity's type is embedded)</li>
 *    <li>cached: use caching select methods if possible (only for objects-relations)</li>
 *    <li>wurbletargs: additional arguments passed to the PdoSelectList-wurblet (only for list-relations)<br>
 *        Example: <code>select = lazy | -pilePosition</code></li>
 * </ul>
 *
 * delete = [cascade]
 * <ul>
 *    <li>cascade: composition is a list that contains compositions (only if model without integrity checks)</li>
 * </ul>
 *
 * link = &lt;name-of-link-method&gt; [index]
 * <ul>
 *    <li>only for composite relations: special method to set the links in composite lists or objects
 *    in saveReferencingRelations. For example: <code>setBlah</code>. <code>set</code> alone will be
 *    replaced by the corresponding method name according to the model.<br>
 *    The methods arguments are fixed to (this).</li>
 *    <li>
 *    [index] is an integer attribute of the referenced entity.<br>
 *    If [index] is set, (this, ndx) is invoked with index beginning at 0 (only for lists).<br>
 *    [index] may be a path to an attribute of an embedded entity, as well.
 *    </li>
 * </ul>
 *
 * args = &lt;foreign-attribute2&gt;[&lt;method&gt; | &lt;constant&gt;] &lt;foreign-attribute3&gt;[&lt;method&gt; | &lt;constant&gt;]...
 * <ul>
 *    <li>second and more arguments passed to selectBy- or deleteBy-method. Only applicable to list relations
 *      or reversed object relations.<br>
 *      Example: <code>args = forCreditor[isCreditor()] lineType[CalculationLineType.ON_CALCULATION]</code><br>
 *      The first argument cannot be overwritten. For lists, it is <code>&lt;name-of-current-entity&gt;Id[getId()]</code>.<br>
 *      (For object relations it is <code>id[get&lt;name-of-relation&gt;Id()]</code> and no extra args are allowed anyway.)
 *    </li>
 * </ul>
 *
 * method = &lt;methodname&gt;
 * <ul>
 *    <li>methodname: is the part of the method names, i.e., select[By]&lt;methodname&gt;, delete[By]&lt;methodname&gt;,
 *    set&lt;methodname&gt;<br>
 *    [By] is added for lists.</li>
 * </ul>
 *
 * name = &lt;relationname&gt;
 * <ul>
 *    <li>relationname: name of the relation. Defaults to &lt;class&gt;.<br>
 *    The getter- and setter-names are generated as follows:<br>
 *    get&lt;name&gt; and set&lt;name&gt; (always first letter in uppercase) for object-relations and
 *    get&lt;name&gt;List, set&lt;name&gt;List for list-relations.<br>
 *    If &lt;name&gt; is explicitly set, the methods will be named get&lt;name&gt; and set&lt;name&gt;
 *    regardless of the relation type.
 *    </li>
 * </ul>
 *
 * prefix = &lt;columnprefix&gt;
 * <ul>
 *    <li>columnprefix: the prefix to add to the column names of the related embedded PDO.<br>
 *    Only applicable to embedded relations!<br>
 *    Defaults to lowercase of &lt;relationname&gt; plus an underline.<br>
 *    For nested embedded PDOs, the prefixes are concatenated accordingly.
 *    </li>
 * </ul>
 *
 * nm = &lt;entity&gt; [mnMethodName] [nmScope]
 * <ul>
 *    <li>entity: name of opposite entity the N:M relation.</li>
 *    <li>mnMethodName: optional method name to access the list of opposite entities.</li>
 *    <li>mnScope: optional access scope of the nm-method (defaults to scope = ...). Can be used
 *        to hide the technical methods via <code>scope=private</code> from the interfaces and making the nm-method <code>public</code>.</li>
 * </ul>
 * The relation must be a list relation.<p>
 *
 * scope = &lt;public|private|protected|package&gt;
 * <ul>
 *    <li>sets the scope of the generated setter/getter-methods. Defaults to public.</li>
 * </ul>
 *
 * count = &lt;counter attribute&gt;
 * <ul>
 *    <li>sets the counter attribute. Must be numeric. Only available for composite non-reversed list relations.</li>
 * </ul>
 *
 * comment = &lt;comment&gt;
 * <ul>
 *    <li>to include in getter/setter comment</li>
 * </ul>
 *
 * @author harald
 */
public class RelationLine extends MultiLine {

  private String className;               // the classname
  private Map<String,String> properties;  // the properties


  /**
   * Creates a relation line.<br>
   * A relation line may span multiple lines.
   *
   * @param document the whole document
   * @param offset offset to first character within document
   * @param lineType the line type
   */
  public RelationLine(Document document, int offset, LineType lineType) {
    super(document, offset, lineType);
  }


  @Override
  public void parse() throws ModelException {
    super.parse();

    String text = getText();

    // find first colon
    int ndx = text.indexOf(':');
    if (ndx < 0) {
      throw createModelException("missing colon (:)");
    }
    className = text.substring(0, ndx).trim();
    if (className.isEmpty()) {
      throw createModelException("missing classname");
    }

    String configText = text.substring(ndx + 1);

    // scan properties , ....
    properties = new LinkedHashMap<>();   // preserver order
    OptionParser parser = new OptionParser(configText, null);
    String prop;
    while ((prop = parser.nextOption()) != null) {
      if (!prop.isEmpty()) {
        ndx = prop.charAt(0) == '@' ? -1 : prop.indexOf('='); // leave annotations as is
        if (ndx > 0) {
          String propName = prop.substring(0, ndx).trim();
          if (propName.isEmpty()) {
            throw createModelException("missing property name");
          }
          String propValue = prop.substring(ndx + 1).trim();
          properties.put(propName, propValue);
        }
        else  {
          properties.put(prop, "");
        }
      }
    }
  }


  /**
   * Gets the classname.
   *
   * @return the classname, never null or empty
   * @throws ModelException if not parsed
   */
  public String getClassName() throws ModelException {
    assertParsed();
    return className;
  }


  /**
   * Gets the properties.
   *
   * @return the properties
   * @throws ModelException if not parsed
   */
  public Map<String,String> getProperties() throws ModelException {
    assertParsed();
    return properties;
  }

}
