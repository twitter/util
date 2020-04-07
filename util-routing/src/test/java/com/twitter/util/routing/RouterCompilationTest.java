package com.twitter.util.routing;

import org.junit.Assert;
import org.junit.Test;

import scala.collection.Iterable;

import java.util.Map;
import java.util.HashMap;
import java.util.Optional;

import static com.twitter.util.Function.exfunc;
import static com.twitter.util.Function.func;

public class RouterCompilationTest {

  private static final class StringRoute {
    public final String input;
    public final String output;

    StringRoute(String input, String output) {
      this.input = input;
      this.output = output;
    }
  }

  private static final class StringRouter extends AbstractRouter<String, StringRoute> {
    static StringRouterBuilder newBuilder() { return new StringRouterBuilder(); }

    private final Map<String, StringRoute> routeMap;

    public StringRouter(Map<String, StringRoute> routeMap) {
      super("strings", routeMap.values());
      this.routeMap = routeMap;
    }

    @Override
    public Optional<StringRoute> findAny(String input) {
      StringRoute found = routeMap.get(input);
      return Optional.ofNullable(found);
    }

  }

  private static final class StringRouterBuilder
      extends RouterBuilder<String, StringRoute, StringRouter> {

    @Override
    public StringRouter newRouter(Iterable<StringRoute> routes) {
      Map<String, StringRoute> routeMap = new HashMap<>();
      routes.foreach(func(r -> routeMap.put(r.input, r)));
      return new StringRouter(routeMap);
    }

  }

  private static final class ValidatingStringRouterBuilder
      extends RouterBuilder<String, StringRoute, StringRouter> {

    ValidatingStringRouterBuilder() {
      super(exfunc(r -> {
        if (r.input.equals("invalid")) throw new RouteValidationException("INVALID!");
        return null;
      }));
    }

    @Override
    public StringRouter newRouter(Iterable<StringRoute> routes) {
      Map<String, StringRoute> routeMap = new HashMap<>();
      routes.foreach(func(r -> routeMap.put(r.input, r)));
      return new StringRouter(routeMap);
    }

  }

  @Test
  public void testRouter() {
    StringRoute hello = new StringRoute("hello", "abc");
    StringRoute goodbye = new StringRoute("goodbye", "123");

    Map<String, StringRoute> routes = new HashMap<>();
    routes.put(hello.input, hello);
    routes.put(goodbye.input, goodbye);

    AbstractRouter<String, StringRoute> router = new StringRouter(routes);
    Assert.assertEquals(router.route("hello"), Optional.of(hello));
    Assert.assertEquals(router.route("goodbye"), Optional.of(goodbye));
    Assert.assertEquals(router.route("oh-no"), Optional.empty());
  }

  @Test
  public void testRouterBuilder() {
    StringRoute hello = new StringRoute("hello", "abc");
    StringRoute goodbye = new StringRoute("goodbye", "123");

    AbstractRouter<String, StringRoute> router = StringRouter.newBuilder()
        .withRoute(hello)
        .withRoute(goodbye)
        .newRouter();

    Assert.assertEquals(router.route("hello"), Optional.of(hello));
    Assert.assertEquals(router.route("goodbye"), Optional.of(goodbye));
    Assert.assertEquals(router.route("oh-no"), Optional.empty());
  }

  @Test(expected = RouteValidationException.class)
  public void testValidatingRouterBuilder() {
    new ValidatingStringRouterBuilder()
        .withRoute(new StringRoute("invalid", "other"));
  }

}
