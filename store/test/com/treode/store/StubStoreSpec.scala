package com.treode.store

import scala.util.Random

import com.treode.async.Async
import org.scalatest.FreeSpec

class StubStoreSpec extends FreeSpec with StoreBehaviors {

  private class TestableStubStore extends TestableStore {

    private val delegate = new StubStore

    def read (rt: TxClock, ops: ReadOp*): Async [Seq [Value]] =
      delegate.read (rt, ops)

    def write (ct: TxClock, ops: WriteOp*): Async [WriteResult] =
      delegate.write (TxId (Random.nextLong, 0), ct, ops)

    def expectCells (t: TableId) (expected: Cell*): Unit =
      assertResult (expected.sorted) (delegate.scan (t))
  }

  "The StubStore should" - {

    behave like aStore (_ => new TestableStubStore)

    behave like aMultithreadableStore (10000) (new TestableStubStore)
  }}
