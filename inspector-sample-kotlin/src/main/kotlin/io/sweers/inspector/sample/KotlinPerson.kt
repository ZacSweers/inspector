package io.sweers.inspector.sample

import android.support.annotation.IntDef
import android.support.annotation.IntRange
import android.support.annotation.Size
import android.support.annotation.StringDef
import com.uber.rave.annotation.MustBeFalse
import com.uber.rave.annotation.MustBeTrue
import io.sweers.inspector.InspectorIgnored
import io.sweers.inspector.ValidatedBy
import io.sweers.inspector.compiler.annotations.GenerateValidator
import java.util.Date

const val FOO = "foo"
const val FOO2 = "foo2"
const val FOO_INT = 0L

@StringDef(FOO, FOO2)
annotation class StringDefChecked

@IntDef(FOO_INT)
annotation class IntDefChecked

@GenerateValidator
data class Person<T, V>(
    val firstName: String,
    val lastName: String,
    val favoriteNumbers: IntArray,
    val aList: List<String>,
    val aMap: Map<String, String>,
    val favoriteFoods: Set<String>,
    @StringDefChecked
    val stringDefChecked: String,
    @IntDefChecked
    val intDefChecked: Int,
    @IntRange(from = 0)
    val age: Int,
    val occupation: String?,
    @ValidatedBy(value = [DateValidator::class, SecondaryDateValidator::class])
    val birthday: Date,
    @InspectorIgnored
    val uuid: String,
    @Size(multiple = 2)
    val doublesOfStrings: List<String>,
    @Size(3)
    val threePairs: Map<String, String>,
    @Size(min = 3)
    val atLeastThreeStrings: Set<String>,
    @Size(max = 3)
    val atMostThreeStrings: Set<String>,
    val genericOne: T,
    val genericTwo: V
) {

  @MustBeTrue
  fun checkMustBeTrue(): Boolean {
    return true
  }

  @MustBeFalse
  fun checkMustBeFalse(): Boolean {
    return false
  }
}
