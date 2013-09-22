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

import akka.actor.ActorDSL._
import akka.actor._

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.os.{Handler, Bundle}
import android.view.View
import android.widget.{Button, TextView}
import java.lang.Runnable
import scala.concurrent.{ future, ExecutionContext }
import ExecutionContext.Implicits.global

import com.oranda.libanius.util.Util
import com.oranda.libanius.dependencies.AppDependencyAccess
import com.oranda.libanius.model.quizitem.QuizItemViewWithChoices
import com.oranda.libanius.model.{Quiz, QuizGroupHeader}
import com.oranda.libanius.mobile.{Timestamps}
import com.oranda.libanius.actors.{NoMessage, Message, CollectMessage}
import com.oranda.libanius.mobile.actors.{LibaniusActorSystem}

class QuizScreen extends Activity with TypedActivity with Timestamps with AppDependencyAccess {

  private[this] lazy val questionLabel: TextView = findView(TR.question)
  private[this] lazy val questionNotesLabel: TextView = findView(TR.questionNotes)
  private[this] lazy val answerOption1Button: Button = findView(TR.answerOption1)
  private[this] lazy val answerOption2Button: Button = findView(TR.answerOption2)
  private[this] lazy val answerOption3Button: Button = findView(TR.answerOption3)
    
  private[this] lazy val prevQuestionLabel: TextView = findView(TR.prevQuestion)
  private[this] lazy val prevAnswerOption1Label: TextView = findView(TR.prevAnswerOption1)
  private[this] lazy val prevAnswerOption2Label: TextView = findView(TR.prevAnswerOption2)
  private[this] lazy val prevAnswerOption3Label: TextView = findView(TR.prevAnswerOption3)

  private[this] lazy val speedLabel: TextView = findView(TR.speed)
  private[this] lazy val statusLabel: TextView = findView(TR.status)

  private[this] lazy val answerOptionButtons = List(answerOption1Button,
      answerOption2Button, answerOption3Button)
  private[this] lazy val prevOptionLabels = List(prevAnswerOption1Label,
      prevAnswerOption2Label, prevAnswerOption3Label)

  private[this] var currentQuizItem: QuizItemViewWithChoices = _

  private[this] var quiz: Quiz = _  // set in onCreate

  override def onCreate(savedInstanceState: Bundle) {
    super.onCreate(savedInstanceState)

    l.log("QuizScreen: onCreate ")
    implicit val system: ActorSystem = LibaniusActorSystem.system
    val recipientName = getClass.getSimpleName
    LibaniusActorSystem.mailCentre ! CollectMessage(recipientName,
      actor(new Act {
        become {
          case Message(quizReceived: Quiz) =>
            l.log("received quiz " + quizReceived.numItems + " and setting it in QuizScreen")
            quiz = quizReceived
            runGuiOnUiThread()
          case NoMessage() =>
            runGuiOnUiThread()
        }
      })
    )
  }

  def runGuiOnUiThread() {
    runOnUiThread(new Runnable { override def run() { initGui() } })
  }

  def initGui() {
    setContentView(R.layout.quizscreen)
    testUserWithQuizItem()
  }

  override def onPause() {
    super.onPause()
    saveQuiz
  }

  def testUserWithQuizItem() {
    Util.stopwatch(quiz.findPresentableQuizItem, "find quiz items") match {
      case (Some((quizItem, quizGroup))) =>
        currentQuizItem = quizItem
        showNextQuizItem()
        quiz = quiz.addQuizGroup(quizGroup.updatedPromptNumber)
      case _ =>
        printStatus("No more questions found! Done!")
    }
  }
  
  def testUserWithQuizItemAgain() {
    showScoreAsync() // The score takes a second to calculate, so do it in the background
    showSpeed()
    testUserWithQuizItem()
  }
  
  def showNextQuizItem() {
    answerOptionButtons.foreach(_.setBackgroundColor(Color.LTGRAY))

    questionLabel.setText(currentQuizItem.prompt.toString)
    var questionNotesText = "What is the " + currentQuizItem.responseType + "?"
    if (currentQuizItem.numCorrectAnswersInARow > 0)
      questionNotesText += " (correctly answered " +
          currentQuizItem.numCorrectAnswersInARow + " times)"
    questionNotesLabel.setText(questionNotesText)
        
    val optionsIter = currentQuizItem.allChoices.iterator
    answerOptionButtons.foreach(_.setText(optionsIter.next))
  }
  
  def answerOption1Clicked(v: View) { processUserAnswer(answerOption1Button) }
  def answerOption2Clicked(v: View) { processUserAnswer(answerOption2Button) }
  def answerOption3Clicked(v: View) { processUserAnswer(answerOption3Button) }
  
  def removeCurrentWord(v: View) {
    val (newQuiz, wasRemoved) = quiz.removeQuizItem(currentQuizItem.quizItem,
        currentQuizItem.quizGroupHeader)
        
    quiz = newQuiz
    if (wasRemoved) printStatus("Deleted word " + currentQuizItem.prompt)
    testUserWithQuizItemAgain()    
  }
  
