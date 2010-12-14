package com.twitter.util

import java.io.{OutputStream, InputStream}
import com.twitter.util.StorageUnitConversions._

object StreamHelper {
  implicit def inputStreamToRichInputStream(inputStream: InputStream) = new {
    def writeTo(outputStream: OutputStream, bufferSize: Int = 24.kilobytes.inBytes.toInt) {
      val buffer = new Array[Byte](bufferSize)
      while (inputStream.available > 0) {
        val length = inputStream.read(buffer)
        outputStream.write(buffer, 0, length)
      }
    }
  }
}