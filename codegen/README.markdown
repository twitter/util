# Code Generators

This directory contains templates and executables for generating Scala code. We
normally need to do this when defining a function or method that accepts a
variable number of type parameters. For example, we may want to define a `zip`
function for tuples of arbitrary size:

    def zip[A, B](a: Seq[A], b: Seq[B]): Seq[(A, B)]
    def zip[A, B, C](a: Seq[A], b: Seq[B], c: Seq[C]): Seq[(A, B, C)]
    def zip[A, B, C, D] // and so on...

There isn't a native way to do this sort of thing in pure Scala (hence the
existence of Tuple1 through Tuple22, Function1...22, etc.).

## Usage

A Makefile exists which can build the code-generated files. To (re-)generate a
file:

    make ../util-something/path/to/File.scala

The Mako Python templating library is used by some targets, and it can be
installed with `pip`:

    pip install -r requirements.txt
