package com.treode.store.local

import java.io.Closeable

import com.treode.store.TableId

private abstract class TableCache [T <: Closeable] {

  private var tables = Map.empty [TableId, T]

  protected def make (id: TableId): T

  def get (id: TableId): T = synchronized {
    tables.get (id) match {
      case Some (t) =>
        t
      case None =>
        val t = make (id: TableId)
        tables += (id -> t)
        t
    }}

  def close() {
    for (t <- tables.values)
      t.close()
  }}