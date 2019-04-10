package com.twitter

package object app {
  object PackageObjectTest extends GlobalFlag[Boolean](true, "a package object test flag")
}
