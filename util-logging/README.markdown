# util-logging

Util-logging is a small wrapper around java's builtin logging to make it more
scala-friendly.

This library is released under the Apache Software License, version 2, which
should be included with the source in a file named `LICENSE`.


## Building

Use sbt (simple-build-tool) to build:

    $ sbt clean update package-dist

The finished jar will be in `dist/`.


## Using

To access logging, you can usually just use:

    import com.twitter.logging.Logger
    private val log = Logger.get(getClass)

This creates a `Logger` object that uses the current class or object's
package name as the logging node, so class "com.example.foo.Lamp" will log to
node "com.example.foo" (generally showing "foo" as the name in the logfile).
You can also get a logger explicitly by name:

    private val log = Logger.get("com.example.foo")

Logger objects wrap everything useful from `java.util.logging.Logger`, as well
as adding some convenience methods:

    // log a string with sprintf conversion:
    log.info("Starting compaction on zone %d...", zoneId)

    try {
      ...
    } catch {
      // log an exception backtrace with the message:
      case e: IOException =>
        log.error(e, "I/O exception: %s", e.getMessage)
    }

Each of the log levels (from "fatal" to "trace") has these two convenience
methods. You may also use `log` directly:

    log(Level.DEBUG, "Logging %s at debug level.", name)

An advantage to using sprintf ("%s", etc) conversion, as opposed to:

    log(Level.DEBUG, "Logging " + name + " at debug level.")

is that java & scala perform string concatenation at runtime, even if nothing
will be logged because the logfile isn't writing debug messages right now.
With sprintf parameters, the arguments are just bundled up and passed directly
to the logging level before formatting. If no log message would be written to
any file or device, then no formatting is done and the arguments are thrown
away. That makes it very inexpensive to include excessive debug logging which
can be turned off without recompiling and re-deploying.

If you prefer, there are also variants that take lazy-evaluated parameters,
and only evaluate them if logging is active at that level:

    log.ifDebug("Login from " + name + " at " + date + ".")

The logging classes are done as an extension to the `java.util.logging` API,
and so you can use the java interface directly, if you want to. Each of the
java classes (Logger, Handler, Formatter) is just wrapped by a scala class
with a cleaner interface.


## Configuring

In the java style, log nodes are in a tree, with the root node being "" (the
empty string). If a node has a filter level set, only log messages of that
priority or higher are passed up to the parent. Handlers are attached to nodes
for sending log messages to files or logging services, and may have formatters
attached to them.

Logging levels are, in priority order of highest to lowest:

- `FATAL` - the server is about to exit
- `CRITICAL` - an event occurred that is bad enough to warrant paging someone
- `ERROR` - a user-visible error occurred (though it may be limited in scope)
- `WARNING` - a coder may want to be notified, but the error was probably not
  user-visible
- `INFO` - normal informational messages
- `DEBUG` - coder-level debugging information
- `TRACE` - intensive debugging information

Each node may also optionally choose to *not* pass messages up to the parent
node.

The `LoggerConfig` builder is used to configure individual log nodes, by
filling in fields and calling the `apply` method. For example, to configure
the root logger to filter at `INFO` level and write to a file:

    import com.twitter.logging.config._

    val config = new LoggerConfig {
      node = ""
      level = Logger.INFO
      handlers = new FileHandlerConfig {
        filename = "/var/log/example/example.log"
        roll = Policy.SigHup
      }
    }
    config()

As many `LoggerConfig`s can be configured as you want, so you can attach to
several nodes if you like. To remove all previous configurations, use:

    Logger.clearHandlers()

The API docs for the config classes (in `LoggerConfig.scala`) are the
definitive documentation for the various logging settings. The rest of this
README describes the bundled handlers and formatters.


## Handlers

- `ConsoleHandlerConfig`

  Logs to the console.

- `FileHandlerConfig`

  Logs to a file, with an optional file rotation policy. The policies are:

  - `Policy.Never` - always use the same logfile (default)
  - `Policy.Hourly` - roll to a new logfile at the top of every hour
  - `Policy.Daily` - roll to a new logfile at midnight every night
  - `Policy.Weekly(n)` - roll to a new logfile at midnight on day N (0 = Sunday)
  - `Policy.SigHup` - reopen the logfile on SIGHUP (for logrotate and similar services)

  When a logfile is rolled, the current logfile is renamed to have the date
  (and hour, if rolling hourly) attached, and a new one is started. So, for
  example, `test.log` may become `test-20080425.log`, and `test.log` will be
  reopened as a new file.

- `SyslogHandlerConfig`

  Log to a syslog server, by host and port.

- `ScribeHandlerConfig`

  Log to a scribe server, by host, port, and category. Buffering and backoff
  can also be configured: You can specify how long to collect log lines
  before sending them in a single burst, the maximum burst size, and how long
  to backoff if the server seems to be offline.

- `ThrottledHandlerConfig`

  Wraps another handler, tracking (and squelching) duplicate messages. If you
  use a format string like `"Error %d at %s"`, the log messages will be
  de-duped based on the format string, even if they have different parameters.


## Formatters

Handlers usually have a formatter attached to them, and these formatters
generally just add a prefix containing the date, log level, and logger name.

- `BasicFormatterConfig`

  A standard log prefix like `"ERR [20080315-18:39:05.033] jobs: "`, which
  can be configured to truncate log lines to a certain length, limit the lines
  of an exception stack trace, and use a special time zone.

  You can override the format string used to generate the prefix, also.

- `BareFormatterConfig`

  No prefix at all. May be useful for logging info destined for scripts.

- `SyslogFormatterConfig`

  A formatter required by the syslog protocol, with configurable syslog
  priority and date format.
