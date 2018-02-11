package io.sweers.inspector.extensions.autovalue;

import com.google.auto.service.AutoService;
import io.sweers.inspector.compiler.plugins.spi.InspectorExtension;
import java.util.Collections;
import java.util.Set;

@AutoService(InspectorExtension.class) public final class AutoValueInspectorExtension
    implements InspectorExtension {

  @Override public Set<String> applicableAnnotations() {
    return Collections.singleton("com.google.auto.value.AutoValue");
  }

  @Override public String toString() {
    return AutoValueInspectorExtension.class.getSimpleName();
  }
}
