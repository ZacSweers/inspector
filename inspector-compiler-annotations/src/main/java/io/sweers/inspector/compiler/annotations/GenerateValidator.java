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
import java.util.Set;
import javax.annotation.Nullable;

import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * Annotate a given type to indicate to inspector-compiler to generate its Validator in a separate
 * class regardless of a static validator method. There is an optional {@link #FACTORY} you can use
 * to leverage to reflectively look these up.
 */
@Inherited @Retention(RUNTIME) @Target(TYPE) public @interface GenerateValidator {

  Validator.Factory FACTORY = new Validator.Factory() {

    @SuppressWarnings("unchecked") @Nullable @Override
    public Validator<?> create(Type type, Set<? extends Annotation> annotations,
        Inspector inspector) {

      Class<?> rawType = Types.getRawType(type);
      if (!rawType.isAnnotationPresent(GenerateValidator.class)) {
        return null;
      }

      Constructor<? extends Validator> constructor;
      String clsName = rawType.getSimpleName()
          .replace("$", "_");
      String packageName = rawType.getPackage()
          .getName();
      try {
        Class<? extends Validator> bindingClass = (Class<? extends Validator>) rawType.getClassLoader()
            .loadClass(packageName + "." + clsName + "Validator");
        if (type instanceof ParameterizedType) {
          // Generic, so use the two-arg constructor
          constructor = bindingClass.getConstructor(Inspector.class, Type[].class);
        } else {
          // The normal single-arg inspector constructor
          constructor = bindingClass.getConstructor(Inspector.class);
        }
        constructor.setAccessible(true);
      } catch (ClassNotFoundException e) {
        throw new RuntimeException("Unable to find generated Moshi adapter class for " + clsName,
            e);
      } catch (NoSuchMethodException e) {
        throw new RuntimeException("Unable to find binding constructor for " + clsName, e);
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
        throw new RuntimeException(
            "Could not create generated TypeAdapter instance for type " + rawType, cause);
      }
    }
  };
}
