# How to Contribute

We'd love to get patches from you!

## Building

This library is built using [sbt][sbt]. When building please use the included
[`./sbt`](https://github.com/twitter/util/blob/develop/sbt) script which
provides a thin wrapper over [sbt][sbt] and correctly sets memory and other
settings.

If you have any questions or run into any problems, please create
an issue here, chat with us in [gitter](https://gitter.im/twitter/finagle), or email
the Finaglers [mailing list](https://groups.google.com/forum/#!forum/finaglers).

## Workflow

The workflow that we support:

1.  Fork util
1.  Check out the `develop` branch
1.  Make a feature branch (use `git checkout -b "cool-new-feature"`)
1.  Make your cool new feature or bugfix on your branch
1.  Write a test for your change
1.  From your branch, make a pull request against `twitter/util/develop`
1.  Work with repo maintainers to get your change reviewed
1.  Wait for your change to be pulled into `twitter/util/develop`
1.  Merge `twitter/util/develop` into your origin `develop`
1.  Delete your feature branch

## Checklist

There are a number of things we like to see in pull requests. Depending
on the scope of your change, there may not be many to take care of, but
please scan this list and see which apply. It's okay if something is missed;
the maintainers will help out during code review.

1. Include [tests](CONTRIBUTING.md#testing).
1. Update the [changelog][changes] for new features, API breakages, runtime behavior changes,
   deprecations, and bug fixes.
1. All public APIs should have [Scaladoc][scaladoc].
1. When adding a constructor to an existing class or arguments to an existing
   method, in order to preserve backwards compatibility for Java users, avoid
   Scala's default arguments. Instead use explicit forwarding methods.
1. The second argument of an `@deprecated` annotation should be the current
   date, in `YYYY-MM-DD` form.

## Testing

We've standardized on using the [ScalaTest testing framework][scalatest].
Because ScalaTest has such a big surface area, we use a restricted subset of it
in our tests to keep them easy to read.  We've chosen the `assert` API, not the
`Matchers` one, and we use the [`FunSuite` mixin][funsuite], which supports
xUnit-like semantics.

We encourage our contributors to ensure Java compatibility for any new public APIs
they introduce. The easiest way to do so is to provide _Java compilation tests_
and make sure the new API is easily accessible (typing `X$.MODULE$` is not easy)
from Java. These compilation tests also provide Java users with testable examples
of the API usage. For an example of a Java compilation test see
[VarCompilationTest.java][1].

Note that while you will see a [Travis CI][travis-ci] status message in your
pull request, this may not always be accurate, and in any case all changes will
be tested internally at Twitter before being merged. We're working to make
Travis CI more useful for development, but for now you don't need to worry if
it's failing (assuming that you are able to build and test your changes
locally).

### Property-based testing

When appropriate, use [ScalaCheck][scalacheck] to write property-based
tests for your code. This will often produce more thorough and effective
inputs for your tests. We use ScalaTest's
[ScalaCheckDrivenPropertyChecks][gendrivenprop] as the entry point for
writing these tests.

## Compatibility

We try to keep public APIs stable for the obvious reasons. Often,
compatibility can be kept by adding a forwarding method. Note that we
avoid adding default arguments because this is not a compatible change
for our Java users.  However, when the benefits outweigh the costs, we
are willing to break APIs. The break should be noted in the Breaking
API Changes section of the [changelog][changes]. Note that changes to
non-public APIs will not be called out in the [changelog][changes].

## Java

While the project is written in Scala, its public APIs should be usable from
Java. This occasionally works out naturally from the Scala interop, but more
often than not, if care is not taken Java users will have rough corners
(e.g. `SomeCompanion$.MODULE$.someMethod()` or a symbolic operator).
We take a variety of approaches to minimize this.

1. Add a "compilation" unit test, written in Java, that verifies the APIs are
   usable from Java.
1. If there is anything gnarly, we add Java adapters either by adding
   a non-symbolic method name or by adding a class that does forwarding.
1. Prefer `abstract` classes over `traits` as they are easier for Java
   developers to extend.

## Style

We generally follow [Effective Scala][es] and the [Scala Style Guide][ssg]. When
in doubt, look around the codebase and see how it's done elsewhere.

Please note that any additions or changes to the API must be thoroughly
described in [Scaladoc][sd] comments. We will also happily consider pull
requests that improve the existing Scaladocs!

## Issues

When creating an issue please try to ahere to the following format:

    module-name: One line summary of the issue (less than 72 characters)

    ### Expected behavior

    As concisely as possible, describe the expected behavior.

    ### Actual behavior

    As concisely as possible, describe the observed behavior.

    ### Steps to reproduce the behavior

    List all relevant steps to reproduce the observed behavior.

## Pull Requests

Comments should be formatted to a width no greater than 80 columns.

Files should be exempt of trailing spaces.

We adhere to a specific format for commit messages. Please write your commit
messages along these guidelines. Please keep the line width no greater than
80 columns (You can use `fmt -n -p -w 80` to accomplish this).

    module-name: One line description of your change (less than 72 characters)

    Problem

    Explain the context and why you're making that change.  What is the
    problem you're trying to solve? In some cases there is not a problem
    and this can be thought of being the motivation for your change.

    Solution

    Describe the modifications you've done.

    Result

    What will change as a result of your pull request? Note that sometimes
    this section is unnecessary because it is self-explanatory based on
    the solution.

## Code Review

The Util repository on GitHub is kept in sync with an internal repository at
Twitter. For the most part this process should be transparent to Util users,
but it does have some implications for how pull requests are merged into the
codebase.

When you submit a pull request on GitHub, it will be reviewed by the
Util community (both inside and outside of Twitter), and once the changes are
approved, your commits will be brought into the internal system for additional
testing. Once the changes are merged internally, they will be pushed back to
GitHub with the next release.

This process means that the pull request will not be merged in the usual way.
Instead a member of the Util team will post a message in the pull request
thread when your changes have made their way back to GitHub, and the pull
request will be closed (see [this pull request][0] for an example). The changes
in the pull request will be collapsed into a single commit, but the authorship
metadata will be preserved.

Please let us know if you have any questions about this process!

[0]: https://github.com/twitter/util/pull/109
[1]: https://github.com/twitter/util/blob/release/util-core/src/test/java/com/twitter/util/VarCompilationTest.java
[changes]: https://github.com/twitter/util/blob/develop/CHANGELOG.rst
[es]: https://twitter.github.io/effectivescala/
[funsuite]: https://www.scalatest.org/getting_started_with_fun_suite
[gendrivenprop]: https://www.scalatest.org/user_guide/generator_driven_property_checks
[sbt]: https://www.scala-sbt.org/
[scalacheck]: https://www.scalacheck.org/
[scaladoc]: https://docs.scala-lang.org/style/scaladoc.html
[scalatest]: https://www.scalatest.org/
[sd]: https://docs.scala-lang.org/style/scaladoc.html
[ssg]: https://docs.scala-lang.org/style/scaladoc.html
[travis-ci]: https://travis-ci.org/twitter/util
