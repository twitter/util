#!/bin/bash

set -e

dir=/tmp/util.$$
trap "rm -fr $dir" 0 1 2

echo 'making site...' 1>&2
./sbt util-doc/make-site >/dev/null 2>&1

unidoc=target/scala-2.11/unidoc/
rm -fr "$unidoc"

echo 'making unidoc...' 1>&2
./sbt unidoc >/dev/null 2>&1

echo 'cloning...' 1>&2
git clone -b gh-pages git@github.com:twitter/util.git $dir >/dev/null 2>&1

savedir=$(pwd)
cd $dir
cp $savedir/site/index.html .
rsync -a --delete "$savedir/$unidoc" "docs"
rsync -a --delete "$savedir/doc/target/site" "guide"
git add .
echo 'pushing...!' 1>&2
git diff-index --quiet HEAD || (git commit -am"site push by $(whoami)"; git push origin gh-pages:gh-pages;)
echo 'finished!' 1>&2
