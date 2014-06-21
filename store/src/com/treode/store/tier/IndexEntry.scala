package com.treode.store.tier

import com.google.common.primitives.Longs
import com.treode.store.{Bytes, StorePicklers, TxClock}
import com.treode.disk.Position

private case class IndexEntry (key: Bytes, time: TxClock, disk: Int, offset: Long, length: Int)
extends Ordered [IndexEntry] {

  def pos = Position (disk, offset, length)

  def byteSize = IndexEntry.pickler.byteSize (this)

  def compare (that: IndexEntry): Int = {
    val r = key compare that.key
    if (r != 0) return r
    // Reverse chronological order
    that.time compare time
  }}

private object IndexEntry extends Ordering [IndexEntry] {

  def apply (key: Bytes, time: TxClock, pos: Position): IndexEntry =
    new IndexEntry (key, time, pos.disk, pos.offset, pos.length)

  def compare (x: IndexEntry, y: IndexEntry): Int =
    x compare y

  val pickler = {
    import StorePicklers._
    wrap (bytes, txClock, uint, ulong, uint)
    .build (v => IndexEntry (v._1, v._2, v._3, v._4, v._5))
    .inspect (v => (v.key, v.time, v.disk, v.offset, v.length))
  }}
