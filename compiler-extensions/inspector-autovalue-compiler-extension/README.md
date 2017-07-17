inspector-autovalue-compiler-extension
======================================

An inspector compiler extension that tells the inspector compiler to look for `@AutoValue`-annotated 
classes to generate validators for. Note that you must still have a method returning a Validator, this
just tells the compiler where to look for 'em.

[![Maven Central](https://img.shields.io/maven-central/v/io.sweers.inspector/inspector-autovalue-compiler-extension.svg)](https://mvnrepository.com/artifact/io.sweers.inspector/inspector-autovalue-compiler-extension)
```gradle
compileOnly 'io.sweers.inspector:inspector-autovalue-compiler-extension:x.y.z'
```
