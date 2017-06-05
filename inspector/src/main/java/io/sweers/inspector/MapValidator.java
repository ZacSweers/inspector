package io.sweers.inspector;

import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nullable;

/**
 * Validates maps.
 */
final class MapValidator<K, V> extends Validator<Map<K, V>> {
  public static final Factory FACTORY = new Factory() {
    @Override public @Nullable Validator<?> create(Type type,
        Set<? extends Annotation> annotations,
        Inspector inspector) {
      if (!annotations.isEmpty()) return null;
      Class<?> rawType = Types.getRawType(type);
      if (rawType != Map.class) return null;
      Type[] keyAndValue = Types.mapKeyAndValueTypes(type, rawType);
      return new MapValidator<>(inspector, keyAndValue[0], keyAndValue[1]).nullSafe();
    }
  };

  private final Validator<K> keyAdapter;
  private final Validator<V> valueAdapter;

  MapValidator(Inspector inspector, Type keyType, Type valueType) {
    this.keyAdapter = inspector.validator(keyType);
    this.valueAdapter = inspector.validator(valueType);
  }

  @Override public void validate(Map<K, V> map) throws ValidationException {
    for (Map.Entry<K, V> entry : map.entrySet()) {
      if (entry.getKey() == null) {
        throw new ValidationException("Map key is null at");
      }
      keyAdapter.validate(entry.getKey());
      valueAdapter.validate(entry.getValue());
    }
  }

  @Override public String toString() {
    return "Validator(" + keyAdapter + "=" + valueAdapter + ")";
  }
}
