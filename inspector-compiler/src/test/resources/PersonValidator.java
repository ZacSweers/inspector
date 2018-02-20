package io.sweers.inspector.sample;

import io.sweers.inspector.Inspector;
import io.sweers.inspector.Types;
import io.sweers.inspector.ValidationException;
import io.sweers.inspector.Validator;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class PersonValidator extends Validator<Person> {
  private final Validator<String> firstNameValidator;

  private final Validator<String> lastNameValidator;

  private final Validator<int[]> favoriteNumbersValidator;

  private final Validator<List<String>> aListValidator;

  private final Validator<Map<String, String>> aMapValidator;

  private final Validator<Set<String>> favoriteFoodsValidator;

  private final Validator<String> stringDefCheckedValidator;

  private final Validator<Integer> intDefCheckedValidator;

  private final Validator<Integer> ageValidator;

  private final Validator<String> occupationValidator;

  private final Validator<Date> birthdayValidator;

  private final Validator<List<String>> doublesOfStringsValidator;

  private final Validator<Map<String, String>> threePairsValidator;

  private final Validator<Set<String>> atLeastThreeStringsValidator;

  private final Validator<Set<String>> atMostThreeStringsValidator;

  private final Validator<Boolean> checkMustBeTrueValidator;

  private final Validator<Boolean> checkMustBeFalseValidator;

  public PersonValidator(Inspector inspector) {
    this.firstNameValidator = inspector.validator(String.class);
    this.lastNameValidator = inspector.validator(String.class);
    this.favoriteNumbersValidator = inspector.validator(int[].class);
    this.aListValidator = inspector.validator(Types.newParameterizedType(List.class, String.class));
    this.aMapValidator = inspector.validator(Types.newParameterizedType(Map.class, String.class, String.class));
    this.favoriteFoodsValidator = inspector.validator(Types.newParameterizedType(Set.class, String.class));
    this.stringDefCheckedValidator = inspector.validator(String.class);
    this.intDefCheckedValidator = inspector.validator(int.class);
    this.ageValidator = inspector.validator(int.class);
    this.occupationValidator = inspector.validator(String.class);
    this.birthdayValidator = new DateValidator();
    this.doublesOfStringsValidator = inspector.validator(Types.newParameterizedType(List.class, String.class));
    this.threePairsValidator = inspector.validator(Types.newParameterizedType(Map.class, String.class, String.class));
    this.atLeastThreeStringsValidator = inspector.validator(Types.newParameterizedType(Set.class, String.class));
    this.atMostThreeStringsValidator = inspector.validator(Types.newParameterizedType(Set.class, String.class));
    this.checkMustBeTrueValidator = inspector.validator(boolean.class);
    this.checkMustBeFalseValidator = inspector.validator(boolean.class);
  }

  @Override
  public void validate(Person value) throws ValidationException {
    // Begin validation for "firstName()"
    String firstName = value.firstName();

    // Validations contributed by "NullabilityInspectorExtension"
    if (firstName == null) {
      throw new ValidationException("firstName() is not nullable but returns a null");
    }
    firstNameValidator.validate(firstName);

    // Begin validation for "lastName()"
    String lastName = value.lastName();

    // Validations contributed by "NullabilityInspectorExtension"
    if (lastName == null) {
      throw new ValidationException("lastName() is not nullable but returns a null");
    }
    lastNameValidator.validate(lastName);

    // Begin validation for "favoriteNumbers()"
    int[] favoriteNumbers = value.favoriteNumbers();

    // Validations contributed by "NullabilityInspectorExtension"
    if (favoriteNumbers == null) {
      throw new ValidationException("favoriteNumbers() is not nullable but returns a null");
    }
    favoriteNumbersValidator.validate(favoriteNumbers);

    // Begin validation for "aList()"
    List<String> aList = value.aList();

    // Validations contributed by "NullabilityInspectorExtension"
    if (aList == null) {
      throw new ValidationException("aList() is not nullable but returns a null");
    }
    aListValidator.validate(aList);

    // Begin validation for "aMap()"
    Map<String, String> aMap = value.aMap();

    // Validations contributed by "NullabilityInspectorExtension"
    if (aMap == null) {
      throw new ValidationException("aMap() is not nullable but returns a null");
    }
    aMapValidator.validate(aMap);

    // Begin validation for "favoriteFoods()"
    Set<String> favoriteFoods = value.favoriteFoods();

    // Validations contributed by "NullabilityInspectorExtension"
    if (favoriteFoods == null) {
      throw new ValidationException("favoriteFoods() is not nullable but returns a null");
    }
    favoriteFoodsValidator.validate(favoriteFoods);

    // Begin validation for "stringDefChecked()"
    String stringDefChecked = value.stringDefChecked();

    // Validations contributed by "NullabilityInspectorExtension"
    if (stringDefChecked == null) {
      throw new ValidationException("stringDefChecked() is not nullable but returns a null");
    }
    // Validations contributed by "AndroidInspectorExtension"
    if (!("foo".equals(stringDefChecked) && "foo2".equals(stringDefChecked))) {
      throw new ValidationException("stringDefChecked's value must be within scope of its StringDef. Is " + stringDefChecked);
    }
    stringDefCheckedValidator.validate(stringDefChecked);

    // Begin validation for "intDefChecked()"
    int intDefChecked = value.intDefChecked();

    // Validations contributed by "AndroidInspectorExtension"
    if (!(intDefChecked != 0)) {
      throw new ValidationException("intDefChecked's value must be within scope of its IntDef. Is " + intDefChecked);
    }
    intDefCheckedValidator.validate(intDefChecked);

    // Begin validation for "age()"
    int age = value.age();

    // Validations contributed by "AndroidInspectorExtension"
    if (age < 0) {
      throw new ValidationException("age must be greater than 0 but is " + age);
    }
    ageValidator.validate(age);

    // Begin validation for "occupation()"
    String occupation = value.occupation();

    occupationValidator.validate(occupation);

    // Begin validation for "doublesOfStrings()"
    List<String> doublesOfStrings = value.doublesOfStrings();

    // Validations contributed by "NullabilityInspectorExtension"
    if (doublesOfStrings == null) {
      throw new ValidationException("doublesOfStrings() is not nullable but returns a null");
    }
    // Validations contributed by "AndroidInspectorExtension"
    int doublesOfStringsSize = doublesOfStrings.size();
    if (doublesOfStringsSize % 2 != 0) {
      throw new ValidationException("doublesOfStrings's size must be a multiple of 2 but is " + doublesOfStringsSize);
    }
    doublesOfStringsValidator.validate(doublesOfStrings);

    // Begin validation for "threePairs()"
    Map<String, String> threePairs = value.threePairs();

    // Validations contributed by "NullabilityInspectorExtension"
    if (threePairs == null) {
      throw new ValidationException("threePairs() is not nullable but returns a null");
    }
    // Validations contributed by "AndroidInspectorExtension"
    int threePairsSize = threePairs.size();
    if (threePairsSize != 3) {
      throw new ValidationException("threePairs's size must be exactly 3 but is " + threePairsSize);
    }
    threePairsValidator.validate(threePairs);

    // Begin validation for "atLeastThreeStrings()"
    Set<String> atLeastThreeStrings = value.atLeastThreeStrings();

    // Validations contributed by "NullabilityInspectorExtension"
    if (atLeastThreeStrings == null) {
      throw new ValidationException("atLeastThreeStrings() is not nullable but returns a null");
    }
    // Validations contributed by "AndroidInspectorExtension"
    int atLeastThreeStringsSize = atLeastThreeStrings.size();
    if (atLeastThreeStringsSize < 3) {
      throw new ValidationException("atLeastThreeStrings's size must be greater than 3 but is " + atLeastThreeStringsSize);
    }
    atLeastThreeStringsValidator.validate(atLeastThreeStrings);

    // Begin validation for "atMostThreeStrings()"
    Set<String> atMostThreeStrings = value.atMostThreeStrings();

    // Validations contributed by "NullabilityInspectorExtension"
    if (atMostThreeStrings == null) {
      throw new ValidationException("atMostThreeStrings() is not nullable but returns a null");
    }
    // Validations contributed by "AndroidInspectorExtension"
    int atMostThreeStringsSize = atMostThreeStrings.size();
    if (atMostThreeStringsSize > 3) {
      throw new ValidationException("atMostThreeStrings's size must be less than 3 but is " + atMostThreeStringsSize);
    }
    atMostThreeStringsValidator.validate(atMostThreeStrings);

    // Begin validation for "checkMustBeTrue()"
    boolean checkMustBeTrue = value.checkMustBeTrue();

    // Validations contributed by "RaveInspectorExtension"
    if (!value.checkMustBeTrue()) {
      throw new ValidationException("checkMustBeTrue must be true but is false");
    }
    checkMustBeTrueValidator.validate(checkMustBeTrue);

    // Begin validation for "checkMustBeFalse()"
    boolean checkMustBeFalse = value.checkMustBeFalse();

    // Validations contributed by "RaveInspectorExtension"
    if (value.checkMustBeFalse()) {
      throw new ValidationException("checkMustBeFalse must be false but is true");
    }
    checkMustBeFalseValidator.validate(checkMustBeFalse);

  }
}
