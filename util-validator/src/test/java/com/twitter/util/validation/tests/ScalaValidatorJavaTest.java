package com.twitter.util.validation.tests;

import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.Assert;
import org.junit.Test;

import com.twitter.util.validation.ScalaValidator;
import com.twitter.util.validation.cfg.ConstraintMapping;
import com.twitter.util.validation.constraints.ValidPassengerCount;

import jakarta.validation.ConstraintViolation;

public class ScalaValidatorJavaTest extends Assert {
  private final ConstraintMapping mapping =
      ConstraintMapping.apply(
          ValidPassengerCount.class,
          ValidPassengerCountConstraintValidator.class, false
      );

  private final   ScalaValidator validator =
      ScalaValidator.builder()
          .withConstraintMapping(mapping)
          .validator();

  @Test
  public void testConstructors() {
    Assert.assertNotNull(ScalaValidator.apply());
    Assert.assertNotNull(ScalaValidator.builder().validator());
  }

  @Test
  public void testBuilder() {
    Assert.assertNotNull(ScalaValidator.builder().validator());

    Set<ConstraintMapping> mappings = new HashSet<>();
    mappings.add(mapping);

    Assert.assertNotNull(
        ScalaValidator.builder()
            .withConstraintMapping(mapping)
            .validator()
    );

    Assert.assertNotNull(
        ScalaValidator.builder()
            .withConstraintMappings(mappings)
            .validator()
    );
  }

  @Test
  public void testUnderlyingValidator() {
    Car car = new Car("Renault", "DD-AB-123", 4);

    Set<ConstraintViolation<Car>> violations = validator.underlying().validate(car);
    Assert.assertEquals(violations.size(), 0);

    List<Person> passengers = new ArrayList<Person>();
    passengers.add(new Person("R Smith"));
    passengers.add(new Person("S Smith"));
    passengers.add(new Person("T Smith"));
    passengers.add(new Person("U Smith"));

    car = new Car("Renault", "DD-AB-123", 2);
    car.load(passengers);

    violations = validator.underlying().validate(car);
    Assert.assertEquals(violations.size(), 1);
  }

  @Test
  public void testValidateFieldValue() {
    Map<String, Object> sizeConstraintValues = new HashMap<>();
    sizeConstraintValues.put("min", 5);
    sizeConstraintValues.put("max", 7);

    Map<Class<? extends Annotation>, Map<String, Object>> constraints = new HashMap<>();
    constraints.put(jakarta.validation.constraints.Size.class, sizeConstraintValues);

    Set<ConstraintViolation<Object>> violations =
        validator.validateFieldValue(
            constraints,
            "javaSetIntField",
            newSet(6)
        );

    Assert.assertEquals(violations.size(), 0);

    Set<Integer> value = newSet(9);
    violations =
        validator.validateFieldValue(
            constraints,
            "javaSetIntField",
            value
        );

    Assert.assertEquals(violations.size(), 1);
    ConstraintViolation<Object> violation = violations.iterator().next();
    Assert.assertEquals(violation.getPropertyPath().toString(), "javaSetIntField");
    Assert.assertEquals(violation.getMessage(), "size must be between 5 and 7");
    Assert.assertEquals(violation.getInvalidValue(), value);
  }

  @Test
  public void testValidateFieldValueWithGroups() {
    Class<?>[] groups = {TestConstraintGroup.class};
    Map<String, Object> sizeConstraintValues = new HashMap<>();
    sizeConstraintValues.put("min", 5);
    sizeConstraintValues.put("max", 7);
    sizeConstraintValues.put("groups", groups);

    Map<Class<? extends Annotation>, Map<String, Object>> constraints = new HashMap<>();
    constraints.put(jakarta.validation.constraints.Size.class, sizeConstraintValues);

    Set<Integer> value = newSet(9);
    // TestConstraintGroup is not active
    Set<ConstraintViolation<Object>> violations =
        validator.validateFieldValue(
            constraints,
            "javaSetIntField",
            value
        );
    Assert.assertEquals(violations.size(), 0);

    // TestConstraintGroup is active
    violations =
        validator.validateFieldValue(
            constraints,
            "javaSetIntField",
            value,
            TestConstraintGroup.class
        );
    ConstraintViolation<Object> violation = violations.iterator().next();
    Assert.assertEquals(violation.getPropertyPath().toString(), "javaSetIntField");
    Assert.assertEquals(violation.getMessage(), "size must be between 5 and 7");
    Assert.assertEquals(violation.getInvalidValue(), value);
  }

  public Set<Integer> newSet(Integer total) {
    Set<Integer> results = new HashSet<>();
    for (int i = 0; i < total; i++) {
      results.add(i);
    }

    return results;
  }
}
