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


package org.tentackle.reflect;

import org.tentackle.common.Service;
import org.tentackle.common.ServiceFactory;
import org.tentackle.common.TentackleRuntimeException;

import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;


interface InterceptionUtilitiesHolder {
  InterceptionUtilities INSTANCE = ServiceFactory.createService(InterceptionUtilities.class, InterceptionUtilities.class);
}


/**
 * Utility methods for interception handling.
 *
 * @author harald
 */
@Service(InterceptionUtilities.class)   // defaults to self
public class InterceptionUtilities {

  /**
   * The singleton.
   *
   * @return the singleton
   */
  public static InterceptionUtilities getInstance() {
    return InterceptionUtilitiesHolder.INSTANCE;
  }



  /**
   * Finds all interceptors of a method.
   *
   * @param clazz the interface the interface the method is declared in extends
   * @param method the interface method
   * @param implMethod the implementing class method
   * @return the head of interceptors, null if not intercepted
   */
  @SuppressWarnings("unchecked")
  public Interceptor findInterceptors(Class<?> clazz, Method method, Method implMethod) {

    List<Interceptor> interceptors = new ArrayList<>();
    Class<?> declaringClass = method.getDeclaringClass();

    // find public interceptors first
    if (declaringClass.isInterface() && // must be declared in an interface...
        clazz.isAssignableFrom(declaringClass)) {  // extending a given interface (e.g. Interceptable)

      for (Annotation annotation : method.getAnnotations()) {
        /*
         * in dynamic proxies the returned annotations are proxies as well.
         * Therefore we must load the real annotation by annotationType() and not getClass()!
         */
        // check if annotation is annotated with Interception
        Interception interception = annotation.annotationType().getAnnotation(Interception.class);
        if (interception != null) {
          interceptors.add(createInterceptor(interception, annotation));
        }
      }
    }

    // find hidden interceptors (will be invoked after the public ones)
    declaringClass = implMethod.getDeclaringClass();
    if (!declaringClass.isInterface() && // must be declared in an implementation
        clazz.isAssignableFrom(implMethod.getDeclaringClass())) {  // extending a given interface (e.g. Interceptable)

      for (Annotation annotation : implMethod.getAnnotations()) {
        /*
         * in dynamic proxies the returned annotations are proxies as well.
         * Therefore we must load the real annotation by annotationType() and not getClass()!
         */
        // check if annotation is annotated with Interception
        Interception interception = annotation.annotationType().getAnnotation(Interception.class);
        if (interception != null) {
          interceptors.add(createInterceptor(interception, annotation));
        }
      }
    }

    // chain interceptors if more than one
    if (interceptors.size() > 1) {
      int ndx = interceptors.size() - 1;
      Interceptor chainedInterceptor = interceptors.get(ndx);   // last in chain
      while (--ndx >= 0) {
        Interceptor interceptor = interceptors.get(ndx);
        interceptor.setChainedInterceptor(chainedInterceptor);
        chainedInterceptor = interceptor;
      }
    }

    return interceptors.isEmpty() ? null : interceptors.get(0);
  }



  /**
   * Creates the interceptor.
   *
   * @param interception the interception
   * @param annotation the interceptor annotation (for diagnostics only)
   * @return the interceptor
   */
  @SuppressWarnings("unchecked")
  private Interceptor createInterceptor(Interception interception, Annotation annotation) {
    Class<? extends Interceptor> implClass = interception.implementedBy();
    try {
      if (implClass == Interceptor.class) {
        // load by name or service
        if (!interception.implementedByName().isEmpty()) {
          implClass = (Class<? extends Interceptor>) Class.forName(interception.implementedByName());
        }
        else if (!interception.implementedByService().isEmpty()) {
          Map.Entry<String,URL> entry = ServiceFactory.getServiceFinder().
                  findFirstServiceConfiguration(interception.implementedByService());
          implClass = (Class<? extends Interceptor>) Class.forName(entry.getKey());
        }
        else {
          // default to service (name of interceptor)
          Map.Entry<String,URL> entry = ServiceFactory.getServiceFinder().
                  findFirstServiceConfiguration(annotation.annotationType().getName());
          implClass = (Class<? extends Interceptor>) Class.forName(entry.getKey());
        }
      }
      Interceptor interceptor = implClass.getDeclaredConstructor().newInstance();
      interceptor.setAnnotation(annotation);
      return interceptor;
    }
    catch (ClassNotFoundException | IllegalAccessException | IllegalArgumentException | InstantiationException |
           NoSuchMethodException | SecurityException | InvocationTargetException ex) {
      throw new TentackleRuntimeException("interceptor @" + annotation + " could not be created", ex);
    }
  }


  /**
   * Creates an interceptable method.
   * <p>
   * Finds the implementing method of the delegate and optional interceptors.
   *
   * @param clazz the topmost interceptable's interface
   * @param delegateClazz the delegate's class to invoke the method on
   * @param method the interface's method to invoke
   * @return the interceptable method
   */
  public InterceptableMethod createInterceptableMethod(Class<? extends Interceptable> clazz, Class<? extends Interceptable> delegateClazz, Method method) {
    Method effectiveMethod = ReflectionHelper.findDeclaredMethod(clazz, method);
    Method implMethod;
    try {
      implMethod = delegateClazz.getMethod(method.getName(), method.getParameterTypes());
    }
    catch (NoSuchMethodException nx) {
      throw new TentackleRuntimeException("cannot create interceptable method", nx);
    }
    Interceptor interceptor = findInterceptors(Interceptable.class, effectiveMethod, implMethod);
    return new InterceptableMethod(method, implMethod, interceptor);
  }

}
