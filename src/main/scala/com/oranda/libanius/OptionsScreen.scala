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

import android.app.{AlertDialog, Activity}
import android.content.{Context, Intent}
import android.os.Bundle
import android.view.inputmethod.{EditorInfo}
import android.view.{KeyEvent, View, ViewGroup}
import android.widget.CompoundButton.OnCheckedChangeListener
import android.widget._
import scala.concurrent.{future, ExecutionContext}
import ExecutionContext.Implicits.global
import scala.collection.immutable.Set
import java.util.concurrent.TimeoutException
import scala.util.Try
import com.oranda.libanius.model._
import com.oranda.libanius.dependencies.AppDependencyAccess
import com.oranda.libanius.mobile.actors._
import com.oranda.libanius.model.quizgroup.QuizGroupHeader
import scala.concurrent.{future, ExecutionContext}
import ExecutionContext.Implicits.global
import LibaniusActorSystem._

class OptionsScreen extends Activity with TypedActivity with AppDependencyAccess {

  private[this] lazy val quizGroupsLayout: LinearLayout = findView(TR.quizGroupsLayout)

  private[this] lazy val searchInputBox: EditText = findView(TR.searchInput)
  private[this] lazy val searchResultsLayout: LinearLayout = findView(TR.searchResultsLayout)

  private[this] lazy implicit val statusLabel: TextView = findView(TR.status)

  private[this] var checkBoxes = Map[CheckBox, QuizGroupHeader]()

  private[this] var quiz: LazyQuiz = LazyQuiz(Quiz())

  override def onCreate(savedInstanceState: Bundle) {
    super.onCreate(savedInstanceState)

    l.log("OptionsScreen: onCreate ")
    implicit val system: ActorSystem = LibaniusActorSystem.system
    val recipientName = getClass.getSimpleName
    LibaniusActorSystem.mailCentre ! CollectMessage(recipientName,
      actor(new Act {
        become {
          case ObjectMessage(quizReceived: LazyQuiz) =>
            l.log("received quiz " + quizReceived.numQuizItems + " and setting it in OptionScreen")
            quiz = quizReceived
            runGuiOnUiThread()
          case NoMessage() =>
            quiz = LazyQuiz(Quiz())
            runGuiOnUiThread()
        }
      })
    )
  }

  /*
  val subscriber = system.actorOf(Props(new Actor {
    def receive = {
      case MessageEvent(QUIZ_CHANNEL, QuizMessage(_, _, quizReceived)) =>
        l.log("OptionsScreen received quiz " + quizReceived.numQuizItems + " and sets it")
        quiz = quizReceived
        l.log("OptionsScreen unsubscribed from QUIZ_CHANNEL")
      case x =>
        l.log(s"OptionsScreen received something strange: $x")
    }
  }))

  override def onCreate(savedInstanceState: Bundle) {
    super.onCreate(savedInstanceState)

    l.log("OptionsScreen: onCreate ")


    LibaniusActorSystem.appActorEventBus.subscribe(subscriber, QUIZ_CHANNEL)
  }

  override def onStart() {
    super.onStart()
    l.log("OptionsScreen: onStart")
    runOnUiThread { initGui() }
  }
  */

  def runGuiOnUiThread() {
    runOnUiThread { initGui() }
  }

  def initGui() {
    setContentView(R.layout.optionsscreen)
    addQuizGroupCheckBoxes()
    prepareSearchUi()
  }

  override def onPause() {
    super.onPause()
    saveQuiz
  }

  private[this] def saveQuiz() {
    try { showStatus("Saving quiz data...") } catch { case e: Exception => /* ignore NPEs */ }
    future { dataStore.saveQuiz(quiz) }
  }

