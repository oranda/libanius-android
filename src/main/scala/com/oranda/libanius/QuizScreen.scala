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

package com.oranda.libanius

import akka.actor.ActorDSL._
import akka.actor._

import android.app.Activity
import android.content.Intent
import android.os.{Handler, Bundle}
import android.view.{Gravity, KeyEvent, View}
import android.widget.{EditText, LinearLayout, Button, TextView}
import scala.concurrent.{ future, ExecutionContext }
import ExecutionContext.Implicits.global

import com.oranda.libanius.util.{StringUtil, Util}
import com.oranda.libanius.dependencies.AppDependencyAccess
import com.oranda.libanius.model.{Quiz, LazyQuiz}
import com.oranda.libanius.mobile.Timestamps
import com.oranda.libanius.mobile.actors._
import com.oranda.libanius.model.quizgroup.QuizGroupHeader
import android.view.View.OnClickListener
import android.util.TypedValue
import android.view.inputmethod.EditorInfo
import com.oranda.libanius.model.quizitem.QuizItemViewWithChoices
import android.graphics.Color
import LibaniusActorSystem._
import scala.Some
import com.oranda.libanius.mobile.actors.NoMessage

class QuizScreen extends Activity with TypedActivity with Timestamps with AppDependencyAccess {

  private[this] lazy val questionLabel: TextView = findView(TR.question)
  private[this] lazy val questionNotesLabel: TextView = findView(TR.questionNotes)

  private[this] lazy val responseInputArea: LinearLayout = findView(TR.responseInputArea)

  private[this] lazy val prevQuestionArea: LinearLayout = findView(TR.prevQuestionArea)
  private[this] lazy val prevOptionArea: LinearLayout = findView(TR.prevOptionArea)

  private[this] lazy val speedLabel: TextView = findView(TR.speed)

  private[this] lazy implicit val statusLabel: TextView = findView(TR.status)

  private[this] var currentQuizItem: QuizItemViewWithChoices = _

  private[this] var quiz: LazyQuiz = LazyQuiz(Quiz())  // set in onCreate

  /*
  override def onCreate(savedInstanceState: Bundle) {
    super.onCreate(savedInstanceState)

    l.log("QuizScreen: onCreate ")

    val subscriber = system.actorOf(Props(new Actor {
      def receive = {
        case MessageEvent(QUIZ_CHANNEL, QuizMessage(_, _, quizReceived)) =>
          l.log("QuizScreen received quiz " + quizReceived.numQuizItems + " and sets it")
          quiz = quizReceived
          appActorEventBus.unsubscribe(self, QUIZ_CHANNEL)
          l.log("QuizScreen unsubscribed from QUIZ_CHANNEL")
          runOnUiThread { testUserWithQuizItem() }
        case _ =>
          l.log("QuizScreen received something strange")
      }
    }))

    setContentView(R.layout.quizscreen)

    LibaniusActorSystem.appActorEventBus.subscribe(subscriber, QUIZ_CHANNEL)
  }
*/


