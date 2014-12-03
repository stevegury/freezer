package com.github.stevegury.freezer.tasks

import com.github.stevegury.freezer.Vault

class Inventory(vault: Vault) {
  def run(): Int = {
    vault.getInventory match {
      case Right(archiveInfos) =>
        archiveInfos.sortBy(_.desc) foreach { info => println(info.desc) }
      case Left(jobId) =>
        println("Inventory in progress (JobID: '%s')".format(jobId))
    }
    0
  }
}
