package com.twitter.io


import java.io.File

import org.junit.runner.RunWith
import org.scalatest.WordSpec
import org.scalatest.junit.JUnitRunner

import com.twitter.util.TempFolder

@RunWith(classOf[JUnitRunner])
class FilesTest extends WordSpec with TempFolder {
  "Files" should {

    "delete" in withTempFolder {
      val tempFolder = new File(canonicalFolderName)

      val file = new File(tempFolder, "file")
      assert(file.createNewFile() == true)

      val folder = new File(tempFolder, "folder")
      assert(folder.mkdir() == true)

      val subfile = new File(folder, "file-in-folder")
      assert(subfile.createNewFile() == true)

      assert(Files.delete(tempFolder) == true)
      Seq(file, subfile, folder, tempFolder).foreach { x => assert(!x.exists) }
    }

  }

}
