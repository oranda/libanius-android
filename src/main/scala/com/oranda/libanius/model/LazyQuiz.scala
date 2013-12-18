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