inspector-compiler
==================

An annotation processor that can generate `Validator` implementations. This does nothing by default, and 
relies on extensions to generate the real validation.

See the `inspector-compiler-extensions-api` for information on writing your own extension, or the 
`compiler-extensions` directory for first-party extensions.


[![Maven Central](https://img.shields.io/maven-central/v/io.sweers.inspector/inspector-compiler.svg)](https://mvnrepository.com/artifact/io.sweers.inspector/inspector-compiler)
```gradle
compileOnly 'io.sweers.inspector:inspector-compiler:x.y.z'
```