  override def onCreate(savedInstanceState: Bundle) {
    super.onCreate(savedInstanceState)

    l.log("QuizScreen: onCreate ")
    implicit val system: ActorSystem = LibaniusActorSystem.system
    val recipientName = getClass.getSimpleName
    LibaniusActorSystem.mailCentre ! CollectMessage(recipientName,
      actor(new Act {
        become {
          case ObjectMessage(quizReceived: LazyQuiz) =>
            l.log("received quiz with numItems " + quizReceived.numQuizItems +
              " and setting it in QuizScreen")
            quiz = quizReceived
            runGuiOnUiThread()
          case NoMessage() =>
            l.logError("received no quiz!")
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
    if (quiz == null) {
      // Check that the quiz data has not been cleared from memory on a resume
      quiz = LazyQuiz(Quiz(dataStore.loadAllQuizGroupsFromFilesDir))
    }
    testUserWithQuizItem()
  }

  override def onPause() {
    super.onPause()
    saveQuiz
  }

  private[this] def saveQuiz() {
    try { showStatus("Saving quiz data...") } catch { case e: Exception => /* ignore NPEs */ }
    future { dataStore.saveQuiz(quiz) }
  }

  def testUserWithQuizItem() {
    Util.stopwatch(quiz.findPresentableQuizItem, "find quiz items") match {
      case (Some((quizItem, qgWithHeader))) =>
        currentQuizItem = quizItem
        showNextQuizItem(currentQuizItem)
        quiz = quiz.addOrReplaceQuizGroup(qgWithHeader.header,
            qgWithHeader.quizGroup.updatedPromptNumber)
      case _ =>
        showStatus("No more questions found! Done!")
    }
  }

  def testUserWithQuizItemAgain() {
    showScoreAsync() // The score takes a second to calculate, so do it in the background
    showSpeed()
    testUserWithQuizItem()
  }

  private[this] def showNextQuizItem(currentQuizItem: QuizItemViewWithChoices) {

    questionLabel.setText(currentQuizItem.prompt.toString)
    var questionNotesText = "What is the " + currentQuizItem.responseType + "?"
    if (currentQuizItem.numCorrectResponsesInARow > 0)
      questionNotesText += " (answered right " +
          currentQuizItem.numCorrectResponsesInARow + " times)"
    questionNotesLabel.setText(questionNotesText)
    if (currentQuizItem.useMultipleChoice) presentChoiceButtons(currentQuizItem)
    else showTextBoxAndGetInput(currentQuizItem)
  }

  private[this] def presentChoiceButtons(currentQuizItem: QuizItemViewWithChoices) {
    responseInputArea.removeAllViews()
    val choiceButtons = Widgets.constructChoiceButtons(this, currentQuizItem.allChoices)

    choiceButtons.foreach { choiceButton =>
      choiceButton.setOnClickListener(new OnClickListener() {
        def onClick(view: View) {
          processButtonResponse(currentQuizItem, choiceButtons, choiceButton)
        }
      })
      responseInputArea.addView(choiceButton)
    }
  }

  private[this] def showTextBoxAndGetInput(currentQuizItem: QuizItemViewWithChoices) {
    responseInputArea.removeAllViews()

    val responseTextBox = new EditText(this)
    val ctx = this
    responseTextBox.setOnEditorActionListener(new TextView.OnEditorActionListener() {
      override def onEditorAction(searchInputBox: TextView, actionId: Int,
          event: KeyEvent): Boolean = {
        if (actionId == EditorInfo.IME_ACTION_DONE || event.getAction == KeyEvent.ACTION_DOWN) {
          Widgets.closeOnscreenKeyboard(ctx, searchInputBox.getWindowToken)
          processTextResponse(currentQuizItem, searchInputBox.getText.toString)
        }
        true
      }
    })
    responseInputArea.addView(responseTextBox)
    responseTextBox.setGravity(Gravity.TOP)
    Widgets.showOnscreenKeyboard(this)
    responseTextBox.setSelected(true)
  }

  def removeCurrentWord(v: View) {
    val (newQuiz: LazyQuiz, wasRemoved) = quiz.removeQuizItem(currentQuizItem.quizItem,
        currentQuizItem.quizGroupHeader)
    quiz = newQuiz
    if (wasRemoved) showStatus("Deleted word " + currentQuizItem.prompt)
    testUserWithQuizItemAgain()
  }

  def gotoOptions(v: View) {
    LibaniusActorSystem.sendQuizTo("OptionsScreen", quiz)
    l.log("in QuizScreen, sending quiz with active group headers " +
        quiz.quiz.activeQuizGroupHeaders)
    val optionsScreen = new Intent(getApplicationContext, classOf[OptionsScreen])
    startActivity(optionsScreen)
  }

  private[this] def updateUIAfterChoice(currentQuizItem: QuizItemViewWithChoices,
      choiceButtons: List[Button], clickedButton: Button) {
    updatePrevOptionArea(currentQuizItem)
    val correctResp = currentQuizItem.quizItem.correctResponse.value
    Widgets.setColorsForButtons(choiceButtons, findPrevOptionLabels, correctResp, clickedButton)
  }

  private[this] def optionalIsCompleteText(itemComplete: Boolean,
      numCorrectResponsesRequired: Int): String =
    if (itemComplete)  " (correct " + numCorrectResponsesRequired + " times -- COMPLETE)" else ""

  private[this] def updateUIAfterText(currentQuizItem: QuizItemViewWithChoices,
      userWasCorrect: Boolean, quizItemComplete: Boolean) {
    updatePrevOptionArea(currentQuizItem)

    val feedbackText = new TextView(this)
    feedbackText.setGravity(Gravity.TOP)
    feedbackText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20)

    if (userWasCorrect) {
      feedbackText.setTextColor(Color.GREEN)
      feedbackText.setText("Correct! " +
          optionalIsCompleteText(quizItemComplete, currentQuizItem.numCorrectResponsesRequired))
    } else {
      feedbackText.setTextColor(Color.RED)
      feedbackText.setText("Wrong! It's " + currentQuizItem.quizItem.correctResponse)
    }
    responseInputArea.addView(feedbackText)

    val correctResp = currentQuizItem.quizItem.correctResponse.value
    Widgets.setColorsForPrevOptions(findPrevOptionLabels, correctResp)
  }

  private[this] def updatePrevOptionArea(currentQuizItem: QuizItemViewWithChoices) {
    addPrevQuestionLabel(currentQuizItem)
    addPrevOptionLabels(currentQuizItem)
  }

  private[this] def findPrevOptionLabels: Seq[TextView] =
    (0 until prevOptionArea.getChildCount).map( prevOptionArea.getChildAt(_).
        asInstanceOf[TextView])

  private[this] def constructPrevOptionLabels(currentQuizItem: QuizItemViewWithChoices):
      List[TextView] = {

    val reverseGroupHeader = currentQuizItem.quizGroupHeader.reverse
    val isReverseLookupPossible = quiz.hasQuizGroup(reverseGroupHeader)

    if (isReverseLookupPossible) {
      val prevOptionTexts = currentQuizItem.allChoices.map(prevOptionsText(_,
          currentQuizItem.quizGroupHeader, reverseGroupHeader))
      Widgets.constructPrevOptionLabels(this, prevOptionTexts)
    }
    else Nil
  }

  private[this] def addPrevQuestionLabel(currentQuizItem: QuizItemViewWithChoices) {
    val prevQuestionText = "PREV: " + questionLabel.getText +
        optionalIsCompleteText(currentQuizItem.isComplete,
        currentQuizItem.numCorrectResponsesRequired)
    val prevQuestionLabel = Widgets.constructPrevLabel(this, prevQuestionText)
    prevQuestionArea.removeAllViews()
    prevQuestionArea.addView(prevQuestionLabel)
    // Add spacer labels in order to align the prevQuestionLabel at the top
    val numSpacers = currentQuizItem.allChoices.size - 1
    List.fill(numSpacers)(" ").map(Widgets.constructPrevLabel(this, _)).foreach(
        prevQuestionArea.addView(_))
  }

  private[this] def addPrevOptionLabels(currentQuizItem: QuizItemViewWithChoices) {
    val prevOptionLabels = constructPrevOptionLabels(currentQuizItem)
    prevOptionArea.removeAllViews()
    prevOptionLabels.foreach(prevOptionArea.addView(_))
  }

  private[this] def prevOptionsText(responseOption: String, qgHeader: QuizGroupHeader,
      qgReverseHeader: QuizGroupHeader): String = {

    val values = quiz.findResponsesFor(responseOption, qgReverseHeader) match {
      case Nil => quiz.findPromptsFor(responseOption, qgHeader)
      case values => values
    }
    responseOption + " = " + values.mkString(", ")
  }

  private[this] def processButtonResponse(currentQuizItem: QuizItemViewWithChoices,
      choiceButtons: List[Button], clickedButton: Button) {
    val userResponse = clickedButton.getText.toString
    updateUIAfterChoice(currentQuizItem, choiceButtons, clickedButton)
    val userWasCorrect = currentQuizItem.quizItem.correctResponse.looselyMatches(userResponse)
    updateModel(userResponse, userWasCorrect)
    pauseThenTestAgain(userWasCorrect, currentQuizItem.isComplete)
  }

  private[this] def processTextResponse(currentQuizItem: QuizItemViewWithChoices,
      userResponse: String) {
    val userWasCorrect = quiz.isCorrect(currentQuizItem.quizGroupHeader,
        currentQuizItem.prompt.value, userResponse)

    // The UI is updated before the model for responsiveness.
    val quizItemComplete = userWasCorrect &&
        currentQuizItem.numCorrectResponsesInARow >=
        currentQuizItem.numCorrectResponsesRequired - 1
    updateUIAfterText(currentQuizItem, userWasCorrect, quizItemComplete)
    updateModel(userResponse, userWasCorrect)

    pauseThenTestAgain(userWasCorrect, quizItemComplete)
  }

  private[this] def updateModel(userResponse: String, userWasCorrect: Boolean) {
    updateTimestamps(userWasCorrect)
    Util.stopwatch(quiz = quiz.updateWithUserAnswer(userWasCorrect, currentQuizItem),
        "updating quiz with the user answer")
  }

  private[this] def pauseThenTestAgain(userWasCorrect: Boolean, quizItemComplete: Boolean) {
    val delayMillis =
      if (currentQuizItem.useMultipleChoice) { if (userWasCorrect) 50 else 300 }
      else if (userWasCorrect && !quizItemComplete) 400 else 1800

    val handler = new Handler
    handler.postDelayed(new Runnable() { def run() = testUserWithQuizItemAgain() }, delayMillis)
  }

  private[this] def showScoreAsync() {
    /*
     * Instead of using Android's AsyncTask, use a Scala Future. It's more concise and general,
     * but we need to remind Android to use the UI thread when the result is returned.
     */
    future {
      Util.stopwatch(quiz.scoreSoFar, "calculating score")
    } map { scoreSoFar: BigDecimal =>
      runOnUiThread(new Runnable { override def run() {
        showScore(StringUtil.formatScore(scoreSoFar))
      }})
    }
  }

  private[this] def showSpeed() { speedLabel.setText("Speed: " + answerSpeed + "/min") }
  private[this] def showScore(score: String) { showStatus("Score: " + score) }
}
