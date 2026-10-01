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

package org.tentackle.trip.types;

import org.tentackle.misc.Holder;
import org.tentackle.trip.DictionaryEntry;
import org.tentackle.trip.OpCode;
import org.tentackle.trip.Reference;
import org.tentackle.trip.Serializer;
import org.tentackle.trip.TripComponent;
import org.tentackle.trip.TripFactory;
import org.tentackle.trip.TripRuntimeException;
import org.tentackle.trip.TripStream;
import org.tentackle.trip.TripType;
import org.tentackle.trip.TripTypeCategory;
import org.tentackle.trip.impl.DefaultOpCode;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.function.Consumer;

/**
 * Fallback for dynamic proxies w/o a dedicated trip type.<br>
 * This is the core type for the majority of dynamic proxies.
 * <p>
 * The serialization format is as follows:
 * <ul>
 *   <li>The opcode {@link DefaultOpCode#PROXY}.</li>
 *   <li>The dictionary entry if new for this stream.<br>
 *       Otherwise, the ordinal according to the dictionary.</li>
 *   <li>Followed by one of:
 *     <ul>
 *        <li>The null code if the proxy is null.</li>
 *        <li>The classname of the invocation handler followed by
 *            the serialization of the handler object.</li>
 *     </ul>
 *   </li>
 * </ul>
 *
 * All the sender's enum constants must be declared in the receiver as well.
 * However, the order of the enum constants is not important.
 *
 * @param <T> the proxy type
 */
// created by ProxyTypeBuilder, not mapped by @TripTypeService
public final class ProxyTripType<T> extends AbstractDictionaryBasedTripType<T> {

  private final TripComponent<T, ?>[] components;

  /**
   * Creates the type.
   *
   * @param clazz the proxy's class
   */
  public ProxyTripType(Class<T> clazz) {
    super(clazz, TripTypeCategory.PROXY);
    components = createComponents(clazz.getInterfaces());
  }

  @Override
  public TripComponent<T, ?>[] getComponents() {
    return components;
  }

  @Override
  public void write(T value, TripStream stream) {
    Serializer serializer = stream.getSerializer();
    serializer.serializeOpCode(DefaultOpCode.PROXY);
    sendDictionary(stream);   // send the interfaces
    if (value == null) {
      sendNull(stream);
    }
    else {
      Reference<T> reference = sendReference(stream, value);
      if (reference == null || reference.isObjectFollowing()) {
        InvocationHandler invocationHandler = Proxy.getInvocationHandler(value);
        Class<? extends InvocationHandler> ihClass = invocationHandler.getClass();
        TripType<InvocationHandler> type = stream.getType(ihClass);
        type.write(invocationHandler, stream);
      }
    }
  }

  @Override
  @SuppressWarnings("unchecked")
  public T read(TripStream stream, OpCode opCode, Consumer<T> initializer) {
    DictionaryEntry entry = null;

    if (opCode == DefaultOpCode.PROXY) {
      // determine the dictionary entry from the stream, if provided
      opCode = stream.receiveOpCode();
      if (opCode == DefaultOpCode.DICTIONARY || opCode.isDictionaryOrdinal()) {
        entry = stream.receiveDictionary(opCode);
        opCode = stream.receiveOpCode();
      }

      if (opCode == DefaultOpCode.NULL) {
        return null;
      }
    }
    // else: invoked as a component of another object (see ObjectTripType#readComponents)

    if (entry == null) {
      // invoked as a component of another object.
      // this trip type is already the runtime-type!
      entry = stream.getDictionaryEntry(getObjectClass());
    }

    T value = null;
    boolean readContent = true;
    final Reference<T> reference;
    if (opCode.isReference()) {
      reference = receiveReference(stream, opCode);
      readContent = reference.isObjectFollowing();
      if (readContent) {
        stream.receiveOpCode();    // skip lead opCode (OBJECT, ...)
      }
      else {
        value = reference.getObject();
        if (value == null) {
          throw new TripRuntimeException("reference " + reference + " has no proxy object");
        }
      }
    }
    else {
      reference = null;
    }

    if (readContent) {
      // get the type from the stream (either new dictionary entry or ordinal)
      TripType<InvocationHandler> type = (TripType<InvocationHandler>) stream.receiveDictionary().getType();  // skip code OBJECT
      Holder<T> proxyHolder = new Holder<>();
      Class<?>[] interfaces = entry.getProxyInterfaces();
      // the next opcode is a reference or the handler data
      type.read(stream, stream.receiveOpCode(),
                // initialize the proxy as early as possible to add its reference to the stream,
                // which may be used by the invocation handler or its components.
                // (ex.: org.tentackle.pdo.PdoInvocationHandler)
                ih -> {
                  T proxy = (T) Proxy.newProxyInstance(ih.getClass().getClassLoader(), interfaces, ih);
                  if (initializer != null) {
                    initializer.accept(proxy);
                  }
                  if (reference != null) {
                    stream.addReference(reference, proxy, false);
                    reference.markReceived();
                  }
                  proxyHolder.accept(proxy);
                });
      value = proxyHolder.get();
    }

    return value;
  }


  @SuppressWarnings({ "unchecked", "rawtypes" })
  private TripComponent<T, ?>[] createComponents(Class<?>[] interfaces) {
    TripComponent<T, ?>[] components = new TripComponent[interfaces.length];
    for (int ordinal = 0; ordinal < interfaces.length; ordinal++) {
      components[ordinal] = TripFactory.getInstance().createComponent(this, ordinal, interfaces[ordinal]);
    }
    return components;
  }

}
