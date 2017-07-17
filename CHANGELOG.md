Changelog
=========

Version 0.2.0 (17/7/2017)
-------------------------

* New: Separate `inspector-factory-compiler-annotations` artifact for better android project compatibility (#11)
* Fix: AutoValue artifact should show up correctly now (#9)
* Fix: Use `t` for the type parameter in `Validator#validate()` so the IDE better autocompletes the name based on the actual type (#12)

Version 0.1.1 (17/7/2017)
-------------------------

* New: Lowered requirements around validator methods when generating factories (#7)
* New: Changed parameter names from `object` to `validationTarget` where possible for nicer kotlin autocompletes (#8)

Version 0.1.0 (16/7/2017)
-------------------------

* Initial release
