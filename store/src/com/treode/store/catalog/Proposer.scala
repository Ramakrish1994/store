/*
 * Copyright 2014 Treode, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.treode.store.catalog

import scala.language.postfixOps

import com.treode.async.{Backoff, Fiber}
import com.treode.async.implicits._
import com.treode.async.misc.RichInt
import com.treode.cluster.{MessageDescriptor, Peer, ReplyTracker}
import com.treode.store.{Atlas, BallotNumber, CatalogId, TimeoutException}

private class Proposer (key: CatalogId, version: Int, kit: CatalogKit) {
  import kit.proposers.remove
  import kit.{cluster, library, random, scheduler}
  import kit.config.{closedLifetime, proposingBackoff}

  private val fiber = new Fiber
  var state: State = Opening

  trait State {
    def open (ballot: Long, patch: Patch) = ()
    def learn (k: Learner)
    def refuse (ballot: Long)
    def grant (from: Peer, ballot: Long, proposal: Proposal)
    def accept (from: Peer, ballot: Long)
    def chosen (value: Patch)
    def timeout()
    def shutdown() = state = Shutdown
  }

  private def max (x: Proposal, y: Proposal) = {
    if (x.isDefined && y.isDefined) {
      if (x.get._1 > y.get._1) x else y
    } else if (x.isDefined) {
      x
    } else if (y.isDefined) {
      y
    } else {
      None
    }}

  private def agreement (x: Proposal, patch: Patch) = {
    x match {
      case Some ((_, patch)) => patch
      case None => patch
    }}

  private def track (atlas: Atlas): ReplyTracker =
    atlas.locate (0) .track

  object Opening extends State {

    override def open (ballot: Long, patch: Patch) =
      state = new Open (ballot, patch)

    def learn (k: Learner) = throw new IllegalStateException

    def refuse (ballot: Long) = ()

    def grant (from: Peer, ballot: Long, proposal: Proposal) = ()

    def accept (from: Peer, ballot: Long) = ()

    def chosen (v: Patch): Unit =
      state = new Closed (v)

    def timeout() = ()

    override def toString = "Proposer.Opening (%s)" format (key.toString)
  }

  class Open (_ballot: Long, patch: Patch) extends State {

    var learners = List.empty [Learner]
    var ballot = _ballot
    var refused = ballot
    var proposed = Option.empty [(BallotNumber, Patch)]
    var atlas = library.atlas
    var granted = track (atlas)
    var accepted = track (atlas)

    // Ballot number zero was implicitly accepted.
    if (ballot == 0)
      Acceptor.propose (atlas.version, key, version, ballot, patch) (granted)
    else
      Acceptor.ask (atlas.version, key, version, ballot, patch) (granted)

    val backoff = proposingBackoff.iterator
    fiber.delay (backoff.next) (state.timeout())

    def learn (k: Learner) =
      learners ::= k

    def refuse (ballot: Long) = {
      refused = math.max (refused, ballot)
      granted = track (atlas)
      accepted = track (atlas)
    }

    def grant (from: Peer, ballot: Long, proposal: Proposal) {
      if (ballot == this.ballot) {
        granted += from
        proposed = max (proposed, proposal)
        if (granted.quorum) {
          val v = agreement (proposed, patch)
          Acceptor.propose (atlas.version, key, version, ballot, v) (accepted)
        }}}

    def accept (from: Peer, ballot: Long) {
      if (ballot == this.ballot) {
        accepted += from
        if (accepted.quorum) {
          val v = agreement (proposed, patch)
          Acceptor.choose (key, version, v) (track (atlas))
          learners foreach (_.pass (v))
          state = new Closed (v)
        }}}

    def chosen (v: Patch) {
      learners foreach (_.pass (v))
      state = new Closed (v)
    }

    def timeout() {
      if (backoff.hasNext) {
        atlas = library.atlas
        granted = track (atlas)
        accepted = track (atlas)
        ballot = refused + random.nextInt (17) + 1
        refused = ballot
        Acceptor.ask (atlas.version, key, version, ballot, patch) (granted)
        fiber.delay (backoff.next) (state.timeout())
      } else {
        remove (key, version, Proposer.this)
        learners foreach (_.fail (new TimeoutException))
      }}

    override def toString = "Proposer.Open " + (key, ballot, patch)
  }

  class Closed (patch: Patch) extends State {

    fiber.delay (closedLifetime) (remove (key, version, Proposer.this))

    def learn (k: Learner) =
      k.pass (patch)

    def chosen (v: Patch) =
      require (v == patch, "Paxos disagreement")

    def refuse (ballot: Long) = ()
    def grant (from: Peer, ballot: Long, proposal: Proposal) = ()
    def accept (from: Peer, ballot: Long) = ()
    def timeout() = ()

    override def toString = "Proposer.Closed " + (key, patch)
  }

  object Shutdown extends State {

    def learn (k: Learner) = ()
    def refuse (ballot: Long) = ()
    def grant (from: Peer, ballot: Long, proposal: Proposal) = ()
    def accept (from: Peer, ballot: Long) = ()
    def chosen (v: Patch) = ()
    def timeout() = ()

    override def toString = "Proposer.Shutdown (%s)" format (key)
  }

  def open (ballot: Long, patch: Patch) =
    fiber.execute (state.open (ballot, patch))

  def learn (k: Learner) =
    fiber.execute  (state.learn (k))

  def refuse (ballot: Long) =
    fiber.execute  (state.refuse (ballot))

  def grant (from: Peer, ballot: Long, proposal: Proposal) =
    fiber.execute  (state.grant (from, ballot, proposal))

  def accept (from: Peer, ballot: Long) =
    fiber.execute  (state.accept (from, ballot))

  def chosen (patch: Patch) =
    fiber.execute  (state.chosen (patch))

  def shutdown() =
    fiber.execute  (state.shutdown())

  override def toString = state.toString
}

private object Proposer {

  val refuse = {
    import CatalogPicklers._
    MessageDescriptor (0xFF8562E9071168EAL, tuple (catId, uint, ulong))
  }

  val grant = {
    import CatalogPicklers._
    MessageDescriptor (0xFF3F6FFC9993CD75L, tuple (catId, uint, ulong, proposal))
  }

  val accept = {
    import CatalogPicklers._
    MessageDescriptor (0xFF0E7973CC65E95FL, tuple (catId, uint, ulong))
  }

  val chosen = {
    import CatalogPicklers._
    MessageDescriptor (0xFF2259321F9D4EF9L, tuple (catId, uint, patch))
  }}
