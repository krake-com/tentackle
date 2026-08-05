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

import org.tentackle.log.Logger;
import org.tentackle.trip.OpCode;
import org.tentackle.trip.Serializer;
import org.tentackle.trip.TripFactory;
import org.tentackle.trip.TripRuntimeException;
import org.tentackle.trip.TripSerializeLambda;
import org.tentackle.trip.TripStream;
import org.tentackle.trip.TripType;
import org.tentackle.trip.impl.DefaultOpCode;

import java.lang.invoke.SerializedLambda;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Wrapper type for lambdas.<br>
 * By default, lambda serialization is disabled.
 * It must be enabled by annotating the capturing class with {@link org.tentackle.trip.TripSerializeLambda}.
 */
// created by ObjectTypeBuilder, not mapped by @TripTypeService
public final class LambdaTripType extends AbstractDictionaryBasedTripType<Object> {

  private static final Logger LOGGER = Logger.get(LambdaTripType.class);

  private static final String LAMBDA_DESERIALIZE_METHOD = "$deserializeLambda$";
  private static final String WRITE_REPLACE_METHOD = "writeReplace";
  private static final String READ_RESOLVE_METHOD = "readResolve";
  private static final String CAPTURING_CLASS_FIELD = "capturingClass";

  /**
   * The lazily initialized trip types and reflective accessors.<br>
   * They cannot be determined in the constructor of {@link LambdaTripType}, since this would fail
   * with an NPE via {@link DefaultOpCode}. Deferring them to a holder leaves the initialization to
   * the JVM's class initialization, which is thread-safe and runs exactly once, on the first
   * lambda being serialized or deserialized.
   * <p>
   * In modular applications the reflective lookups will always evaluate to the fallbacks
   * unless explicitly allowed via a command line option.
   */
  private static final class LambdaTypeHolder {

    private static final TripType<Integer> INTEGER;
    private static final TripType<String> STRING;
    @SuppressWarnings("rawtypes")
    private static final TripType<Class> CLASS;

    /** The readResolve method of SerializedLambda, null if inaccessible. */
    private static final Method READ_RESOLVE;

    /** The capturingClass field of SerializedLambda, null if inaccessible. */
    private static final Field CAPTURING_CLASS;

    static {
      TripFactory factory = TripFactory.getInstance();
      INTEGER = factory.getType(Integer.TYPE);
      STRING = factory.getType(String.class);
      CLASS = factory.getType(Class.class);

      Method readResolve;
      try {
        readResolve = SerializedLambda.class.getDeclaredMethod(READ_RESOLVE_METHOD);
        readResolve.setAccessible(true);
      }
      catch (Exception ex) {
        readResolve = null;
        LOGGER.warning(ex.getMessage() + " -> using fallback " + LAMBDA_DESERIALIZE_METHOD);
      }
      READ_RESOLVE = readResolve;

      Field capturingClass;
      try {
        capturingClass = SerializedLambda.class.getDeclaredField(CAPTURING_CLASS_FIELD);
        capturingClass.setAccessible(true);
      }
      catch (Exception ex) {
        capturingClass = null;
        LOGGER.warning(ex.getMessage() + " -> using fallback Class.forName()");
      }
      CAPTURING_CLASS = capturingClass;
    }
  }


  /**
   * Creates the type.
   */
  public LambdaTripType() {
    super(Object.class);
  }

  @Override
  public boolean isFinal() {
    return true;
  }

  @Override
  public boolean isReferencable(Object value) {
    return false;
  }

  @Override
  public boolean isDictionaryBased() {
    return false;
  }

  @Override
  public void write(Object value, TripStream stream) {
    SerializedLambda wrapper = createWrapper(value);
    Serializer serializer = stream.getSerializer();

    Class<?> capturingClass = getCapturingClass(wrapper, stream);
    if (!capturingClass.isAnnotationPresent(TripSerializeLambda.class)) {
      throw new TripRuntimeException("cannot serialize lambda " + value + " because its capturing class " +
                                      capturingClass.getName() + " is not annotated with @TripSerializeLambda");
    }

    serializer.serializeOpCode(DefaultOpCode.LAMBDA);
    LambdaTypeHolder.CLASS.write(capturingClass, stream);
    LambdaTypeHolder.STRING.write(wrapper.getFunctionalInterfaceClass(), stream);
    LambdaTypeHolder.STRING.write(wrapper.getFunctionalInterfaceMethodName(), stream);
    LambdaTypeHolder.STRING.write(wrapper.getFunctionalInterfaceMethodSignature(), stream);
    LambdaTypeHolder.INTEGER.write(wrapper.getImplMethodKind(), stream);
    LambdaTypeHolder.STRING.write(wrapper.getImplClass(), stream);
    LambdaTypeHolder.STRING.write(wrapper.getImplMethodName(), stream);
    LambdaTypeHolder.STRING.write(wrapper.getImplMethodSignature(), stream);
    LambdaTypeHolder.STRING.write(wrapper.getInstantiatedMethodType(), stream);

    int argCount = wrapper.getCapturedArgCount();
    LambdaTypeHolder.INTEGER.write(argCount, stream);
    for (int i = 0; i < argCount; i++) {
      Object arg = wrapper.getCapturedArg(i);
      TripType<Object> type = stream.getType(arg.getClass());
      type.write(arg, stream);
    }
  }

