package com.twitter.util

import java.io.{OutputStream, InputStream}
import com.twitter.util.StorageUnitConversions._

object StreamHelper {
  val defaultBufferSize = 24.kilobytes.inBytes.toInt

  implicit def inputStreamToRichInputStream(inputStream: InputStream) = new {
    def writeTo(outputStream: OutputStream) {
      val buffer = new Array[Byte](defaultBufferSize)
      while (inputStream.available > 0) {
        val length = inputStream.read(buffer)
        outputStream.write(buffer, 0, length)
      }
    }
  }
}