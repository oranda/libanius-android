/*
 * Libanius-Android
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

import akka.actor.{Props, ActorSystem}
import android.content.Context
import com.oranda.libanius.model.LazyQuiz
import com.oranda.libanius.dependencies.AppDependencyAccess
import com.oranda.libanius.model.quizgroup.QuizGroupHeader
import com.oranda.libanius.model.quizitem.QuizItem

class LibaniusActorSystem(implicit ctx: Context) extends AppDependencyAccess {
  val system: ActorSystem = ActorSystem("LibaniusActorSystem")
  val appActorEventBus = new LibaniusActorEventBus
  val mailCentre = system.actorOf(Props(new MailCentre), "MailCentre")
  val voice = system.actorOf(Props(new Voice), "Voice")
  val soundPlayer = system.actorOf(Props(new SoundPlayer), "SoundPlayer")
}

object LibaniusActorSystem extends AppDependencyAccess {

  val QUIZ_ITEM_CHANNEL = "/data_objects/quiz_item"
  var actorSystem: LibaniusActorSystem = _

  def init(implicit ctx: Context): Unit =
    actorSystem = new LibaniusActorSystem

  def shutdown(): Unit = {
    l.log("ActorSystem shutdown")
    actorSystem.system.shutdown()
  }

  def sendQuizTo(recipientName: String, quiz: LazyQuiz): Unit =
    actorSystem.mailCentre ! DropMessage(recipientName, quiz)

  def sendQuizItem(qgh: QuizGroupHeader, quizItem: QuizItem): Unit = {
    val newQuizItemMessage = NewQuizItemMessage(qgh, quizItem)
    actorSystem.appActorEventBus.publish(
        EventBusMessageEvent(QUIZ_ITEM_CHANNEL, newQuizItemMessage))
  }

  def speak(text: String, quizGroupKeyType: String): Unit = {
    actorSystem.voice ! Speak(text, quizGroupKeyType)
  }
}
