package io.sweers.inspector.compiler.plugins.spi;

import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;

/** A value class with various types for the current inspector compiler context. */
public final class InspectorCompilerContext {

  /** The current {@link ProcessingEnvironment}. */
  public final ProcessingEnvironment processingEnv;

  /** The current {@link RoundEnvironment}. */
  public final RoundEnvironment roundEnv;

  /** A flag indicating whether or not the extension should prefer generating kotlin code. */
  public final boolean preferKotlin;

  public InspectorCompilerContext(ProcessingEnvironment processingEnv, RoundEnvironment roundEnv,
      boolean preferKotlin) {
    this.processingEnv = processingEnv;
    this.roundEnv = roundEnv;
    this.preferKotlin = preferKotlin;
  }
}
