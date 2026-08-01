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

package org.tentackle.dbms.trip;

import org.tentackle.common.Service;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a class as a candidate for preloading its remote delegate.<br>
 * Applicable to persistence implementations of entities and operations, i.e. all classes
 * registered via {@link org.tentackle.dbms.Db#prepareRemoteDelegate}.
 * <p>
 * Remote delegates are created lazily, one roundtrip per class. For applications with a large
 * entity model running on links with a high latency, this may slow down the application startup
 * substantially. Annotated classes are collected at build time and can be requested by the client
 * in a single roundtrip via {@link org.tentackle.session.Session#preloadRemoteDelegates()}.
 * <p>
 * The annotation only makes a class a <em>candidate</em>: nothing is preloaded unless the
 * application explicitly requests it, since not all sessions benefit from preloading.
 * Annotate the classes used during startup, not the whole entity model.
 * <p>
 * Notice that preloading creates the delegate on the server, which invokes
 * {@link RemoteDbDelegateImpl#initialize()}. Classes whose delegates change the state of the
 * server session when created must not be annotated. {@code DbModificationTracker} is such
 * an example: creating its delegate marks the session as the modification tracker's session.
 *
 * @see org.tentackle.session.Session#preloadRemoteDelegates()
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Service(PreloadRemoteDelegate.class)   // the annotation itself serves as the service name
public @interface PreloadRemoteDelegate {
}
