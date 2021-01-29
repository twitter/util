package com.twitter.util.routing;

import org.junit.Assert;
import org.junit.Test;

import java.util.Map;
import java.util.HashMap;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

public class RouterCompilationTest {

  private static final class StringRoute {
    public final String input;
    public final String output;

    StringRoute(String input, String output) {
      this.input = input;
      this.output = output;
    }
  }

  private static final class StringRouterGenerator extends Generator<String, StringRoute, StringRouter> {
    private static final StringRouterGenerator instance = new StringRouterGenerator();
    static final StringRouterGenerator getInstance() {
      return instance;
    }

    private StringRouterGenerator() {}

    @Override
    public StringRouter apply(RouterInfo<StringRoute> routerInfo) {
      Map<String, StringRoute> routeMap = new HashMap<>();
      routerInfo.jRoutes().forEach(r -> routeMap.put(r.input, r));
      return new StringRouter(routeMap);
    }
  }

  private static final class StringRouter extends Router<String, StringRoute> {
    static RouterBuilder<String, StringRoute, StringRouter> newBuilder() {
      return RouterBuilder.newBuilder(StringRouterGenerator.getInstance());
    }

    private final Map<String, StringRoute> routeMap;

    public StringRouter(Map<String, StringRoute> routeMap) {
      super("strings", routeMap.values());
      this.routeMap = routeMap;
    }

    @Override
    public Result find(String input) {
      StringRoute found = routeMap.get(input);
      if (found == null) return NotFound.get();
      return Found.apply(input, found);
    }

  }

  private static final class ValidatingStringRouterBuilder {
    static RouterBuilder<String, StringRoute, StringRouter> newBuilder() {
      return StringRouter.newBuilder().withValidator(Validator.create(routes ->
          StreamSupport.stream(routes.spliterator(), false)
            .filter(r -> r.input.equals("invalid"))
            .map(msg -> new ValidationError("INVALID!"))
            .collect(Collectors.toList())));
    }
  }

  @Test
  public void testRouter() {
    StringRoute hello = new StringRoute("hello", "abc");
    StringRoute goodbye = new StringRoute("goodbye", "123");

    Map<String, StringRoute> routes = new HashMap<>();
    routes.put(hello.input, hello);
    routes.put(goodbye.input, goodbye);

    Router<String, StringRoute> router = new StringRouter(routes);
    Assert.assertEquals(router.apply("hello"), Found.apply("hello", hello));
    Assert.assertEquals(router.apply("goodbye"), Found.apply("goodbye", goodbye));
    Assert.assertEquals(router.apply("oh-no"), NotFound.get());
  }

  @Test
  public void testRouterBuilder() {
    StringRoute hello = new StringRoute("hello", "abc");
    StringRoute goodbye = new StringRoute("goodbye", "123");

    Router<String, StringRoute> router = StringRouter.newBuilder()
        .withRoute(hello)
        .withRoute(goodbye)
        .newRouter();

    Assert.assertEquals(router.apply("hello"), Found.apply("hello", hello));
    Assert.assertEquals(router.apply("goodbye"), Found.apply("goodbye", goodbye));
    Assert.assertEquals(router.apply("oh-no"), NotFound.get());
  }

  @Test(expected = ValidationException.class)
  public void testValidatingRouterBuilder() {
    ValidatingStringRouterBuilder.newBuilder()
        .withRoute(new StringRoute("invalid", "other"))
        .newRouter();
  }

}
