package com.treode.disk

import java.nio.file.Paths

import com.treode.async.{AsyncConversions, AsyncTestTools, StubScheduler}
import com.treode.async.io.StubFile
import org.scalatest.FreeSpec

import AsyncConversions._
import DiskTestTools._

class RecoverySpec extends FreeSpec {

  implicit val config = DisksConfig (0, 8, 1<<10, 100, 3, 1)
  val geom = DiskGeometry (10, 4, 1<<20)
  val record = RecordDescriptor (0x1BF6DBABE6A70060L, DiskPicklers.int)

  "Recovery.replay should" - {

    "allow registration of a record descriptor" in {
      implicit val scheduler = StubScheduler.random()
      val recovery = Disks.recover()
      recovery.replay (record) (_ => ())
    }

    "reject double registration of a record descriptor" in {
      implicit val scheduler = StubScheduler.random()
      val recovery = Disks.recover()
      recovery.replay (record) (_ => ())
      intercept [IllegalArgumentException] {
        recovery.replay (record) (_ => ())
      }}

    "reject registration of a record descriptor after attach" in {
      implicit val scheduler = StubScheduler.random()
      val file = new StubFile
      val recovery = Disks.recover()
      recovery.attachAndWait (("a", file, geom)) .pass
      intercept [IllegalArgumentException] {
        recovery.replay (record) (_ => ())
      }}

    "reject registration of a record descriptor after reattach" in {
      implicit val scheduler = StubScheduler.random()
      val file = new StubFile
      var recovery = Disks.recover()
      recovery.attachAndLaunch (("a", file, geom))
      recovery = Disks.recover()
      recovery.reattachAndLaunch (("a", file))
      intercept [IllegalArgumentException] {
        recovery.replay (record) (_ => ())
      }}}

  "Recovery.attach should" - {

    "allow attaching an item" in {
      implicit val scheduler = StubScheduler.random()
      val file = new StubFile
      val recovery = Disks.recover()
      recovery.attachAndLaunch (("a", file, geom))
    }

    "allow attaching multiple items" in {
      implicit val scheduler = StubScheduler.random()
      val file1 = new StubFile
      val file2 = new StubFile
      val recovery = Disks.recover()
      recovery.attachAndLaunch (("a", file1, geom), ("b", file2, geom))
    }

    "reject attaching no items" in {
      implicit val scheduler = StubScheduler.random()
      val recovery = Disks.recover()
      recovery.attachAndWait() .fail [IllegalArgumentException]
    }

    "reject attaching the same path multiply" in {
      implicit val scheduler = StubScheduler.random()
      val file = new StubFile
      val recovery = Disks.recover()
      recovery
          .attachAndWait (("a", file, geom), ("a", file, geom))
          .fail [IllegalArgumentException]
    }

    "pass through an exception from DiskDrive.init" in {
      implicit val scheduler = StubScheduler.random()
      val file = new StubFile
      val recovery = Disks.recover()
      file.stop = true
      val cb = recovery.attachAndCapture (("a", file, geom))
      scheduler.runTasks()
      file.stop = false
      while (file.hasLast)
        file.last.fail (new Exception)
      scheduler.runTasks()
      cb.failed [Exception]
    }}

