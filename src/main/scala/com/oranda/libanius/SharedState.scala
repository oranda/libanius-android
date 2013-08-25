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

package com.oranda.libanius

import com.oranda.libanius.model.wordmapping._

object SharedState {

  // The quiz needs to be global because it is shared between the OptionsScreen and the QuizScreen.
  var quiz: QuizOfWordMappings = QuizOfWordMappings()

  def numActiveQuizGroups = quiz.wordMappingGroups.size

  // A cache of quiz groups. It needs to be global because it must be updated when the quiz is.
  // TODO: use function-local memoization instead!
  var loadedQuizGroups = List[WordMappingGroup]()

  protected[libanius] def updateQuiz(newQuiz: QuizOfWordMappings) {
    SharedState.quiz = newQuiz
    newQuiz.wordMappingGroups.foreach(updateLoadedQuizGroups(_))
  }

  protected[libanius] def updateLoadedQuizGroups(quizGroup: WordMappingGroup) {
    loadedQuizGroups = loadedQuizGroups.filter(_.header != quizGroup.header)
    loadedQuizGroups :+= quizGroup
  }
}