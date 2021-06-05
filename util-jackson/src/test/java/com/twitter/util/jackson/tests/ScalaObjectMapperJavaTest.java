package com.twitter.util.jackson.tests;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

import org.junit.Assert;
import org.junit.Test;

import com.twitter.util.jackson.ScalaObjectMapper;

public class ScalaObjectMapperJavaTest extends Assert {

  /* Class under test */
  private final ScalaObjectMapper mapper = ScalaObjectMapper.apply();

  @Test
  public void testConstructors() {
    // new mapper with defaults
    Assert.assertNotNull(ScalaObjectMapper.apply());

    // augments the underlying mapper with the defaults
    Assert.assertNotNull(ScalaObjectMapper.apply(mapper.underlying()));

    // copies the underlying Jackson object mapper
    Assert.assertNotNull(ScalaObjectMapper.objectMapper(mapper.underlying()));
    try {
      ScalaObjectMapper.yamlObjectMapper(mapper.underlying());
      fail();
    } catch (IllegalArgumentException e) {
      // underlying mapper needs to have a JsonFactory of type YAMLFactory
    };
    Assert.assertNotNull(ScalaObjectMapper.yamlObjectMapper(ScalaObjectMapper.builder().yamlObjectMapper().underlying()));
    Assert.assertNotNull(ScalaObjectMapper.camelCaseObjectMapper(mapper.underlying()));
    Assert.assertNotNull(ScalaObjectMapper.snakeCaseObjectMapper(mapper.underlying()));
}

  @Test
  public void testBuilderConstructors() {
    Assert.assertNotNull(ScalaObjectMapper.builder().objectMapper());
    Assert.assertNotNull(ScalaObjectMapper.builder().objectMapper(new JsonFactory()));
    Assert.assertNotNull(ScalaObjectMapper.builder().objectMapper(new YAMLFactory()));

    Assert.assertNotNull(ScalaObjectMapper.builder().yamlObjectMapper());
    Assert.assertNotNull(ScalaObjectMapper.builder().camelCaseObjectMapper());
    Assert.assertNotNull(ScalaObjectMapper.builder().snakeCaseObjectMapper());
  }
}
