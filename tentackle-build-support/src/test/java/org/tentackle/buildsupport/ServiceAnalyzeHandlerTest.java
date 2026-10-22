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

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;

/**
 * Tests for {@link ServiceAnalyzeHandler}.
 *
 * @author harald
 */
@SuppressWarnings("missing-explicit-ctor")
public class ServiceAnalyzeHandlerTest {

  private static final String SERVICE = """
      package test.svc;
      public interface MyService {}
      """;

  @Test
  public void directServiceAnnotationGoesToServices() throws Exception {
    AnalyzeCompilation result = AnalyzeCompilation.process(Map.of(
        "test.svc.MyService", SERVICE,
        "test.svc.DirectImpl", """
            package test.svc;
            @org.tentackle.common.Service(MyService.class)
            public class DirectImpl implements MyService {}
            """));

    assertEquals(result.serviceLines("META-INF/services/test.svc.MyService"), List.of("test.svc.DirectImpl"));
    assertEquals(result.analyzeLines("test/svc/DirectImpl/service.log"), List.of("test.svc.MyService"));
  }

  /**
   * An explicit {@code meta = false} must behave like the default, not like {@code meta = true}.
   */
  @Test
  public void explicitMetaFalseIsNotAMetaService() throws Exception {
    AnalyzeCompilation result = AnalyzeCompilation.process(Map.of(
        "test.svc.MyService", SERVICE,
        "test.svc.MyServiceProvider", """
            package test.svc;
            @org.tentackle.common.Service(value = MyService.class, meta = false)
            public @interface MyServiceProvider {}
            """,
        "test.svc.MyServiceImpl", """
            package test.svc;
            @MyServiceProvider
            public class MyServiceImpl implements MyService {}
            """));

    assertEquals(result.serviceLines("META-INF/services/test.svc.MyService"), List.of("test.svc.MyServiceImpl"));
    assertFalse(result.serviceFile("META-INF/meta-services").exists());
  }

  @Test
  public void metaTrueTakesTheServicingClassFromTheAnnotatedAnnotation() throws Exception {
    AnalyzeCompilation result = AnalyzeCompilation.process(Map.of(
        "test.svc.MyService", SERVICE,
        "test.svc.Other", """
            package test.svc;
            public class Other implements MyService {}
            """,
        "test.svc.MetaProvider", """
            package test.svc;
            @org.tentackle.common.Service(value = MyService.class, meta = true)
            public @interface MetaProvider {
              Class<?> value();
            }
            """,
        "test.svc.Annotated", """
            package test.svc;
            @MetaProvider(Other.class)
            public class Annotated {}
            """));

    assertEquals(result.serviceLines("META-INF/meta-services/test.svc.MyService"), List.of("test.svc.Other"));
    assertFalse(result.serviceFile("META-INF/services/test.svc.MyService").exists());
  }

}
