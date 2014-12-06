package com.github.stevegury.freezer.tasks

import com.github.stevegury.freezer.Vault

class Inventory(vault: Vault, now: Boolean, reporter: String => Unit) {
  def run(): Int = {
    vault.getInventory(now) match {
      case Right(archiveInfos) =>
        archiveInfos.sortBy(_.path) foreach { info => reporter(info.path) }
        0
      case Left(jobId) =>
        reporter(s"Inventory in progress (JobID: '$jobId')")
        1
    }
  }
}
