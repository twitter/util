package com.twitter.util

import com.twitter.util.validation.{MethodValidation, ScalaValidator}
import com.twitter.util.validation.engine.MethodValidationResult
import jakarta.validation.ValidationException
import jakarta.validation.constraints.{Min, NotEmpty}
import org.openjdk.jmh.annotations.{Benchmark, Scope, State}
import scala.util.control.NonFatal

private object ValidationBenchmark {
  case class User(@NotEmpty id: String, name: String, @Min(18) age: Int)

  case class Users(@NotEmpty users: Seq[User]) {
    @MethodValidation
    def uniqueUsers: MethodValidationResult =
      MethodValidationResult.validIfTrue(
        users.map(_.id).distinct.size == users.size,
        "user ids are not distinct.")
  }
}

// ./sbt 'project util-benchmark' 'jmh:run ValidationBenchmark'
@State(Scope.Benchmark)
class ValidationBenchmark extends StdBenchAnnotations {
  import ValidationBenchmark._

  private[this] val validator: ScalaValidator = ScalaValidator()
  private[this] val validUser: User = User("1234567", "jack", 21)
  private[this] val invalidUser: User = User("", "notJack", 13)
  private[this] val nestedValidUser: Users = Users(Seq(validUser))
  private[this] val nestedInvalidUser: Users = Users(Seq(invalidUser))
  private[this] val nestedDuplicateUser: Users = Users(Seq(validUser, validUser))

  @Benchmark
  def withValidUser(): Unit = {
    validator.verify(validUser)
  }

  @Benchmark
  def withInvalidUser(): Unit = {
    try {
      validator.verify(invalidUser)
    } catch {
      case _: ValidationException => // avoid throwing exceptions so the benchmark can finish
    }
  }

  @Benchmark
  def withNestedValidUser(): Unit = {
    validator.verify(nestedValidUser)
  }

  @Benchmark
  def withNestedInvalidUser(): Unit = {
    try {
      validator.verify(nestedInvalidUser)
    } catch {
      case _: ValidationException => // avoid throwing exceptions so the benchmark can finish
    }
  }

  @Benchmark
  def withNestedDuplicateUser(): Unit = {
    try {
      validator.verify(nestedDuplicateUser)
    } catch {
      case _: ValidationException => // avoid throwing exceptions so the benchmark can finish
    }
  }

  override def finalize(): Unit = {
    try {
      validator.close()
    } catch {
      case NonFatal(_) => // do nothing
    }
    super.finalize()
  }
}
