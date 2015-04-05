/*
 * Libanius-Android
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

import akka.actor.{Actor}
import com.oranda.libanius.mobile.actors._
import com.oranda.libanius.dependencies.AppDependencyAccess
import scala.collection.mutable

class MailCentre extends Actor with AppDependencyAccess {

  /*
   * Simple map of requesters to messages. At any one time a requester can have only one message.
   * To retrieve a message, a requester must identify itself.
   */
  val messages = new mutable.HashMap[String, Any]()

  def receive = {
    case DropMessage(recipientName, message) =>
      l.log("MailCentre.DropMessage " + message.getClass.getSimpleName + " for " + recipientName)
      messages.put(recipientName, message)
    case CollectMessage(requesterName, listenerRef) =>
      l.log("MailCentre.CollectMessage, requester is " + requesterName)
      listenerRef ! (messages.get(requesterName) match {
        case Some(obj) => ObjectMessage(obj)
        case _ => NoMessage()
      })
  }
}