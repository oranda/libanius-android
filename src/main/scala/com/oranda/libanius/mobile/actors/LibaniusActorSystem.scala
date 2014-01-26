/*
 * Libanius-Android
 * Copyright (C) 2012-2014 James McCabe <james@oranda.com>
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

import akka.actor.{Props, ActorSystem}
import com.oranda.libanius.model.{LazyQuiz}
import com.oranda.libanius.actors.{DropMessage}
import com.oranda.libanius.dependencies.AppDependencyAccess

object LibaniusActorSystem extends AppDependencyAccess {
  val system: ActorSystem = ActorSystem("LibaniusActorSystem")
  val mailCentre = LibaniusActorSystem.system.actorOf(Props(new MailCentre), "MailCentre")

  def sendQuizTo(recipientName: String, quiz: LazyQuiz) {
    mailCentre ! DropMessage(recipientName, quiz)
  }
}


