package io.sweers.inspector.extensions.autovalue;

import com.google.auto.service.AutoService;
import com.google.auto.value.AutoValue;
import io.sweers.inspector.compiler.plugins.spi.AbstractInspectorExtension;
import io.sweers.inspector.compiler.plugins.spi.InspectorExtension;
import java.util.Collections;
import java.util.Set;

@AutoService(InspectorExtension.class) public final class AutoValueInspectorExtension
    extends AbstractInspectorExtension {

  @Override public Set<String> applicableAnnotations() {
    return Collections.singleton(AutoValue.class.getName());
  }
}
