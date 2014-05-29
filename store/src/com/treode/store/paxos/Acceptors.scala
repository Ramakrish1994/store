package com.treode.store.paxos

import com.treode.async.{Async, Latch}
import com.treode.async.implicits._
import com.treode.async.misc.materialize
import com.treode.disk.{Disk, ObjectId, PageDescriptor, PageHandler, Position, RecordDescriptor}
import com.treode.store.{Bytes, Cell, Residents, TxClock}
import com.treode.store.tier.{TierDescriptor, TierTable}

import Async.{guard, latch, supply}
import PaxosKit.locator

private class Acceptors (kit: PaxosKit) extends PageHandler [Long] {
  import kit.{archive, cluster, disks, library}
  import kit.library.atlas

  val acceptors = newAcceptorsMap

  def get (key: Bytes, time: TxClock): Acceptor = {
    var a0 = acceptors.get ((key, time))
    if (a0 != null)
      return a0
    val a1 = new Acceptor (key, time, kit)
    a0 = acceptors.putIfAbsent ((key, time), a1)
    if (a0 != null) {
      a1.dispose()
      return a0
    }
    a1
  }

  def recover (key: Bytes, time: TxClock, a: Acceptor): Unit =
    acceptors.put ((key, time), a)

  def remove (key: Bytes, time: TxClock, a: Acceptor): Unit =
    acceptors.remove ((key, time), a)

  def receive (cells: Seq [Cell]) {
    cells foreach {
      case Cell (key, time, Some (chosen)) =>
        val a = acceptors.get ((key, time))
        if (a != null)
          a.choose (chosen)
      case _ => ()
    }}

  def probe (obj: ObjectId, groups: Set [Long]): Async [Set [Long]] =
    guard {
      archive.probe (groups)
    }

  def compact (obj: ObjectId, groups: Set [Long]): Async [Unit] =
    guard {
      val residents = library.residents
      for {
        meta <- archive.compact (groups, residents)
        _ <- Acceptors.checkpoint.record (meta)
      } yield ()
    }

  def checkpoint(): Async [Unit] =
    guard {

      val residents = library.residents

      val _archive = for {
        meta <- archive.checkpoint (residents)
        _ <- Acceptors.checkpoint.record (meta)
      } yield ()

      val _acceptors = for {
        a <- materialize (acceptors.values) .latch.unit
      } {
        a.checkpoint()
      }

      latch (_archive, _acceptors) .map (_ => ())
    }

  def attach () (implicit launch: Disk.Launch) {
    import Acceptor.{choose, propose, query}

    launch.checkpoint (checkpoint())

    Acceptors.archive.handle (this)

    query.listen { case ((version, key, time, ballot, default), c) =>
      if (atlas.version - 1 <= version && version <= atlas.version + 1)
        get (key, time) query (c, ballot, default)
    }

    propose.listen { case ((version, key, time, ballot, value), c) =>
      if (atlas.version - 1 <= version && version <= atlas.version + 1)
        get (key, time) propose (c, ballot, value)
    }

    choose.listen { case ((key, time, chosen), c) =>
      get (key, time) choose (chosen)
    }}}

private object Acceptors {

  val receive = {
    import PaxosPicklers._
    RecordDescriptor (0x4DCE11AA, tuple (ulong, seq (cell)))
  }

  val checkpoint = {
    import PaxosPicklers._
    RecordDescriptor (0x42A17DC354412E17L, tierMeta)
  }

  val archive = TierDescriptor (0x9F59C4262C8190E8L) { (residents, _, cell) =>
    residents contains (PaxosKit.locator, (cell.key, cell.time))
  }}
