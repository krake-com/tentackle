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

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

/**
 * Tests for {@link AnalyzeProcessor}.
 *
 * @author harald
 */
@SuppressWarnings("missing-explicit-ctor")
public class AnalyzeProcessorTest {

  /**
   * A classloader that records the classes it was asked for.
   */
  private static class RecordingClassLoader extends ClassLoader {

    private final Set<String> requested = ConcurrentHashMap.newKeySet();

    private RecordingClassLoader() {
      super(AnalyzeProcessorTest.class.getClassLoader());
    }

    @Override
    protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
      requested.add(name);
      return super.loadClass(name, resolve);
    }
  }


  /**
   * The handlers and the annotation classes must be loaded via the processing classloader,
   * which the maven plugin sets to search the project's classpath as well.
   */
  @Test
  public void loadsClassesViaTheProcessingClassLoader() throws Exception {
    RecordingClassLoader loader = new RecordingClassLoader();
    AnalyzeCompilation result = AnalyzeCompilation.process(loader, Map.of(
        "test.cl.MyService", """
            package test.cl;
            public interface MyService {}
            """,
        "test.cl.Impl", """
            package test.cl;
            @org.tentackle.common.Service(MyService.class)
            public class Impl implements MyService {
              @org.tentackle.common.RemoteMethod
              public void doIt() {}
            }
            """));

    assertEquals(result.serviceLines("META-INF/services/test.cl.MyService"), List.of("test.cl.Impl"));
    assertTrue(result.analyzeFile("test/cl/Impl/doIt.remote").exists());
    assertTrue(loader.requested.contains(ServiceAnalyzeHandler.class.getName()), loader.requested.toString());
    assertTrue(loader.requested.contains(RemoteMethodAnalyzeHandler.class.getName()), loader.requested.toString());
    assertTrue(loader.requested.contains("org.tentackle.common.RemoteMethod"), loader.requested.toString());
  }

  /**
   * The loop detection limits the nesting level of meta-annotations, not the number of
   * meta-annotations of a single annotation.
   */
  @Test
  public void manySiblingMetaAnnotationsAreNoLoop() throws Exception {
    Map<String, String> sources = new TreeMap<>();
    StringBuilder metas = new StringBuilder();
    for (int i = 0; i <= 100; i++) {
      sources.put("test.wide.M" + i, "package test.wide; public @interface M" + i + " {}");
      metas.append("@M").append(i).append('\n');
    }
    sources.put("test.wide.MyService", "package test.wide; public interface MyService {}");
    sources.put("test.wide.Wide", "package test.wide;\n" + metas +
                                  "@org.tentackle.common.Service(MyService.class)\npublic @interface Wide {}");
    sources.put("test.wide.WideImpl", "package test.wide; @Wide public class WideImpl implements MyService {}");

    AnalyzeCompilation result = AnalyzeCompilation.process(sources);

    assertEquals(result.messages(javax.tools.Diagnostic.Kind.WARNING), List.of());
    assertEquals(result.serviceLines("META-INF/services/test.wide.MyService"), List.of("test.wide.WideImpl"));
  }

}