  @Override
  public Object read(TripStream stream, OpCode opCode) {
    if (opCode == DefaultOpCode.LAMBDA) {
      Class<?> capturingClass = LambdaTypeHolder.CLASS.read(stream, stream.receiveOpCode());
      String functionalInterfaceClassName = LambdaTypeHolder.STRING.read(stream, stream.receiveOpCode());
      String functionalInterfaceMethodName = LambdaTypeHolder.STRING.read(stream, stream.receiveOpCode());
      String functionalInterfaceMethodSignature = LambdaTypeHolder.STRING.read(stream, stream.receiveOpCode());
      int implMethodKind = LambdaTypeHolder.INTEGER.read(stream, stream.receiveOpCode());
      String implClassName = LambdaTypeHolder.STRING.read(stream, stream.receiveOpCode());
      String implMethodName = LambdaTypeHolder.STRING.read(stream, stream.receiveOpCode());
      String implMethodSignature = LambdaTypeHolder.STRING.read(stream, stream.receiveOpCode());
      String instantiatedMethodType = LambdaTypeHolder.STRING.read(stream, stream.receiveOpCode());

      int argCount = LambdaTypeHolder.INTEGER.read(stream, stream.receiveOpCode());
      Object[] args = new Object[argCount];
      for (int i = 0; i < argCount; i++) {
        opCode = stream.receiveOpCode();
        TripType<?> argType = stream.deriveType(opCode);
        if (argType.isDictionaryBased()) {
          opCode = stream.receiveOpCode();
        }
        args[i] = argType.read(stream, opCode);
      }
      SerializedLambda wrapper = new SerializedLambda(capturingClass, functionalInterfaceClassName,
                                                      functionalInterfaceMethodName, functionalInterfaceMethodSignature,
                                                      implMethodKind, implClassName, implMethodName,
                                                      implMethodSignature, instantiatedMethodType, args);
      return readResolve(capturingClass, wrapper);
    }
    else {
      return super.read(stream, opCode);
    }
  }


  private SerializedLambda createWrapper(Object value) {
    try {
      Method writeReplace = value.getClass().getDeclaredMethod(WRITE_REPLACE_METHOD);
      writeReplace.setAccessible(true);
      return (SerializedLambda) writeReplace.invoke(value);
    }
    catch (Exception ex) {
      throw new TripRuntimeException("cannot create SerializedLambda for " + value.getClass(), ex);
    }
  }

  private static Class<?> getCapturingClass(SerializedLambda wrapper, TripStream stream) {
    if (LambdaTypeHolder.CAPTURING_CLASS != null) {
      try {
        return (Class<?>) LambdaTypeHolder.CAPTURING_CLASS.get(wrapper);
      }
      catch (Exception ex) {
        LOGGER.warning("access to SerializedLambda#capturingClass denied unexpectedly -> using fallback Class.forName()", ex);
      }
    }
    return stream.getClass(wrapper.getCapturingClass().replace('/', '.'));
  }

  private Object readResolve (Class<?> capturingClass, SerializedLambda wrapper) {
    if (LambdaTypeHolder.READ_RESOLVE != null) {
      try {
        return LambdaTypeHolder.READ_RESOLVE.invoke(wrapper);
      }
      catch (Exception ex) {
        throw new TripRuntimeException("readResolve failed for lambda " + wrapper, ex);
      }
    }
    else {
      try {
        Method method = capturingClass.getDeclaredMethod(LAMBDA_DESERIALIZE_METHOD, SerializedLambda.class);
        method.setAccessible(true);
        return method.invoke(null, wrapper);
      }
      catch (Exception ex) {
        throw new TripRuntimeException("deserialization failed for lambda " + wrapper, ex);
      }
    }
  }

}
