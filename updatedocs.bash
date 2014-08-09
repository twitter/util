#!/bin/bash

set -e

dir=/tmp/util.$$
trap "rm -fr $dir" 0 1 2

unidoc=target/scala-2.10/unidoc/
rm -fr "$unidoc"

echo 'making unidoc...' 1>&2
./sbt unidoc >/dev/null 2>&1

echo 'cloning...' 1>&2
git clone -b gh-pages git@github.com:twitter/util.git $dir >/dev/null 2>&1

savedir=$(pwd)
cd $dir
rsync -a --delete "$savedir/$unidoc" "docs"
git add .
echo 'pushing...!' 1>&2
git diff-index --quiet HEAD || (git commit -am"site push by $(whoami)"; git push origin gh-pages:gh-pages;)
echo 'finished!' 1>&2
