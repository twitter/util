new (() => String) {
  @deprecated("don't use hello")
  def hello() = "hello"

  def apply() = hello()
}
