# Twitter Util SLF4J API Support

This library provides a Scala-friendly logging wrapper around the [slf4j-api](https://www.slf4j.org/) [Logger](https://www.slf4j.org/apidocs/org/slf4j/Logger.html) interface.

## Differences with `util-logging`

The code in `util-logging` is a scala wrapper over `java.util.logging` (JUL) which is a logging API **and** implementation. `util-slf4j-api` is an API-only and adds a thin scala wrapper over the [slf4j-api](https://www.slf4j.org/) which allows
users to choose their logging implementation. It is recommended that users move to using `util-slf4j-api` over `util-logging`.

Since the [slf4j-api](https://www.slf4j.org/manual.html) is only an interface it requires an actual logging implementation. You should ensure that you do not end-up with *multiple* logging implementations on your classpath, e.g., you should not have multiple SLF4J bindings (`slf4j-nop`, `slf4j-log4j12`, `slf4j-jdk14`, etc.) and/or a `java.util.logging` implementation, etc. on your classpath as these are all competing implementations and since classpath order is non-deterministic, can lead to unexpected logging behavior.

## Usages

You can create a `c.t.util.logging.Logger` in different ways. You can pass a `name` to the `Logger#apply` factory method on the `c.t.util.logging.Logger` companion object:

```scala
val logger = Logger("name")
```

Or, pass in a class:

```scala
val logger = Logger(classOf[MyClass])
```

Or, use the runtime class wrapped by the implicit ClassTag parameter:

```scala
val logger = Logger[MyClass]
```

Or, finally, use an SLF4J Logger instance:

```scala
val logger = Logger(LoggerFactory.getLogger("name"))
```

There is also the `c.t.util.logging.Logging` trait which can be mixed into a class. Note, that the underlying SLF4J logger will be named according to the leaf class of any hierarchy into which the trait is mixed:

```scala
class Stock(symbol: String, price: BigDecimal) extends Logging {
  info(f"New stock with symbol = $symbol and price = $price%1.2f")
  ...
}
```

This would produce a logging statement along the lines of:

```
2017-01-05 13:05:49,349 INF Stock                     New stock with symbol = ASDF and price = 100.00
```

If you are using Java and happen to extend a Scala class which mixes in the `c.t.util.logging.Logging` trait, you can either access the logger directly or choose to instantiate a new logger in your
Java class to log your statements. Again, note that the underlying SLF4J logger when mixed in via the trait will be named according to the leaf class into which it is mixed.

E.g., for a Scala class:

```
class MyBaseScalaClass extends Logging {
  ...
}
```

You could choose to use the inherited logger directly since the inherited `c.t.util.logging.Logging` trait methods expect an anonymous function:

```
class MyJavaClass extends MyBaseScalaClass {
  ...

  public void methodThatDoesSomeLogging() {
    logger().info("Accessing the superclass logger");
  }
}
```

Or simply instantiate a new logger:

```
class MyJavaClass extends MyBaseScalaClass {
  private static final Logger logger = new Logger(MyJavaClass.class);

  ...

  public void methodThatDoesSomeLogging() {
    logger.info("Accessing the superclass logger");
  }
}
```

For another Scala class that extends the above example Scala class with the mixed-in `c.t.util.logging.Logging` trait, you have the same options along with:
  - simply use the inherited Logging trait methods.
  - extend the `c.t.util.logging.Logging` trait again.
