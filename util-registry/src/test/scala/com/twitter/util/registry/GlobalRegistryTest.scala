package com.twitter.util.registry

class GlobalRegistryTest extends RegistryTest {
  def mkRegistry(): Registry = GlobalRegistry.withRegistry(new SimpleRegistry) {
    GlobalRegistry.get
  }
  def name: String = "GlobalRegistry"

  val unique = Seq("__flibberty", "__gibbert", "__warbly", "$$parkour")

  private[this] def find(haystack: Iterable[Entry], needle: Seq[String]): Option[Entry] =
    haystack.find({ case Entry(key, value) => key == needle })

  test(s"$name can write, swap registry, and then read the old write") {
    val naive = new SimpleRegistry
    GlobalRegistry.get.put(unique, "foo")
    GlobalRegistry.withRegistry(naive) {
      assert(find(GlobalRegistry.get, unique).isEmpty)
      GlobalRegistry.get.put(unique, "bar")
    }
    assert(find(GlobalRegistry.get, unique) == Some(Entry(unique, "foo")))
  }
}
