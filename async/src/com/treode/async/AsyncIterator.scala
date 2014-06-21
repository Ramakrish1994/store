package com.treode.async

import java.lang.{Iterable => JIterable}
import java.util.{Iterator => JIterator}
import scala.util.{Failure, Success}

import com.treode.async.implicits._

import Async.{async, supply, when}

/** Concrete classes should implement `foreach`. */
trait AsyncIterator [A] {
  self =>

  /** Execute the asynchronous operation `f` foreach element. */
  def foreach (f: A => Async [Unit]): Async [Unit]

  def map [B] (f: A => B): AsyncIterator [B] =
    new AsyncIterator [B] {
      def foreach (g: B => Async [Unit]): Async [Unit] =
        self.foreach (x => g (f (x)))
    }

  def flatMap [B] (f: A => AsyncIterator [B]): AsyncIterator [B] =
    new AsyncIterator [B] {
      def foreach (g: B => Async [Unit]): Async [Unit] =
        self.foreach (x => f (x) foreach (g))
    }

  def filter (p: A => Boolean): AsyncIterator [A] =
    new AsyncIterator [A] {
      def foreach (g: A => Async [Unit]): Async [Unit] =
        self.foreach (x => when (p (x)) (g (x)))
    }

  def withFilter (p: A => Boolean): AsyncIterator [A] =
    filter (p)

  /** Execute the asynchronous operation `f` foreach element while `p` is true.  Return the first
    * element for which `p` fails, or `None` if `p` never fails and the whole sequence is
    * consumed.
    */
  def whilst [B >: A] (p: A => Boolean) (f: A => Async [Unit]): Async [Option [B]] =
    async { close =>
      foreach { x =>
        async { next =>
          if (p (x))
            f (x) run (next)
          else
            close.pass (Some (x))
        }
      } run {
        case Success (v) => close.pass (None)
        case Failure (t) => close.fail (t)
      }}

  /** Iterate the entire asynchronous iterator and return a standard sequence. */
  def toSeq: Async [Seq [A]] = {
    val builder = Seq.newBuilder [A]
    foreach (x => supply (builder += x)) .map (_ => builder.result)
  }}

object AsyncIterator {

  def empty [A] =
    new AsyncIterator [A] {
      def foreach (f: A => Async [Unit]): Async [Unit] =
        async (_.pass())
    }

  /** Transform a Scala iterator into an AsyncIterator. */
  def adapt [A] (iter: Iterator [A]) (implicit scheduler: Scheduler): AsyncIterator [A] =
    new AsyncIterator [A] {
      def foreach (f: A => Async [Unit]): Async [Unit] =
        scheduler.whilst (iter.hasNext) (f (iter.next))
    }

  /** Transform a Java iterator into an AsyncIterator. */
  def adapt [A] (iter: JIterator [A]) (implicit scheduler: Scheduler): AsyncIterator [A] =
    new AsyncIterator [A] {
      def foreach (f: A => Async [Unit]): Async [Unit] =
        scheduler.whilst (iter.hasNext) (f (iter.next))
  }

  def make [A] (maker: => Async [AsyncIterator [A]]): AsyncIterator [A] =
    new AsyncIterator [A] {
      def foreach (f: A => Async [Unit]): Async [Unit] =
        maker.flatMap (_.foreach (f))
    }

  /** Given asynchronous iterators of sorted items, merge them into a single asynchronous iterator
    * that maintains the sort.  Keep duplicate elements, and when two or more input iterators
    * duplicate an element, first list the element from the earlier iterator, that is by position
    * in `iters`.
    */
  def merge [A] (
      iters: Seq [AsyncIterator [A]]
  ) (implicit
      ordering: Ordering [A],
      scheduler: Scheduler
  ): AsyncIterator [A] =
    new MergeIterator (iters)
}
