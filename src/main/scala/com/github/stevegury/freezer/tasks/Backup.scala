package com.github.stevegury.freezer.tasks

import com.amazonaws.services.glacier.TreeHashGenerator._
import com.github.stevegury.freezer.Path.relativize
import com.github.stevegury.freezer._
import java.io.File
import scala.collection._
import scala.util.matching.Regex

class Backup(dir: File, root: File, cfg: Config, vault: Vault, reporter: String => Unit) {

  private[this] val rootStatusDir = statusDir(root)
  private[this] val exclusions = cfg.exclusions

  // Return (filenames, dirnames) in `dir` that aren't excluded by the regex
  // Also check that no parent directory has been excluded (in order to remove file when updating the exclusion)
  private[this] def filterSubfiles(dir: File, exclusions: Seq[Regex]): (Set[String], Set[String]) = {
    // return true is the filename match one of the exclusions
    def fltr(filename: String) = exclusions.exists {
      regex => regex.findFirstIn(filename) match {
        case Some(mtch) => mtch == filename
        case _ => false
      }
    }

    val isDirExcluded = relativize(root, dir).split("/").exists(fltr)
    if (!dir.exists() || isDirExcluded)
      (Set.empty, Set.empty)
    else {
      val list = dir.listFiles
      if (list == null)
        (Set.empty, Set.empty)
      else {
        val (files, dirs) = list.partition(_.isFile)
        (files.map(_.getName).filterNot(fltr).toSet, dirs.map(_.getName).filterNot(fltr).toSet)
      }
    }
  }

  // upload file to the vault and update its status files
  private[this] def upload(file: File, hash: String, relativePath: String) = {
    val archInfo = vault.upload(file, hash, relativePath)
    val archiveInfoFile = new File(rootStatusDir, relativePath)
    val parent = archiveInfoFile.getAbsoluteFile.getParentFile
    if (!parent.exists())
      parent.mkdirs()
    archInfo.save(archiveInfoFile)
    archInfo
  }

  /**
   * Scan the directory and its children, and upload/delete files based on their
   * content and based on the status archiveInfo file (present in the .freezer/status dir)
   * @return the number of actions taken
   */
  private[this] def loop(dir: File, statusDir: File): Int = {
    var action = 0

    val (files, subdirs) = filterSubfiles(dir, exclusions)
    val (statuses, statusSubdirs) = filterSubfiles(statusDir, Seq.empty)

    val newFiles = files -- statuses
    val deletedFiles = statuses -- files
    val otherFiles = files -- newFiles
    val nextDirs = subdirs ++ statusSubdirs

    newFiles.toSeq.sorted foreach { filename =>
      val f = new File(dir, filename)
      if (f.length() != 0) {
        val relativePath = relativize(root, f)
        val hash = calculateTreeHash(f)
        reporter(s"Uploading new file: $relativePath")
        upload(f, hash, relativePath)
        action += 1
      }
    }
    deletedFiles.toSeq.sorted foreach { statusFilename =>
      val statusFile = new File(statusDir, statusFilename)
      val relativePath = relativize(rootStatusDir, statusFile)
      val archiveInfo = ArchiveInfo.load(statusFile, relativePath)
      reporter(s"Removing deleted file: $relativePath")
      vault.deleteArchive(archiveInfo.archiveId)
      statusFile.delete()
      action += 1
    }
    otherFiles.toSeq.sorted foreach { case filename =>
      val file = new File(dir, filename)
      val relativePath = relativize(root, file)
      val archInfoPath = new File(statusDir, filename)
      val oldArchiveInfo = ArchiveInfo.load(archInfoPath, relativePath)
      if (file.length() == 0) {
        reporter(s"Removing zero-byte file: $relativePath")
        vault.deleteArchive(oldArchiveInfo.archiveId)
        archInfoPath.delete()
        action += 1
      } else {
        val hash = calculateTreeHash(file)
        if (hash != oldArchiveInfo.hash) {
          val relativePath = relativize(root, file)
          reporter(s"Updating modified file: $relativePath")
          val newArchiveInfo = vault.upload(file, hash, relativePath)
          newArchiveInfo.save(archInfoPath)
          vault.deleteArchive(oldArchiveInfo.archiveId)
          action += 1
        }
      }
    }

    for {
      dirName <- nextDirs.toSeq.sorted
      if !(dirName == configDirname && dir == root) // skip '.freezer' directory in root
      newDir = new File(dir, dirName)
      newStatusDir = new File(statusDir, dirName)
    } {
      action += loop(newDir, newStatusDir)
    }

    // Deleted empty status directories
    if (statusDir.exists() && statusDir.listFiles().isEmpty)
      statusDir.delete()
    action
  }

  def run(): Int = {
    if (! rootStatusDir.exists())
      rootStatusDir.mkdirs()

    if (loop(dir, rootStatusDir) == 0)
      reporter("Everything up-to-date.")
    0
  }
}
