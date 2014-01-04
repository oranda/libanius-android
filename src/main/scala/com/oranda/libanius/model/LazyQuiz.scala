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

package com.oranda.libanius.model

import com.oranda.libanius.model.quizitem.QuizItem
import com.oranda.libanius.dependencies.AppDependencyAccess
import scala.concurrent._
import com.oranda.libanius.model.quizitem.QuizItemViewWithChoices


import scala.collection.immutable._

import scala.concurrent.duration._

import scala.concurrent.{ future, ExecutionContext }
import ExecutionContext.Implicits.global
import com.oranda.libanius.model.quizgroup.{QuizGroupHeader, QuizGroup, QuizGroupWithHeader}


/*
 * The libanius library contains the core model for Libanius, including the Quiz singleton.
 * This class wraps Quiz and adds futures that load specific quiz groups.
 */
case class LazyQuiz(quiz: Quiz) extends AppDependencyAccess {

  private[this] var qgLoadingFutures: Set[Future[QuizGroupWithHeader]] = Set()

  def addQuizGroup(header: QuizGroupHeader, quizGroup: QuizGroup): LazyQuiz =
    copy(quiz = quiz.addQuizGroup(header, quizGroup))

  def addQuizGroups(qgs: Map[QuizGroupHeader, QuizGroup]): LazyQuiz =
    copy(quiz = quiz.addQuizGroups(qgs))

  def addOrReplaceQuizGroup(header: QuizGroupHeader, quizGroup: QuizGroup): LazyQuiz =
    copy(quiz = quiz.addOrReplaceQuizGroup(header, quizGroup))

  def addQuizItemToFrontOfTwoGroups(header: QuizGroupHeader, prompt: String, response: String):
      LazyQuiz =
    copy(quiz = quiz.addQuizItemToFrontOfTwoGroups(header, prompt, response))

  def updateWithUserAnswer(isCorrect: Boolean, currentQuizItem: QuizItemViewWithChoices):
      LazyQuiz =
    copy(quiz = quiz.updateWithUserResponse(isCorrect, currentQuizItem.quizGroupHeader,
        currentQuizItem.quizItem))

  def removeQuizItem(quizItem: QuizItem, header: QuizGroupHeader): (LazyQuiz, Boolean) = {
    val (newQuiz, result) = quiz.removeQuizItem(quizItem, header)
    (copy(quiz = newQuiz), result)
  }

  def deactivate(header: QuizGroupHeader): LazyQuiz = copy(quiz = quiz.deactivate(header))

  def activate(header: QuizGroupHeader): LazyQuiz =
    if (!quiz.hasQuizGroup(header)) loadQuizGroup(header)
    else copy(quiz = quiz.activate(header))

  private def loadQuizGroup(header: QuizGroupHeader): LazyQuiz = {
    qgLoadingFutures += future {
      val quizGroup = dataStore.loadQuizGroup(header).activate
      QuizGroupWithHeader(header, quizGroup)
    }
    this
    // active flag is not set yet but it is by the time the quizGroup is retrieved from the Future
  }

  def waitForQuizGroupsToLoad(quizGroupHeaders: Set[QuizGroupHeader]): LazyQuiz = {
    val loadedQuizGroups: Iterable[QuizGroupWithHeader] =
        Await.result(Future.sequence(qgLoadingFutures), 10 seconds)
    qgLoadingFutures = Set() // make sure the futures don't run again
    fillQuizWithQuizGroups(loadedQuizGroups)
  }

  def fillQuizWithQuizGroups(quizGroups: Iterable[QuizGroupWithHeader]): LazyQuiz =
    copy(quiz = quiz.addQuizGroups(quizGroups))
}

object LazyQuiz {
  // Forward access from LazyQuiz to Quiz whenever necessary
  implicit def lazyQuiz2quiz(LazyQuiz: LazyQuiz): Quiz = LazyQuiz.quiz
}