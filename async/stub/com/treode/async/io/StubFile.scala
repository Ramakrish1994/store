package com.treode.async.io

import java.io.EOFException
import java.util.Arrays

import com.treode.async.{Callback, CallbackCaptor, StubScheduler}
import com.treode.buffer.PagedBuffer

class StubFile (scheduler: StubScheduler) extends File {

  private var data = new Array [Byte] (0)

  override def fill (input: PagedBuffer, pos: Long, len: Int, cb: Callback [Unit]): Unit =
    try {
      require (pos + len < Int.MaxValue)
      if (len <= input.readableBytes) {
        scheduler.execute (cb())
      } else if (data.length < pos) {
        scheduler.execute (cb.fail (new EOFException))
      } else  {
        input.capacity (input.writePos + len)
        val n = math.min (data.length - pos.toInt, input.writeableBytes)
        input.writeBytes (data, pos.toInt, n)
        if (data.length < pos + len) {
          val e = new EOFException
          scheduler.execute (cb.fail (e))
        } else
          scheduler.execute (cb())
      }
    } catch {
      case t: Throwable => scheduler.execute (cb.fail (t))
    }

  def fill (input: PagedBuffer, pos: Long, len: Int) {
    val cb = new CallbackCaptor [Unit]
    fill (input, pos, len, cb)
    scheduler.runTasks()
    cb.passed
  }

  override def flush (output: PagedBuffer, pos: Long, cb: Callback [Unit]): Unit =
    try {
      require (pos + output.readableBytes < Int.MaxValue)
      if (data.length < pos + output.readableBytes)
        data = Arrays.copyOf (data, pos.toInt + output.readableBytes)
      output.readBytes (data, pos.toInt, output.readableBytes)
      scheduler.execute (cb())
    } catch {
      case t: Throwable => scheduler.execute (cb.fail (t))
    }

  def flush (output: PagedBuffer, pos: Long) {
    val cb = new CallbackCaptor [Unit]
    flush (output, pos, cb)
    scheduler.runTasks()
    cb.passed
  }

  override def toString = s"StubFile(size=${data.length})"
}
