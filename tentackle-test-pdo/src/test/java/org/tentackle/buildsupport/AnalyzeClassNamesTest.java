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

package org.tentackle.buildsupport;

import org.testng.Reporter;
import org.testng.annotations.Test;

import org.tentackle.dbms.AbstractDbObject;
import org.tentackle.misc.ScrollableResource;
import org.tentackle.pdo.DomainContext;
import org.tentackle.pdo.PersistentDomainObject;
import org.tentackle.session.Session;

import java.util.Collection;

import static org.testng.Assert.assertEquals;

/**
 * Verifies that the classnames in {@code ClassNames.properties} still refer to existing classes.
 * <p>
 * The analyze phase classifies the return type and the parameters of a {@code @RemoteMethod} by
 * comparing their {@code TypeMirror} names against the classnames configured in
 * {@code org/tentackle/buildsupport/ClassNames.properties} (the names are configurable so that
 * derived frameworks can point them at their own base classes). The comparison is by string, so
 * a classname that no longer exists does not fail the build - it simply never matches, and the
 * {@code RemoteMethod} wurblet silently generates a delegate that no longer drops the
 * {@code Session} parameter nor binds persistent parameters to the server's session.
 * <p>
 * This is exactly what happened between 2017 and 2026: the properties file still named
 * {@code org.tentackle.pdo.Session} and {@code org.tentackle.persist.AbstractDbObject} long after
 * both classes had moved. The test therefore lives here rather than in
 * {@code tentackle-build-support}: that module is built before - and cannot depend on - the
 * modules whose classes it names. Referring to the classes by literal makes a rename or a move
 * break the compilation of this test, and a change of the properties file break its assertions.
 *
 * @author harald
 */
public class AnalyzeClassNamesTest {

  @Test(groups = "local")
  public void testClassNamesExist() throws Exception {
    Reporter.log("AnalyzeClassNamesTest.testClassNamesExist <br>", true);

    RemoteMethodInfo info = new RemoteMethodInfo(RemoteMethodInfo.TYPE_METHOD);

    assertEquals(info.getNameCollection(), Collection.class.getName(), "CLASSNAME_COLLECTION");
    assertEquals(info.getNameSession(), Session.class.getName(), "CLASSNAME_SESSION");
    assertEquals(info.getNameDbObject(), AbstractDbObject.class.getName(), "CLASSNAME_DBOBJECT");
    assertEquals(info.getNameCursor(), ScrollableResource.class.getName(), "CLASSNAME_CURSOR");
    assertEquals(info.getNameContext(), DomainContext.class.getName(), "CLASSNAME_CONTEXT");
    assertEquals(info.getNamePdo(), PersistentDomainObject.class.getName(), "CLASSNAME_PDO");
  }

}
