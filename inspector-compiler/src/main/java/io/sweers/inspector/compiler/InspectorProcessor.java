package io.sweers.inspector.compiler;

import io.sweers.inspector.compiler.plugins.spi.InspectorCompilerContext;
import io.sweers.inspector.compiler.plugins.spi.InspectorExtension;
import java.util.Set;
import javax.lang.model.element.TypeElement;

public interface InspectorProcessor {
  void process(InspectorCompilerContext context,
      TypeElement element,
      Set<InspectorExtension> extensions);
}
