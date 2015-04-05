/*
 * Libanius
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

package com.oranda.libanius.model

import java.util.Locale

/**
 * Quiz groups known by this client app.
 * This client may choose to treat some quiz groups specially if it knows about them.
 */
object KnownQuizGroups {
  sealed abstract class QuizGroupKeyType(
    val quizGroupKeyTypeName: String,
    val locale: Locale
  )

  case object ENGLISH_WORD extends QuizGroupKeyType("English word", Locale.UK)
  case object GERMAN_WORD extends QuizGroupKeyType("German word", Locale.GERMANY)
  case object SPANISH_WORD extends QuizGroupKeyType("Spanish word", new Locale("es", "ES"))
  case object SAMPLE_SENTENCE extends QuizGroupKeyType("Sample sentence", Locale.UK)
  case object SYNONYMS extends QuizGroupKeyType("Synonyms", Locale.UK)
  case object ENGLISH_DEFINITION extends QuizGroupKeyType("English definition", Locale.UK)

  val quizGroupKeyTypes = Seq(ENGLISH_WORD, GERMAN_WORD, SPANISH_WORD, SAMPLE_SENTENCE,
      SYNONYMS, ENGLISH_DEFINITION)

  def getLocale(quizGroupKeyTypeName: String): Option[Locale] =
    quizGroupKeyTypes.find(_.quizGroupKeyTypeName == quizGroupKeyTypeName).map(_.locale)
}
