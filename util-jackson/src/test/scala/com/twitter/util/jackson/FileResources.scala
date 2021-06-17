package com.twitter.util.jackson

import com.twitter.io.TempFolder
import java.io.{FileOutputStream, OutputStream, File => JFile}
import java.nio.charset.StandardCharsets
import java.nio.file.{FileSystems, Files}

trait FileResources extends TempFolder {

  protected def writeStringToFile(
    directory: String,
    name: String,
    ext: String,
    data: String
  ): JFile = {
    val file = Files.createTempFile(FileSystems.getDefault.getPath(directory), name, ext).toFile
    try {
      val out: OutputStream = new FileOutputStream(file, false)
      out.write(data.getBytes(StandardCharsets.UTF_8))
      file
    } finally {
      file.deleteOnExit()
    }
  }

  protected def addSlash(directory: String): String = {
    if (directory.endsWith("/")) directory else s"$directory/"
  }
}
