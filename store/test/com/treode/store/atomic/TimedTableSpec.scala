package com.treode.store.atomic

import java.nio.file.Paths

import com.treode.async.{AsyncTestTools, StubScheduler}
import com.treode.async.io.StubFile
import com.treode.disk.{Disks, DisksConfig, DiskGeometry}
import com.treode.store._
import org.scalatest.FreeSpec

import Cardinals._
import Fruits._
import TimedTable.keyToBytes
import TimedTestTools._

class TimedTableSpec extends FreeSpec {

  private def newTable(): (StubScheduler, TimedTable) = {
    implicit val scheduler = StubScheduler.random()
    implicit val disksConfig = DisksConfig (14, 1<<24, 1<<16, 10, 1)
    implicit val recovery = Disks.recover()
    val file = new StubFile
    val geometry = DiskGeometry (20, 12, 1<<30)
    val item = (Paths.get ("a"), file, geometry)
    val launch = recovery.attach (Seq (item)) .pass
    launch.launch()
    implicit val disks = launch.disks
    implicit val storeConfig = StoreConfig (8, 1<<20)
    val table = TimedTable()
    (scheduler, table)
  }

  def expectCells (cs: TimedCell*) (t: TimedTable) (implicit s: StubScheduler): Unit =
    expectResult (cs) (t.iterator.toSeq)

  "TimedTable.keyToBytes should" - {

    "preserve the sort of the embedded key" in {
      assert (Boysenberry < Grape)
      assert (Bytes (Bytes.pickler, Boysenberry) > Bytes (Bytes.pickler, Grape))
      assert (keyToBytes (Boysenberry, 0) < keyToBytes (Grape, 0))
    }

    "reverse the sort order of time" in {
      assert (keyToBytes (Grape, 1) < keyToBytes (Grape, 0))
    }}

  "When a TimedTable is empty, it should" - {

    "get Apple##0 for Apple##1" in {
      implicit val (s, t) = newTable()
      t.get (Apple, 1) expect (0::None)
    }

    "put Apple##1::One" in {
      implicit val (s, t) = newTable()
      t.put (Apple, 1, One)
      t.iterator.toSeq
      expectCells (Apple##1::One) (t)
    }}

  "When a TimedTable has Apple##7::One, it should " - {

    def newTableWithData() = {
      val (s, t) = newTable()
      t.put (Apple, 7, One)
      (s, t)
    }

    "find 7::One for Apple##8" in {
      implicit val (s, t) = newTableWithData()
      t.get (Apple, 8) expect (7::One)
    }

    "find 7::One for Apple##7" in {
      implicit val (s, t) = newTableWithData()
      t.get (Apple, 7) expect (7::One)
    }

    "find 0::None for Apple##6" in {
      implicit val (s, t) = newTableWithData()
      t.get (Apple, 6) expect (0::None)
    }

    "put 11::Two" in {
      implicit val (s, t) = newTableWithData()
      t.put (Apple, 11, Two)
      expectCells (Apple##11::Two, Apple##7::One) (t)
    }

    "put Apple##3::Two" in {
      implicit val (s, t) = newTableWithData()
      t.put (Apple, 3, Two)
      expectCells (Apple##7::One, Apple##3::Two) (t)
    }}

  "When a TimedTable has Apple##14::Two and Apple##7::One, it should" -  {

    def newTableWithData() = {
      val (s, t) = newTable()
      t.put (Apple, 7, One)
      t.put (Apple, 14, Two)
      (s, t)
    }

    "find 14::Two for Apple##15" in {
      implicit val (s, t) = newTableWithData()
      t.get (Apple, 15) expect (14::Two)
    }

    "find 14::Two for Apple##14" in {
      implicit val (s, t) = newTableWithData()
      t.get (Apple, 14) expect (14::Two)
    }

    "find 7::One for Apple##13" in {
      implicit val (s, t) = newTableWithData()
      t.get (Apple, 13) expect (7::One)
    }

    "find 7::One for Apple##8" in {
      implicit val (s, t) = newTableWithData()
      t.get (Apple, 8) expect (7::One)
    }

    "find 7::One for Apple##7" in {
      implicit val (s, t) = newTableWithData()
      t.get (Apple, 7) expect (7::One)
    }

    "find 0::None for Apple##6" in {
      implicit val (s, t) = newTableWithData()
      t.get (Apple, 6) expect (0::None)
    }}}