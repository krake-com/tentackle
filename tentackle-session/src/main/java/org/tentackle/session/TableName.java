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

package org.tentackle.session;

import org.tentackle.common.Analyze;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation for the tablename of a PDO.
 * <p>
 * The tablename can be modified by the build-time options:
 * <ul>
 * <li>mapSchema: if true, dots will be replaced by underscores</li>
 * <li>prefix: prepends the given string to the tablename, i.e. to the schema, if any</li>
 * </ul>
 *
 * Those options are provided as wurblet properties in the pom, such as:
 * <pre>
      &lt;wurbletProperties&gt;
              &lt;mapSchema&gt;false&lt;/mapSchema&gt;
              &lt;tablePrefix&gt;app&lt;/tablePrefix&gt;
              ...
   </pre>
 *
 * and injected into the code by the wurblets:
 *
 * <pre>
   &#64;TableName(value=&#47;*&#64;*&#47;"md.customer"&#47;*&#64;*&#47;,    // &#64;wurblet &lt; Inject --string $tablename
           mapSchema=&#47;*&#64;*&#47;false&#47;*&#64;*&#47;,     // &#64;wurblet &lt; Inject $mapSchema
           prefix=&#47;*&#64;*&#47;"app"&#47;*&#64;*&#47;)    // &#64;wurblet &lt; Inject --string $tablePrefix
  </pre>
 *
 * Means: all schemas get "app" prepended (for example, "td.message" becomes "apptd.message").
 * The technical tables of the Tentackle framework are built without these options and therefore
 * keep their plain names in the default schema.
 * This configuration is particularly useful for databases like Oracle that
 * emulate schemas via extra users. As a result, the main user "app" remains unchanged;
 * transaction data goes to the "schema-user" apptd while the master data goes to appmd.
 * The model, however, remains unchanged! It's just a configuration in the pom.<p>
 * With mapSchema=true and prefix="" all tables go to the default schema, while
 * master data tables start with md_ and transaction data with td_.
 * <p>
 * Notice that the prefix is not applied to the generated DDL, see the documentation on table names.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Analyze("org.tentackle.buildsupport.TableNameAnalyzeHandler")
public @interface TableName {

  /**
   * Determines the tablename.
   *
   * @return the tablename
   */
  String value();

  /**
   * Determines whether schemas are allowed or mapped to simple table names.<br>
   * If true, table names like "md.blah" are mapped to "md_blah".
   * This is especially useful for databases that don't support schemas
   * or provide only quirky workarounds (Oracle, MySQL, for example).
   *
   * @return true if map to non-schema name
   */
  boolean mapSchema() default false;

  /**
   * Adds a prefix to the tablename, i.e. to the schema name if the tablename contains a schema.
   *
   * @return the optional prefix, empty if none
   */
  String prefix() default "";

}
