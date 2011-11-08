package com.twitter.io

import org.specs.Specification
import com.twitter.util.TempFolder
import java.io.File

class FilesSpec extends Specification with TempFolder {
  noDetailedDiffs()

  "Files" should {

    "delete" in withTempFolder {
      val tempFolder = new File(canonicalFolderName)

      val file = new File(tempFolder, "file")
      file.createNewFile() must beTrue

      val folder = new File(tempFolder, "folder")
      folder.mkdir() must beTrue

      val subfile = new File(folder, "file-in-folder")
      subfile.createNewFile() must beTrue

      Files.delete(tempFolder) must beTrue
      Seq(file, subfile, folder, tempFolder).foreach { _.exists must beFalse }
    }

  }

}
