/*
 * Copyright 2012-2013 James McCabe <james@oranda.com>
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License. You may obtain
 * a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.oranda.libanius.mobile.actors

import akka.actor.{Actor}
import com.oranda.libanius.actors.{NoMessage, Message, DropMessage, CollectMessage}
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
      l.log("MailCentre.DropMessage for " + recipientName)
      messages.put(recipientName, message)
    case CollectMessage(requesterName, listenerRef) =>
      l.log("MailCentre.CollectMessage, requester is " + requesterName)
      listenerRef ! (messages.get(requesterName) match {
        case Some(obj) => Message(obj)
        case None => NoMessage()
      })
  }
}