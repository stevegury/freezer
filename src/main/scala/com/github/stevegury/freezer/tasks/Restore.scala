package com.github.stevegury.freezer.tasks

import java.io.File

import com.github.stevegury.freezer.Vault

class Restore(vault: Vault) {
  def run(): Int = {
//    val root = new File(cfg.path).getParentFile
//
//    val newCfg = client.getVault(vaultName) match {
//      case Some(vault) => vault.getInventory match {
//        case Right(archiveInfos) =>
//          vault.download(root, archiveInfos)
//          cfg.copy(archiveInfos = archiveInfos)
//        case Left(jobId) =>
//          println("Restore requested but not yet ready")
//          println("(please redo this command later)")
//          cfg
//      }
//      case _ =>
//        println("Can't find vault '%s'".format(vaultName))
//        cfg
//    }
//    newCfg.copy(vaultName = vaultName)
//    newCfg.save()
    0
  }
}
