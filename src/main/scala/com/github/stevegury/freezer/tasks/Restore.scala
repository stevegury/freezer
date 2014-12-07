package com.github.stevegury.freezer.tasks

import java.io.File

import com.amazonaws.services.glacier.TreeHashGenerator._
import com.github.stevegury.freezer._
import com.github.stevegury.freezer.Vault

class Restore(dir: File, root: File, vault: Vault, now: Boolean, reporter: String => Unit) {
  require(dir.getAbsolutePath == root.getAbsolutePath)
  private[this] val statusRoot = statusDir(root)

  def run(): Int = {
    // TODO: handle partial restore

    vault.getInventory(now) match {
      case Right(archiveInfos) =>
        download(archiveInfos)
      case Left(jobId) =>
        reporter(s"Inventory in progress (JobID: '$jobId')")
        1
    }
  }

  private[this] def download(archiveInfos: Seq[ArchiveInfo]): Int = {
    val allJobId = vault.listArchiveRetrievalJobs filter {
      job => archiveInfos.map(_.archiveId).contains(job.getArchiveId)
    }
    val lastestJobs = (allJobId.groupBy(_.getJobDescription) map {
      case (path, descs) if descs.size == 1 => descs.head
      // if multiple archive retrievals are available for the same file, select the latest
      case (path, descs) => descs.sortBy(_.getCreationDate).last
    }).toSeq
    val newJobs = archiveInfos filterNot { archiveInfo =>
      lastestJobs.exists(_.getArchiveId == archiveInfo.archiveId)
    }
    val (completed, inProgress) = lastestJobs partition { _.getCompleted }

    newJobs.sortBy(_.path) foreach { info =>
      val jobId = vault.requestDownload(info.archiveId, info.path)
      reporter(s"Requesting download for: '${info.path}'")
      jobId
    }
    inProgress.map(_.getJobDescription).sorted foreach { path =>
      reporter(s"Server still preparing: '$path'")
    }
    completed.sortBy(_.getJobDescription) foreach { jobDesc =>
      val path = jobDesc.getJobDescription
      val output = new File(root, path)

      val skipDownload =
        if (output.exists() && output.length() > 0) {
          val hash = jobDesc.getArchiveSHA256TreeHash
          val computedHash = calculateTreeHash(output)
          hash == computedHash
        } else
          false

      if (skipDownload)
        reporter(s"Skipping '$path' (already present and identical hash)")
      else {
        reporter(s"Downloading '$path'...")
        vault.downloadToFile(jobDesc.getJobId, output)
        val archiveInfo = ArchiveInfo(
          jobDesc.getArchiveId,
          jobDesc.getArchiveSHA256TreeHash,
          path
        )
        archiveInfo.save(new File(statusRoot, path))
      }
    }
    0
  }
}
