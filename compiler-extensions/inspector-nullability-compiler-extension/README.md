inspector-nullability-compiler-extension
========================================

An inspector compiler extension that generates nullability validation by checking for any `@Nullable`
 annotation. Everything is assumed not-null by default. For obvious reasons, this does not work on primitive
 or `Void` types.
 
```gradle
compileOnly 'io.sweers.inspector:inspector-nullability-compiler-extension:x.y.z'
```
