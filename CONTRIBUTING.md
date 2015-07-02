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

## Style

We generally follow [Effective Scala][es] and the [Scala Style Guide][ssg]. When
in doubt, look around the codebase and see how it's done elsewhere.

Comments should be formatted to a width no greater than 80 columns.

Files should be exempt of trailing spaces.

We adhere to a specific format for commit messages. Please write your commit
messages along these guidelines:

    One line description of your change (less than 72 characters)

    Problem

    Explain here the context, and why you're making that change.
    What is the problem you're trying to solve?

    Solution

    Describe the modifications you've done.

    Result

    After your change, what will change?

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
[funsuite]: http://www.scalatest.org/getting_started_with_fun_suite
[scalatest]: http://www.scalatest.org/
[ssg]: http://docs.scala-lang.org/style/scaladoc.html
[travis-ci]: https://travis-ci.org/twitter/util
