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

package com.treode.disk

import java.nio.file.Path

import com.treode.async.{Async, Scheduler}, Async.async
import com.treode.buffer.PagedBuffer

package edit {

  case class ReattachFailure (path: Path, thrown: Throwable) {
    override def toString = s"Could not reattach ${quote (path)}: $thrown"
  }

  class ReattachException (failures: Seq [ReattachFailure]) extends Exception {
    override def getMessage() = failures mkString "; "
  }}

package object edit {

  private [edit] type LogDispatcher = Dispatcher [PickledRecord]

  private [edit] type PageDispatcher = Dispatcher [PickledPage]
}
