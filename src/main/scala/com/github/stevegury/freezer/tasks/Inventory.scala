package com.github.stevegury.freezer.tasks

import com.github.stevegury.freezer.Vault

class Inventory(vault: Vault, reporter: String => Unit) {
  def run(): Int = {
    vault.getInventory match {
      case Right(archiveInfos) =>
        archiveInfos.sortBy(_.path) foreach {
          info => reporter(info.path)
        }
      case Left(jobId) =>
        reporter(s"Inventory in progress (JobID: '$jobId')")
    }
    0
  }
}
