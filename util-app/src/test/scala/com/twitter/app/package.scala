package com.twitter

package object app {
  object packageObjectTest extends GlobalFlag[Boolean](true, "a package object test flag")
}
