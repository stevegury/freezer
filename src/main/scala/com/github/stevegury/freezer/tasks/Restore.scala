package com.github.stevegury.freezer.tasks

import java.io.File

import com.github.stevegury.freezer.Vault

class Restore(dir: File, root: File, vault: Vault, now: Boolean, reporter: String => Unit) {
  def run(): Int = {
    // TODO: handle partial restore
    require(dir.getAbsolutePath == root.getAbsolutePath)

    vault.getInventory(now) match {
      case Right(archiveInfos) =>
        val archInfos = archiveInfos.sortBy(_.path)
        vault.download(dir, archInfos, reporter)
        0
      case Left(jobId) =>
        reporter(s"Inventory in progress (JobID: '$jobId')")
        1
    }
  }
}
