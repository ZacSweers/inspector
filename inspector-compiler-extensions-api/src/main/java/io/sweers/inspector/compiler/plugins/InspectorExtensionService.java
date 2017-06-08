package io.sweers.inspector.compiler.plugins;

import com.google.common.collect.ImmutableSet;
import io.sweers.inspector.compiler.plugins.spi.InspectorExtension;
import java.util.ServiceLoader;

public final class InspectorExtensionService {
  public static synchronized InspectorExtensionService newInstance() {
    return new InspectorExtensionService();
  }

  private final ServiceLoader<InspectorExtension> serviceLoader =
      ServiceLoader.load(InspectorExtension.class);

  public ImmutableSet<InspectorExtension> get() {
    return ImmutableSet.copyOf(serviceLoader.iterator());
  }
}