  def addQuizGroupCheckBoxes() {

    def makeQuizGroupCheckBox(qgHeader: QuizGroupHeader): CheckBox = {
      val checkBox = new CheckBox(getApplicationContext)
      checkBox.setText(qgHeader.promptType + "->" + qgHeader.responseType)
      checkBox.setOnCheckedChangeListener(new OnCheckedChangeListener() {
        override def onCheckedChanged(buttonView: CompoundButton, isChecked: Boolean) {
          quiz = if (isChecked) quiz.activate(qgHeader) else quiz.deactivate(qgHeader)
        }
      })
      checkBox.setChecked(quiz.isActive(qgHeader))
      checkBox
    }

    def addCheckBoxToLayout(checkBox: CheckBox) = {
      val params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.FILL_PARENT,
          ViewGroup.LayoutParams.WRAP_CONTENT)
      params.leftMargin = 20
      params.bottomMargin = 25
      quizGroupsLayout.addView(checkBox, params)
    }

    val availableQuizGroups = dataStore.findAvailableQuizGroups
    checkBoxes = availableQuizGroups.map(qgHeader =>
        (makeQuizGroupCheckBox(qgHeader), qgHeader)).toMap

    val maxCheckBoxes = 5
    checkBoxes.keys.slice(0, maxCheckBoxes).foreach(addCheckBoxToLayout(_))
  }

  def noBoxesChecked = checkBoxes.filter(_._1.isChecked).isEmpty

  def checkedQuizGroupHeaders: Set[QuizGroupHeader] =
    checkBoxes.filter(_._1.isChecked).map(_._2).toSet

  def alert(title: String, message: String) {
    new AlertDialog.Builder(OptionsScreen.this).setTitle(title).setMessage(message).
        setPositiveButton("OK", null).show()
  }

  def gotoQuiz(v: View) {
    if (noBoxesChecked)
      alert("Error", "No boxes checked")
    else {
      getQuizReady()
      LibaniusActorSystem.sendQuizTo("QuizScreen", quiz)
      l.log("in OptionsScreen: sending quiz")
      val intent = new Intent(getBaseContext(), classOf[QuizScreen])
      startActivity(intent)
      /*
      val intent = new Intent(getBaseContext(), classOf[QuizScreen])
      startActivity(intent)
      l.log("started QuizScreen activity... getting quiz ready to send")
      getQuizReady()
      l.log("in OptionsScreen, sending quiz")
      // Unsubscribe QuizScreen, and assume OptionScreen is subscribed to quiz messages
      appActorEventBus.unsubscribe(subscriber, QUIZ_CHANNEL)
      LibaniusActorSystem.sendQuiz(quiz)
      */
    }
  }

  def getQuizReady() {
    Try(quiz = quiz.waitForQuizGroupsToLoad(checkedQuizGroupHeaders)).recover {
      case e: TimeoutException => l.logError("Timed out loading quiz groups")
    }
  }

  def prepareSearchUi() {
    searchInputBox.setOnEditorActionListener(new TextView.OnEditorActionListener() {
      override def onEditorAction(searchInputBox: TextView, actionId: Int,
          event: KeyEvent): Boolean = {
        if (actionId == EditorInfo.IME_ACTION_DONE || event.getAction == KeyEvent.ACTION_DOWN)
          findAndShowResultsAsync()
        true
      }
    })
  }

  private[this] def findAndShowResultsAsync() {
    clearResults()
    Widgets.closeOnscreenKeyboard(this, searchInputBox.getWindowToken)
    getQuizReady()
    val searchInput = searchInputBox.getText.toString
    val dictionarySearch = new DictionarySearch(quiz, searchInput, statusLabel,
        searchResultsLayout, new WidgetFactory(this), addWordToQuiz)
    dictionarySearch.findAndShowResultsAsync()
  }

  private[this] def addWordToQuiz(quizGroupHeader: QuizGroupHeader, keyWord: String,
      value: String) {
    quiz = quiz.addQuizItemToFrontOfTwoGroups(quizGroupHeader, keyWord, value)
    showStatus(keyWord + " - " + value + " added to front of quiz")
  }

  private[this] def clearResults() {
    clearStatus()
    searchResultsLayout.removeAllViews()
  }
}