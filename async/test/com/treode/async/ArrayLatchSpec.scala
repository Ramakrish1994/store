package com.treode.async

import com.treode.async.implicits._
import com.treode.async.stubs.CallbackCaptor
import org.scalatest.FlatSpec

class ArrayLatchSpec extends FlatSpec {

  class DistinguishedException extends Exception

  "The ArrayLatch" should "release immediately for count==0" in {
    val cb = CallbackCaptor [Seq [Int]]
    val ltch = Latch.seq [Int] (0, cb)
    assertResult (Seq [Int] ()) (cb.passed.toSeq)
  }

  it should "reject extra releases" in {
    val cb = CallbackCaptor [Seq [Int]]
    val ltch = Latch.seq [Int] (0, cb)
    assertResult (Seq [Int] ()) (cb.passed.toSeq)
    intercept [Exception] (ltch.pass (0, 0))
  }

  it should "release after one pass for count==1" in {
    val cb = CallbackCaptor [Seq [Int]]
    val ltch = Latch.seq [Int] (1, cb)
    cb.assertNotInvoked()
    ltch.pass (0, 1)
    assertResult (Seq (1)) (cb.passed.toSeq)
  }

  it should "release after one fail for count==1" in {
    val cb = CallbackCaptor [Seq [Int]]
    val ltch = Latch.seq [Int] (1, cb)
    cb.assertNotInvoked()
    ltch.fail (new DistinguishedException)
    cb.failed [DistinguishedException]
  }

  it should "release after two passes for count==2" in {
    val cb = CallbackCaptor [Seq [Int]]
    val ltch = Latch.seq [Int] (2, cb)
    cb.assertNotInvoked()
    ltch.pass (0, 1)
    cb.assertNotInvoked()
    ltch.pass (1, 2)
    assertResult (Seq (1, 2)) (cb.passed.toSeq)
  }

  it should "release after two reversed passes for count==2" in {
    val cb = CallbackCaptor [Seq [Int]]
    val ltch = Latch.seq [Int] (2, cb)
    cb.assertNotInvoked()
    ltch.pass (1, 2)
    cb.assertNotInvoked()
    ltch.pass (0, 1)
    assertResult (Seq (1, 2)) (cb.passed.toSeq)
  }

  it should "release after a pass and a fail for count==2" in {
    val cb = CallbackCaptor [Seq [Int]]
    val ltch = Latch.seq [Int] (2, cb)
    cb.assertNotInvoked()
    ltch.pass (0, 0)
    cb.assertNotInvoked()
    ltch.fail (new DistinguishedException)
    cb.failed [DistinguishedException]
  }

  it should "release after two fails for count==2" in {
    val cb = CallbackCaptor [Seq [Int]]
    val ltch = Latch.seq [Int] (2, cb)
    cb.assertNotInvoked()
    ltch.fail (new Exception)
    cb.assertNotInvoked()
    ltch.fail (new Exception)
    cb.failed [MultiException]
  }}