/*
 * Libanius
 * Copyright (C) 2012-2016 James McCabe <james@oranda.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.oranda.libanius.mobile.actors

import akka.event.ActorEventBus
import akka.event.LookupClassification

import com.oranda.libanius.dependencies.AppDependencyAccess

/*
import java.util.{Date, UUID}
import com.oranda.libanius.model.LazyQuiz

case class QuizMessage(override val id: String = UUID.randomUUID().toString(),
    override val timestamp: Long = new Date().getTime(), quiz: LazyQuiz)
  extends Message(id, timestamp)
*/

case class EventBusMessageEvent(val channel: String, val message: EventBusMessage)

class LibaniusActorEventBus extends ActorEventBus with LookupClassification
    with AppDependencyAccess {

  type Event = EventBusMessageEvent
  type Classifier = String

  protected def mapSize(): Int = 10

  protected def classify(event: Event): Classifier = event.channel

  protected def publish(event: Event, subscriber: Subscriber): Unit = {
    l.log("LibaniusActorEventBus.publishing quiz to channel " + event.channel)
    subscriber ! event
  }
}
