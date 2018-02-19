package io.sweers.inspector.sample

import io.sweers.inspector.ValidationException
import io.sweers.inspector.Validator
import java.util.Date

class SecondaryDateValidator : Validator<Date>() {
  @Throws(ValidationException::class)
  override fun validate(date: Date) {

  }
}
