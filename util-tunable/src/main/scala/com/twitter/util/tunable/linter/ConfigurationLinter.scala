package com.twitter.util.tunable.linter

import com.twitter.app.App
import com.twitter.util.{Throw, Return}
import com.twitter.util.tunable.JsonTunableMapper
import java.net.URL

object ConfigurationLinter extends App {

  def main() {
    var allSucceeded = true

    println("\nValidating TunableMap configuration files...")
    println("------------------------------------------------------------")

    args.foreach { path =>
      println(s"File: $path\n")

      val url = new URL("file://" + path)

      JsonTunableMapper().parse(url) match {
        case Return(map) =>
          println(s"Parsed as: ${map.contentString}")
        case Throw(exc) =>
          allSucceeded = false
          println(s"Exception occurred: $exc")
      }
      println("------------------------------------------------------------")
    }

    if (allSucceeded) {
      println("All configurations valid!")
    } else {
      exitOnError("One or more configurations failed to be parsed, see above for exceptions.")
    }
  }
}
