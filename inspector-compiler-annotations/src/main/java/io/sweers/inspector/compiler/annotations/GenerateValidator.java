package io.sweers.inspector.compiler.annotations;

import io.sweers.inspector.Inspector;
import io.sweers.inspector.Types;
import io.sweers.inspector.Validator;
import java.lang.annotation.Annotation;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nullable;

import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;
import static java.util.Collections.synchronizedMap;

/**
 * Annotate a given type to indicate to inspector-compiler to generate its Validator in a separate
 * class regardless of a static validator method. There is an optional {@link #FACTORY} you can use
 * to leverage to reflectively look these up.
 */
@Inherited @Retention(RUNTIME) @Target(TYPE) public @interface GenerateValidator {

  Validator.Factory FACTORY = new Validator.Factory() {
    private final Map<Class<?>, Constructor<? extends Validator>> validators =
        synchronizedMap(new LinkedHashMap<Class<?>, Constructor<? extends Validator>>());

    @SuppressWarnings("unchecked") @Nullable @Override public Validator<?> create(Type type,
        Set<? extends Annotation> annotations,
        Inspector inspector) {

      Class<?> rawType = Types.getRawType(type);
      if (!rawType.isAnnotationPresent(GenerateValidator.class)) {
        return null;
      }

      Constructor<? extends Validator> constructor = findConstructorForClass(rawType);
      if (constructor == null) {
        return null;
      }
      //noinspection TryWithIdenticalCatches Resolves to API 19+ only type.
      try {
        if (constructor.getParameterTypes().length == 1) {
          return constructor.newInstance(inspector);
        } else {
          if (type instanceof ParameterizedType) {
            return constructor.newInstance(inspector,
                ((ParameterizedType) type).getActualTypeArguments());
          } else {
            throw new IllegalStateException("Unable to handle type " + type);
          }
        }
      } catch (IllegalAccessException e) {
        throw new RuntimeException("Unable to invoke " + constructor, e);
      } catch (InstantiationException e) {
        throw new RuntimeException("Unable to invoke " + constructor, e);
      } catch (InvocationTargetException e) {
        Throwable cause = e.getCause();
        if (cause instanceof RuntimeException) {
          throw (RuntimeException) cause;
        }
        if (cause instanceof Error) {
          throw (Error) cause;
        }
        throw new RuntimeException("Could not create generated TypeAdapter instance for type "
            + rawType, cause);
      }
    }

    @Nullable private Constructor<? extends Validator> findConstructorForClass(Class<?> cls) {
      Constructor<? extends Validator> adapterCtor = validators.get(cls);
      if (adapterCtor != null) {
        return adapterCtor;
      }
      String clsName = cls.getSimpleName().replace("$", "_");
      String packageName = cls.getPackage().getName();
      if (clsName.startsWith("android.")
          || clsName.startsWith("java.")
          || clsName.startsWith("kotlin.")) {
        return null;
      }
      try {
        Class<?> bindingClass = cls.getClassLoader()
            .loadClass(packageName + ".Validator_" + clsName);
        try {
          // Try the inspector constructor
          //noinspection unchecked
          adapterCtor =
              (Constructor<? extends Validator>) bindingClass.getConstructor(Inspector.class);
          adapterCtor.setAccessible(true);
        } catch (NoSuchMethodException e) {
          // Try the inspector + Type[] constructor
          //noinspection unchecked
          adapterCtor =
              (Constructor<? extends Validator>) bindingClass.getConstructor(Inspector.class,
                  Type[].class);
          adapterCtor.setAccessible(true);
        }
      } catch (ClassNotFoundException e) {
        adapterCtor = findConstructorForClass(cls.getSuperclass());
      } catch (NoSuchMethodException e) {
        throw new RuntimeException("Unable to find binding constructor for " + clsName, e);
      }
      validators.put(cls, adapterCtor);
      return adapterCtor;
    }
  };
}
