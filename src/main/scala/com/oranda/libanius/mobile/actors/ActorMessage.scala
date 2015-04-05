/*
 * Libanius
 * Copyright (C) 2012-2015 James McCabe <james@oranda.com>
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

import akka.actor.ActorRef
import com.oranda.libanius.model.quizgroup.QuizGroupHeader
import com.oranda.libanius.model.quizitem.QuizItem
import java.util.{Date, UUID}

sealed trait ActorMessage
object ActorMessage

/*
 * The two main types of actor messages in Libanius are:
 *
 * 1. MailMessage: a message intended for a certain recipient (who may not exist yet)
 * 2. EventBusMessage: a message to be put on a channel that any actor can subscribe to
 */
class MailMessage(val recipientName: String) extends ActorMessage
case class ObjectMessage[T](message: T) extends ActorMessage
case class DropMessage(override val recipientName: String, message: Any)
  extends MailMessage(recipientName)
case class CollectMessage(override val recipientName: String, listenerRef: ActorRef)
  extends MailMessage(recipientName)

case class NoMessage() extends ActorMessage

class EventBusMessage(val id: String = UUID.randomUUID().toString(),
    val timestamp: Long = new Date().getTime()) extends ActorMessage

case class NewQuizItemMessage(header: QuizGroupHeader, quizItem: QuizItem)
  extends EventBusMessage
