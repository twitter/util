val b1 =
#include DerivedWithInclude.scala
val b2 =
#include DerivedWithInclude.scala
new Base {
  override def toString = b1 + "; " + b2
}
