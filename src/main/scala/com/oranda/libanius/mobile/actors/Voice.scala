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

import java.util.Locale

import akka.actor.Actor
import android.content.Context
import android.speech.tts.TextToSpeech
import com.oranda.libanius.dependencies.AppDependencyAccess
import com.oranda.libanius.model.KnownQuizGroups

class Voice(implicit ctx: Context) extends Actor with TextToSpeech.OnInitListener
    with AppDependencyAccess {

  private val DEFAULT_LOCALE = Locale.UK

  private[this] val tts = new TextToSpeech(ctx, this)

  override def onInit(status: Int): Unit =
    if (status == TextToSpeech.SUCCESS)
      setSpeechLanguage(DEFAULT_LOCALE)
    else
      l.logError("Error initializing TextToSpeech engine.")

  override def receive = {
    case Speak(text: String, quizGroupKeyType: String) =>
      KnownQuizGroups.getLocale(quizGroupKeyType).foreach(speak(text, _))
      setSpeechLanguage(DEFAULT_LOCALE)
    case _ =>
      l.logError("Voice received an unknown command")
  }

  override def postStop {
    tts.stop
    tts.shutdown
  }

  def speak(text: String, locale: Locale): Unit = {
    // Some characters like underscores are allowed in "raw" data but can disrupt the speaking.
    val cleanText = text.replaceAll("[_\\*]", ".")

    setSpeechLanguage(locale)
    tts.speak(cleanText, TextToSpeech.QUEUE_FLUSH, null)
  }

  private[this] def setSpeechLanguage(locale: Locale): Unit = {
    val result: Int = tts.setLanguage(locale)
    if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED)
      l.logError("Language is not available.")
  }
}

case class Speak(text: String, quizGroupKeyType: String)