  "Recovery.reattach" - {

    "in general should" - {

      "allow reattaching an item" in {
        implicit val scheduler = StubScheduler.random()
        val file = new StubFile
        var recovery = Disks.recover()
        recovery.attachAndLaunch (("a", file, geom))
        recovery = Disks.recover()
        recovery.reattachAndLaunch (("a", file))
      }

      "allow reattaching multiple items" in {
        implicit val scheduler = StubScheduler.random()
        val file1 = new StubFile
        val file2 = new StubFile
        var recovery = Disks.recover()
        recovery.attachAndLaunch (("a", file1, geom), ("b", file2, geom))
        recovery = Disks.recover()
        recovery.reattachAndLaunch (("a", file1), ("b", file2))
      }

      "reject reattaching no items" in {
        implicit val scheduler = StubScheduler.random()
        val recovery = Disks.recover()
        recovery.reattachAndWait() .fail [IllegalArgumentException]
      }

      "reject reattaching the same path multiply" in {
        implicit val scheduler = StubScheduler.random()
        val file = new StubFile
        var recovery = Disks.recover()
        recovery.attachAndLaunch (("a", file, geom))
        recovery = Disks.recover()
        recovery
            .reattachAndWait (("a", file), ("a", file))
            .fail [IllegalArgumentException]
      }

      "pass through an exception from chooseSuperBlock" in {
        implicit val scheduler = StubScheduler.random()
        val file = new StubFile
        val recovery = Disks.recover()
        recovery.reattachAndWait (("a", file)) .fail [NoSuperBlocksException]
      }

      "pass through an exception from verifyReattachment" in {
        implicit val scheduler = StubScheduler.random()
        val file = new StubFile
        var recovery = Disks.recover()
        recovery.attachAndLaunch (("a", file, geom))
        val config2 = DisksConfig (1, 8, 1<<10, 100, 3, 1)
        recovery = Disks.recover () (scheduler, config2)
        recovery.reattachAndWait (("a", file)) .fail [CellMismatchException]
      }}

    "when given opened files should" - {

      "require the given file paths match the boot blocks disks" in {
        implicit val scheduler = StubScheduler.random()
        val file = new StubFile
        val file2 = new StubFile
        var recovery = Disks.recover()
        recovery.attachAndLaunch (("a", file, geom), ("b", file2, geom))
        recovery = Disks.recover()
        recovery.reattachAndWait (("a", file)) .fail [MissingDisksException]
      }}

    "when given unopened paths should" - {

      val path1 = Paths.get ("a")
      val path2 = Paths.get ("b")
      val path3 = Paths.get ("c")

      "pass when given all of the items" in {
        implicit val scheduler = StubScheduler.random()
        val file = new StubFile
        val file2 = new StubFile
        var recovery = Disks.recover() .asInstanceOf [RecoveryAgent]
        recovery.attachAndLaunch (("a", file, geom), ("b", file2, geom))
        recovery = Disks.recover() .asInstanceOf [RecoveryAgent]
        recovery._reattach (Seq (path1, path2)) {
          case `path1` => SuperBlocks.read (path1, file)
          case `path2` => SuperBlocks.read (path2, file2)
        } .pass
      }

      "pass when given a subset of the items" in {
        implicit val scheduler = StubScheduler.random()
        val file = new StubFile
        val file2 = new StubFile
        var recovery = Disks.recover() .asInstanceOf [RecoveryAgent]
        recovery.attach (Seq ((path1, file, geom), (path2, file2, geom))) .pass
        recovery = Disks.recover() .asInstanceOf [RecoveryAgent]
        recovery._reattach (Seq (path1)) {
          case `path1` => SuperBlocks.read (path1, file)
          case `path2` => SuperBlocks.read (path2, file2)
        } .pass
      }

      "fail when given extra uninitialized items" in {
        implicit val scheduler = StubScheduler.random()
        val file = new StubFile
        val file2 = new StubFile
        val file3 = new StubFile
        var recovery = Disks.recover() .asInstanceOf [RecoveryAgent]
        recovery.attach (Seq ((path1, file, geom), (path2, file2, geom))) .pass
        recovery = Disks.recover() .asInstanceOf [RecoveryAgent]
        recovery._reattach (Seq (path1, path3)) {
          case `path1` => SuperBlocks.read (path1, file)
          case `path2` => SuperBlocks.read (path2, file2)
          case `path3` => SuperBlocks.read (path3, file3)
        } .fail [InconsistentSuperBlocksException]
      }

      "fail when given extra initialized items" in {
        implicit val scheduler = StubScheduler.random()
        val file = new StubFile
        val file2 = new StubFile
        val file3 = new StubFile
        var recovery = Disks.recover() .asInstanceOf [RecoveryAgent]
        recovery.attach (Seq ((path1, file, geom), (path2, file2, geom))) .pass
        recovery = Disks.recover() .asInstanceOf [RecoveryAgent]
        recovery.attach (Seq ((path3, file3, geom))) .pass
        recovery = Disks.recover() .asInstanceOf [RecoveryAgent]
        recovery._reattach (Seq (path1, path3)) {
          case `path1` => SuperBlocks.read (path1, file)
          case `path2` => SuperBlocks.read (path2, file2)
          case `path3` => SuperBlocks.read (path3, file3)
        } .fail [ExtraDisksException]
      }}}}