  def gotoOptions(v: View) {
    LibaniusActorSystem.sendMessageTo("OptionsScreen", quiz)
    l.log("in QuizScreen, sending quiz")
    val optionsScreen = new Intent(getApplicationContext(), classOf[OptionsScreen])
    startActivity(optionsScreen)
  }

  private def updateUI(correctAnswer: String, clickedButton: Button) {
    resetButtonAndLabelColors()
    setPrevQuestionText()
    populatePrevOptions()
    setColorsForButtons(correctAnswer, clickedButton)
  }

  private def resetButtonAndLabelColors() {
    prevOptionLabels.foreach (_.setTextColor(Color.LTGRAY))
    answerOptionButtons.foreach (_.setBackgroundColor(Color.LTGRAY))
  }

  private def setPrevQuestionText() {
    var prevQuestionText = "PREV: " + questionLabel.getText
    val maxAnswers = conf.numCorrectAnswersRequired
    if (currentQuizItem.numCorrectAnswersInARow == maxAnswers)
      prevQuestionText += " (correct " + maxAnswers + " times -- COMPLETE)"
    prevQuestionLabel.setText(prevQuestionText)
  }

  private def populatePrevOptions() {
    val reverseGroupHeader = currentQuizItem.quizGroupHeader.reverse
    val isReverseLookupPossible = quiz.findQuizGroup(reverseGroupHeader).isDefined

    if (isReverseLookupPossible) {
      val labelsToOptions = prevOptionLabels zip currentQuizItem.allChoices
      labelsToOptions.foreach {
        case (label, option) => setPrevOptionsText(label, option,
            currentQuizItem.quizGroupHeader, reverseGroupHeader)
      }
    }
  }

  private def setColorsForButtons(correctAnswer: String, clickedButton: Button) {

    answerOptionButtons.find(_.getText == correctAnswer).foreach { correctButton =>
      val buttonsToLabels = answerOptionButtons zip prevOptionLabels
      buttonsToLabels.foreach { buttonToLabel =>
        setColorOnAnswer(buttonToLabel._1, buttonToLabel._2, correctButton, clickedButton)
      }
    }
  }

  def setPrevOptionsText(prevOptionLabel: TextView, responseOption: String,
      qgHeader: QuizGroupHeader, qgReverseHeader: QuizGroupHeader) {

    val values = quiz.findResponsesFor(responseOption, qgReverseHeader) match {
      case Nil => quiz.findPromptsFor(responseOption, qgHeader)
      case values => values
    }
    prevOptionLabel.setText(responseOption + " = " + values.mkString(", "))
  }
  
  def setColorOnAnswer(answerOptionButton: Button, 
      prevAnswerOptionLabel: TextView, CORRECT_BUTTON: Button, CLICKED_BUTTON: Button) {
    
    answerOptionButton match {
      case CORRECT_BUTTON => 
        answerOptionButton.setBackgroundColor(Color.GREEN)
        prevAnswerOptionLabel.setTextColor(Color.GREEN)
      case CLICKED_BUTTON => 
        answerOptionButton.setBackgroundColor(Color.RED)
        prevAnswerOptionLabel.setTextColor(Color.RED)
      case _ =>
    }
  }
  
  def saveQuiz() {
    printStatus("Saving quiz data...")
    dataStore.saveQuiz(quiz)
    printStatus("Finished saving quiz data!")
  }

  def processUserAnswer(clickedButton: Button) {
    val userAnswerTxt = clickedButton.getText.toString
    val correctAnswer = currentQuizItem.quizItem.response
    val isCorrect = correctAnswer.matches(userAnswerTxt)
    updateTimestamps(isCorrect)
    Util.stopwatch(quiz = quiz.updateWithUserAnswer(isCorrect, currentQuizItem),
        "updateWithUserAnswer")
    updateUI(correctAnswer.text, clickedButton)

    val delayMillis = if (isCorrect) 10 else 300
    val handler = new Handler
    handler.postDelayed(new Runnable() { def run() = testUserWithQuizItemAgain() }, delayMillis)
  }

  def showScoreAsync() {
    def formatAndPrintScore(scoreStr: String) {
      val scoreStrMaxIndex = scala.math.min(scoreStr.length, 6)
      printScore(scoreStr.substring(0, scoreStrMaxIndex) + "%")
    }

    /*
     * Instead of using Android's AsyncTask, use a Scala Future. It's more concise and general,
     * but we need to remind Android to use the UI thread when the result is returned.
     */
    future {
      (Util.stopwatch(quiz.scoreSoFar, "scoreSoFar") * 100).toString
    } map { scoreSoFar: String =>
      runOnUiThread(new Runnable { override def run() { formatAndPrintScore(scoreSoFar) } })
    }
  }
  
  def showSpeed() { speedLabel.setText("Speed: " + answerSpeed + "/min") }
  def printStatus(text: String) { statusLabel.setText(text) }
  def printScore(score: String) { statusLabel.setText("Score: " + score) }
}
