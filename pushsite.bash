#!/usr/bin/env bash

set -e

VERSION=2.12

dir=/tmp/util.$$
trap "rm -fr $dir" 0 1 2

echo 'making site...' 1>&2
./sbt --warn util-doc/makeSite

unidoc="target/scala-$VERSION/unidoc/"
rm -fr "$unidoc"

echo 'making unidoc...' 1>&2
./sbt --warn unidoc

echo 'cloning...' 1>&2
git clone -b gh-pages git@github.com:twitter/util.git $dir >/dev/null 2>&1

savedir=$(pwd)
cd $dir
touch .nojekyll
cp $savedir/site/index.html .
rsync -a --delete "$savedir/$unidoc" "docs"
rsync -a --delete "$savedir/doc/target/site/" "guide"
git add .
echo 'pushing...!' 1>&2
git diff-index --quiet HEAD || (git commit -am"site push by $(whoami)"; git push origin gh-pages:gh-pages;)
echo 'finished!' 1>&2
