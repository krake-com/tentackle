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

import org.testng.annotations.Test;

import java.util.Map;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;

/**
 * Tests for {@link RemoteMethodAnalyzeHandler} and the {@link RemoteMethodInfo} it creates.
 *
 * @author harald
 */
@SuppressWarnings("missing-explicit-ctor")
public class RemoteMethodAnalyzeHandlerTest {

  private static final Map<String, String> SOURCES = Map.of(
      "test.rm.Impl", """
          package test.rm;
          import java.util.List;
          import java.util.Map;
          import org.tentackle.common.RemoteMethod;
          public class Impl {
            @RemoteMethod
            public List<Map<String, Integer>> nested(long id) { return null; }
            @RemoteMethod
            public List<String> simple() { return null; }
            @RemoteMethod("renamed")
            public void other() {}
          }
          """);

  /**
   * The generic part of a nested generic return type must be complete, i.e. not cut at the first {@code '>'}.
   */
  @Test
  public void keepsNestedGenericReturnTypes() throws Exception {
    AnalyzeCompilation result = AnalyzeCompilation.process(SOURCES);

    RemoteMethodInfo info = RemoteMethodInfo.readInfo(result.analyzeFile("test/rm/Impl/nested.remote"));
    assertNotNull(info);
    assertEquals(info.getClassName(), "test.rm.Impl");
    assertEquals(info.getReturnType(), "java.util.List<java.util.Map<java.lang.String,java.lang.Integer>>");
    assertEquals(info.getGenericReturnType(), "<java.util.Map<java.lang.String,java.lang.Integer>>");
    assertEquals(info.getParameters()[0].getType(), "long");

    info = RemoteMethodInfo.readInfo(result.analyzeFile("test/rm/Impl/simple.remote"));
    assertEquals(info.getGenericReturnType(), "<java.lang.String>");
  }

  @Test
  public void annotationValueOverridesTheFileName() throws Exception {
    AnalyzeCompilation result = AnalyzeCompilation.process(SOURCES);

    RemoteMethodInfo info = RemoteMethodInfo.readInfo(result.analyzeFile("test/rm/Impl/renamed.remote"));
    assertNotNull(info);
    assertEquals(info.getMethodName(), "other");
    assertEquals(info.getGenericReturnType(), "");
  }

}
