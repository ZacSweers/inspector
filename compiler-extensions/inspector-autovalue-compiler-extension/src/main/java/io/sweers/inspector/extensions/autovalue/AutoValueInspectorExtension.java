package io.sweers.inspector.extensions.autovalue;

import com.google.auto.service.AutoService;
import com.google.auto.value.AutoValue;
import io.sweers.inspector.compiler.plugins.spi.InspectorExtension;
import java.util.Collections;
import java.util.Set;

@AutoService(InspectorExtension.class) public final class AutoValueInspectorExtension
    implements InspectorExtension {

  @Override public Set<String> applicableAnnotations() {
    return Collections.singleton(AutoValue.class.getName());
  }

  @Override public String toString() {
    return AutoValueInspectorExtension.class.getSimpleName();
  }
}
