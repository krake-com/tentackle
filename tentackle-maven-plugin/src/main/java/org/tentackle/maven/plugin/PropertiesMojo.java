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


package org.tentackle.maven.plugin;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;

import org.tentackle.common.ServiceFactory;
import org.tentackle.common.TentackleRuntimeException;
import org.tentackle.maven.AbstractTentackleMojo;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * Generates maven properties from other maven properties.
 * <p>
 * Example:
 * <pre>
 *   &lt;propertyDescriptors&gt;
 *     &lt;propertyDescriptor&gt;
 *      &lt;input&gt;${dbPasswd}&lt;/input&gt;
 *      &lt;converter&gt;@org.tentackle.common.Cryptor&lt;/converter&gt;
 *      &lt;property&gt;encryptedPasswd&lt;/property&gt;
 *     &lt;/propertyDescriptor&gt;
 *   &lt;/propertyDescriptors&gt;
 * </pre>
 * <p>
 * The converter is a FQCN of a singleton with a no-args constructor. Because it must be
 * in the classpath of the {@code tentackle-maven-plugin}, its artifact must be placed
 * within the plugin's {@code <dependencies>}.<br>
 * If the FQCN starts with an {@code @}, the converter is looked up via {@code META-INF/services}.
 * <p>
 * Important: the converters must be stateless because their instances are cached!
 *
 * @author harald
 */
@Mojo(name = "properties",
      defaultPhase = LifecyclePhase.GENERATE_RESOURCES,
      requiresDependencyResolution = ResolutionScope.COMPILE,
      threadSafe = true)
public class PropertiesMojo extends AbstractTentackleMojo {

  @Parameter
  private List<PropertyDescriptor> propertyDescriptors;

  private final Map<String, Function<String,String>> converterMap = new ConcurrentHashMap<>();


  @Override
  @SuppressWarnings("unchecked")
  public void executeImpl() throws MojoExecutionException, MojoFailureException {
    if (propertyDescriptors != null) {
      for (PropertyDescriptor propertyDescriptor : propertyDescriptors) {
        String property = propertyDescriptor.getProperty();
        if (property == null) {
          throw new MojoExecutionException("missing property name in property descriptor");
        }

        String converter = propertyDescriptor.getConverter();
        if (converter == null) {
          throw new MojoExecutionException("missing converter in property descriptor");
        }

        Function<String, String> function = converterMap.get(converter);
        if (function == null) {
          String className = converter;
          if (converter.startsWith("@")) {
            // locate via service
            className = converter.substring(1);
            try {
              className = ServiceFactory.getServiceFinder().findFirstServiceConfiguration(className).getKey();
            }
            catch (TentackleRuntimeException ex) {
              throw new MojoExecutionException("cannot load converter " + converter, ex);
            }
          }

          try {
            Class<?> clazz = Class.forName(className);
            if (!Function.class.isAssignableFrom(clazz)) {
              throw new MojoExecutionException(clazz + " is not a java.util.Function");
            }
            Method method = clazz.getMethod("apply", String.class);
            if (method.getReturnType() != String.class) {
              throw new MojoExecutionException("method apply(String) of " + className + " does not return a String");
            }
            function = (Function<String, String>) clazz.getConstructor().newInstance();
            converterMap.put(converter, function);
          }
          catch (ClassNotFoundException e1) {
            throw new MojoExecutionException("no such converter: " + className, e1);
          }
          catch (NoSuchMethodException e2) {
            throw new MojoExecutionException(className + " does not provide a method apply(String)", e2);
          }
          catch (IllegalAccessException | InstantiationException | InvocationTargetException e3) {
            throw new MojoExecutionException(className + " cannot be instantiated", e3);
          }
        }

        String value = function.apply(propertyDescriptor.getInput());

        getMavenProject().getProperties().setProperty(property, value);

        if (verbosityLevel.isInfo()) {
          getLogger().info("created property: {} = {}", property, value);
        }
      }
    }
  }

}
