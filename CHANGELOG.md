Changelog
=========

Version 0.3.0
-------------------------

_2018-2-12_

This is a rather huge release with a lot of updates. It shouldn't be anything compile-breaking though
hopefully!

* Composite validation support via `CompositeValidator` and allowing multiple validators in `@ValidatedBy`.
* Fleshed out sample with more examples
  * Generics support
  * Composite validation
  * Retrofit example. Drop this in to your retrofit instance to validate server responses!
* A *lot* of generics improvements
  * Prior to this release, reflective generics were unfortunately broken
* Support for self validation. If a type implements `SelfValidating`, the reflective validator factory
and compiler will just defer to their validation logic.
  * The factory compiler will ignore these types completely.
* AutoValue compiler extension no longer has a hard transitive dependency on AutoValue.
* New artifact (`inspector-compiler-annotations`) with a `@GenerateValidator` annotation. Use this 
annotation to indicate to the compiler that you want it to generate the validator regardless of whether
or not there's a static `Validator`-returning method. 
  * This comes with an optional `GenerateValidator.FACTORY` Validator factory you can use to do runtime
  lookups of the generated Validators. This can be a good alternative solution in multimodule projects, 
  where registering multiple factories to upstream Inspector instances can be problematic.
* The factory compiler supports 0-arg static `Validator`-returning methods, for types that bring their
own validation.

Version 0.2.0
-------------------------

_2017-7-17_

* New: Separate `inspector-factory-compiler-annotations` artifact for better android project compatibility (#11)
* Fix: AutoValue artifact should show up correctly now (#9)
* Fix: Use `t` for the type parameter in `Validator#validate()` so the IDE better autocompletes the name based on the actual type (#12)

Version 0.1.1
-------------------------

_2017-7-17_

* New: Lowered requirements around validator methods when generating factories (#7)
* New: Changed parameter names from `object` to `validationTarget` where possible for nicer kotlin autocompletes (#8)

Version 0.1.0
-------------------------

_2017-7-16_

* Initial release
