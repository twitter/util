package com.twitter.io


import com.twitter.util.TempFolder
import java.io.File
import org.scalatest.WordSpec
import org.scalatest.matchers.ShouldMatchers
import org.junit.runner.RunWith
import org.scalatest.junit.JUnitRunner

@RunWith(classOf[JUnitRunner])
class FilesTest extends WordSpec with ShouldMatchers with TempFolder {
  "Files" should {

    "delete" in withTempFolder {
      val tempFolder = new File(canonicalFolderName)

      val file = new File(tempFolder, "file")
      file.createNewFile() shouldEqual true

      val folder = new File(tempFolder, "folder")
      folder.mkdir() shouldEqual true

      val subfile = new File(folder, "file-in-folder")
      subfile.createNewFile() shouldEqual true

      Files.delete(tempFolder) shouldEqual true
      Seq(file, subfile, folder, tempFolder).foreach { _.exists shouldEqual false }
    }

  }

}
