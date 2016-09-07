# How to Contribute

We'd love to get patches from you!

## Workflow

The workflow that we support:

1.  Fork util
2.  Check out the `develop` branch
3.  Make a feature branch (use `git checkout -b "cool-new-feature"`)
4.  Make your cool new feature or bugfix on your branch
5.  Write a test for your change
6.  From your branch, make a pull request against `twitter/util/develop`
7.  Work with repo maintainers to get your change reviewed
8.  Wait for your change to be pulled into `twitter/util/develop`
9.  Merge `twitter/util/develop` into your origin `develop`
10.  Delete your feature branch

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

## Compatibility

We try to keep public APIs stable for the obvious reasons. Often,
compatibility can be kept by adding a forwarding method. Note that we
avoid adding default arguments because this is not a compatible change
for our Java users.  However, when the benefits outweigh the costs, we
are willing to break APIs. The break should be noted in the Breaking
API Changes section of the [changelog](CHANGES). Note that changes to
non-public APIs will not be called out in the [changelog](CHANGES).

## Java

While the project is written in Scala, its public APIs should be usable from
Java. This occasionally works out naturally from the Scala interop, but more
often than not, if care is not taken Java users will have rough corners
(e.g. `SomeCompanion$.MODULE$.someMethod()` or a symbolic operator).
We take a variety of approaches to minimize this.

1. Add a "compilation" unit test, written in Java, that verifies the APIs are
   usable from Java.
2. If there is anything gnarly, we add Java adapters either by adding
   a non-symbolic method name or by adding a class that does forwarding.
3. Prefer `abstract` classes over `traits` as they are easier for Java
   developers to extend.

## Style

We generally follow [Effective Scala][es] and the [Scala Style Guide][ssg]. When
in doubt, look around the codebase and see how it's done elsewhere.

Please note that any additions or changes to the API must be thoroughly
described in [Scaladoc][sd] comments. We will also happily consider pull
requests that improve the existing Scaladocs!

## Issues

When creating an issue please try to ahere to the following format:

    One line summary of the issue (less than 72 characters)

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

    One line description of your change (less than 72 characters)

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
[1]: https://github.com/twitter/util/blob/master/util-core/src/test/java/com/twitter/util/VarCompilationTest.java
[es]: https://twitter.github.io/effectivescala/
[sd]: http://docs.scala-lang.org/style/scaladoc.html
[funsuite]: http://www.scalatest.org/getting_started_with_fun_suite
[scalatest]: http://www.scalatest.org/
[ssg]: http://docs.scala-lang.org/style/scaladoc.html
[travis-ci]: https://travis-ci.org/twitter/util
