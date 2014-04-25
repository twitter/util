package com.twitter.io


import com.twitter.util.TempFolder
import java.io.File
import org.scalatest.{WordSpec, Matchers}

class FilesSpec extends WordSpec with Matchers with TempFolder {
  "Files" should  {

    "delete" in withTempFolder {
      val tempFolder = new File(canonicalFolderName)

      val file = new File(tempFolder, "file")
      file.createNewFile() shouldBe true

      val folder = new File(tempFolder, "folder")
      folder.mkdir() shouldBe true

      val subfile = new File(folder, "file-in-folder")
      subfile.createNewFile() shouldBe true

      Files.delete(tempFolder) shouldBe true
      Seq(file, subfile, folder, tempFolder).foreach { _.exists shouldBe false }
    }

  }

}
