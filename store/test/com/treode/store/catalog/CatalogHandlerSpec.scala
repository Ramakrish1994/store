package com.treode.store.catalog

import scala.collection.JavaConversions._

import org.scalatest.FreeSpec
import com.treode.cluster.MailboxId
import com.treode.pickle.{Pickler, Picklers}
import com.treode.store.Bytes

class CatalogHandlerSpec extends FreeSpec {

  val ID = MailboxId (0x26)

  val values = Seq (
      0x292C28335A06E344L, 0xB58E76CED969A4C7L, 0xDF20D7F2B8C33B9EL, 0x63D5DAAF0C58D041L,
      0x834727637190788AL, 0x2AE35ADAA804CE32L, 0xE3AA9CFF24BC92DAL, 0xCE33BD811236E7ADL,
      0x7FAF87891BE9818AL, 0x3C15A9283BDFBA51L, 0xE8E45A575513FA90L, 0xE224EF2739907F79L,
      0xFC275E6C532CB3CBL, 0x40C505971288B2DDL, 0xCD1C2FD6707573E1L, 0x2D62491B453DA6A3L,
      0xA079188A7E0C8C39L, 0x46A5B2A69D90533AL, 0xD68C9A2FDAEE951BL, 0x7C781E5CF39A5EB1L)

  val bytes = values map (Bytes (_))

  val patches = {
    var prev = Bytes.empty
    for (v <- bytes) yield {
      val p = CatalogHandler.diff (prev, v)
      prev = v
      p
    }}

  private def newCatalog (issues: Int): CatalogHandler = {
    val cat = CatalogHandler.unknown (ID)
    for ((v, i) <- values.take (issues) .zipWithIndex)
      cat.issue (i+1, Bytes (Picklers.fixedLong, v))
    cat
  }

  "When computing a patch and applying, the handler should" - {
    import Picklers._

    def checkDiff [A] (p: Pickler [A], v1: A, v2: A) {
      val b1 = Bytes (p, v1)
      val b2 = Bytes (p, v2)
      val patch = CatalogHandler.diff (b1, b2)
      val bp = CatalogHandler.patch (b1, patch)
      expectResult (b2) (bp)
      val vp = bp.unpickle (p)
      expectResult (v2) (vp)
    }

    "handle equal longs" in {
      checkDiff (fixedLong, 0x4423014FC535F3AFL, 0x4423014FC535F3AFL)
    }

    "handle consecutive longs" in {
      checkDiff (fixedLong, 0x4423014FC535F3AFL, 0x4423014FC535F3B0L)
    }

    "handle different longs" in {
      checkDiff (fixedLong, 0x4423014FC535F3AFL, 0x85CA2FFA1B08D309L)
    }

    "handle equal arrays of longs" in {
      checkDiff (seq (fixedLong), values, values)
    }

    "handle dropping longs from the array" in {
      checkDiff (seq (fixedLong), values, values.filter (_ != 0x3C4F7468C8B828FEL))
    }

    "handle adding longs to the array" in {
      checkDiff (seq (fixedLong), values filter (_ != 0x056C1999B220A24AL), values)
    }

    "handle changing longs in the array" in {
      checkDiff (
          seq (fixedLong),
          values updated (7, 0x6A973D914A523044L),
          values updated (11, 0xC2E5194FAFF06599L))
    }}

  "When computing and apply an update" - {

    "and this catalog has no history" - {

      "and the other catalog has no history, there should be no updates" in {
        val c = newCatalog (0)
        val u = c.diff (0)
        assert (isEmpty (u))
        val c2 = newCatalog (0)
        c2.patch (u)
        expectResult (0) (c2.version)
        expectResult (Bytes.empty) (c2.bytes)
        expectResult (Seq.empty) (c2.history.toSeq)
      }

      "and the other catalog is ahead, there should be no updates" in {
        val c = newCatalog (0)
        assert (isEmpty (c.diff (8)))
      }}

    "and this catalog has history" - {

      "and the other catalog is caught up, there should be no updates" in {
        val c = newCatalog (8)
        assert (isEmpty (c.diff (8)))
      }

      "and the other catalog is ahead, there should be no updates" in {
        val c = newCatalog (8)
        assert (isEmpty (c.diff (12)))
      }

      "and the other catalog is not too far behind" - {

        "and it can retain all its history, it should work" in {
          val c = newCatalog (12)
          expectResult (bytes (11)) (c.bytes)
          val u @ Right ((v, ps)) = c.diff (8)
          expectResult (8) (v)
          expectResult (patches drop 8 take 4) (ps)
          val c2 = newCatalog (8)
          expectResult (bytes (7)) (c2.bytes)
          c2.patch (u)
          expectResult (12) (c2.version)
          expectResult (bytes (11)) (c2.bytes)
          expectResult (patches take 12) (c2.history.toSeq)
        }

        "and it can drop some of its history, it should work" in {
          val c = newCatalog (20)
          expectResult (bytes (19)) (c.bytes)
          val u @ Right ((v, ps)) = c.diff (18)
          expectResult (18) (v)
          expectResult (patches drop 18 take 2) (ps)
          val c2 = newCatalog (18)
          expectResult (bytes (17)) (c2.bytes)
          expectResult (16) (c2.history.size)
          c2.patch (u)
          expectResult (20) (c2.version)
          expectResult (bytes (19)) (c2.bytes)
          expectResult (patches drop 4) (c2.history.toSeq)
        }}

      "and the other catalog is far behind" - {

        "it should synchronize the full catalog" in {
          val c = newCatalog (20)
          val u @ Left ((v, b, ps)) = c.diff (0)
          expectResult (20) (v)
          expectResult (bytes (19)) (c.bytes)
          expectResult (patches drop 4) (ps)
          val c2 = newCatalog (0)
          c2.patch (u)
          expectResult (20) (c2.version)
          expectResult (bytes (19)) (c2.bytes)
          expectResult (patches drop 4) (c2.history.toSeq)
        }}}}}