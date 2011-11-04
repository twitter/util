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

    "delete does not follow symlink contents" in withTempFolder {
      /** NOTE this'll only work on unix-like systems (linux, osx, solaris)
       * In java7 we'd just use Files.createSymbolicLink() */
      def createSymlink(target: File, link: File) {
        val process = Runtime.getRuntime.exec("ln -s %s %s".format(target.getCanonicalPath, link.getCanonicalPath))
        val exitCode = process.waitFor()
        exitCode must beEqual(0)
      }

      val tempFolder = new File(canonicalFolderName)

      // create a folder with a file in it
      val folder = new File(tempFolder, "folder")
      folder.mkdir() must beTrue
      val file = new File(folder, "fileInFolder")
      file.createNewFile() must beTrue

      val folderForSymlinks = new File(tempFolder, "folder2")
      folderForSymlinks.mkdir() must beTrue

      // create a symlink to a file in the other folder
      val symlinkToFile = new File(folderForSymlinks, "symlinktofile")
      symlinkToFile.exists must beFalse
      createSymlink(file, symlinkToFile)
      symlinkToFile.exists must beTrue

      // create a symlink to the folder
      val symlinkToFolder = new File(folderForSymlinks, "symlinktofolder")
      symlinkToFolder.exists must beFalse
      createSymlink(folder, symlinkToFolder)
      symlinkToFolder.exists must beTrue

      // now delete the symlinks folder and verify thats gone but not what they pointed to
      Files.delete(folderForSymlinks) must beTrue
      folderForSymlinks.exists must beFalse

      folder.exists must beTrue
      file.exists must beTrue
    }

  }

}
