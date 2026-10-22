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

package org.tentackle.pdo;

import org.tentackle.reflect.Interception;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.function.Consumer;

/**
 * Annotation to mark a method to be invoked after the current transaction has been successfully committed.
 * <p>
 * When such a method is invoked, it is <em>not</em> immediately executed, but registered as a
 * post-commit via {@link org.tentackle.session.Session#postCommit(Consumer)}.
 * <ul>
 *   <li>The method must return {@code void}, otherwise a {@link org.tentackle.session.PersistenceException} is thrown.</li>
 *   <li>A transaction must be running when the method is invoked.</li>
 *   <li>The method is executed only if the transaction is committed, and in the JVM it was invoked in,
 *   i.e., it is not executed in the remote server.</li>
 * </ul>
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
@Interception(implementedBy=PostCommitInterceptor.class, type = Interception.Type.PUBLIC)
public @interface PostCommit {

}
