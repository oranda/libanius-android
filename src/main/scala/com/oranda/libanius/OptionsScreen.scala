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
import android.content.{Intent}
import android.os.Bundle
import android.view.inputmethod.{EditorInfo}
import android.view.View.OnClickListener
import android.view.ViewGroup.LayoutParams
import android.view.{KeyEvent, View, ViewGroup}
import android.widget.CompoundButton.OnCheckedChangeListener
import android.widget._
import scala.concurrent.{future, ExecutionContext}
import ExecutionContext.Implicits.global
import com.oranda.libanius.util.Util
import scala.collection.immutable.Set
import java.util.concurrent.TimeoutException
import scala.util.Try
import com.oranda.libanius.model._
import com.oranda.libanius.dependencies.AppDependencyAccess
import com.oranda.libanius.mobile.actors.LibaniusActorSystem
import com.oranda.libanius.actors.{NoMessage, Message, CollectMessage}
import com.oranda.libanius.model.SearchResult
import com.oranda.libanius.model.quizgroup.QuizGroupHeader

class OptionsScreen extends Activity with TypedActivity with AppDependencyAccess {

  private[this] lazy val quizGroupsLayout: LinearLayout = findView(TR.quizGroupsLayout)

  private[this] lazy val searchInputBox: EditText = findView(TR.searchInput)
  private[this] lazy val searchResultsLayout: LinearLayout = findView(TR.searchResultsLayout)


  private[this] lazy val status: TextView = findView(TR.status)

  private[this] var checkBoxes = Map[CheckBox, QuizGroupHeader]()

  private[this] var quiz: LazyQuiz = _  // set in onCreate

  implicit def toRunnable[A](f: => A): Runnable = new Runnable() { def run() = f }

  override def onCreate(savedInstanceState: Bundle) {
    super.onCreate(savedInstanceState)

    l.log("OptionsScreen: onCreate ")
    implicit val system: ActorSystem = LibaniusActorSystem.system
    val recipientName = getClass.getSimpleName
    LibaniusActorSystem.mailCentre ! CollectMessage(recipientName,
      actor(new Act {
        become {
          case Message(quizReceived: LazyQuiz) =>
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

  def runGuiOnUiThread() {
    runOnUiThread { initGui() }
  }

  def initGui() {
    setContentView(R.layout.optionsscreen)
    addQuizGroupCheckBoxes()
    prepareSearchUi()
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
      l.log("in OptionsScreen, sending quiz")
      val intent = new Intent(getBaseContext(), classOf[QuizScreen])
      startActivity(intent)
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

  def findAndShowResultsAsync() {
    clearResults()
    Widgets.closeOnscreenKeyboard(this, searchInputBox.getWindowToken)

    getQuizReady()
    val searchInput = searchInputBox.getText.toString

    l.log("Searching for results for " + searchInput)
    status.setText("Searching locally and remotely...")

    val searchLocal = future {
      Util.stopwatch(quiz.searchLocalDictionary(searchInput), "search local dictionary")
    }

    val searchRemote = future {
      Util.stopwatch(quiz.searchRemoteDictionary(searchInput), "search remote dictionary")
    }

    def showResults(searchResults: List[SearchResult], maxResults: Int) {
      runOnUiThread { showSearchResults(searchResults.slice(0, maxResults)) }
    }

    def setStatus(text: String) = runOnUiThread { status.setText(text) }

    searchLocal map {
      searchResultsLocal =>
        showResults(searchResultsLocal, 2)
        setStatus("Searching remotely...")
        searchRemote map { searchResultsRemote =>
          showResults(searchResultsRemote, 1)
          if (searchResultsLocal.isEmpty && searchResultsRemote.isEmpty)
            setStatus("No results found")
        }
    }
  }

  private[this] def showSearchResults(searchResults: List[SearchResult]) {
    status.setText("")
    for (searchResult <- searchResults)
      addRow(searchResult)
  }

  import com.oranda.libanius.util.StringUtil.RichString
  def clean(word: String) = word.removeAll(",").removeAll("\\.").removeAll(";").removeAll(":").trim

  private[this] def addRow(searchResult: SearchResult) {
    val searchResultsRow = new LinearLayout(this)
    val keyWordLabel = new TextView(this)
    keyWordLabel.setLayoutParams(new LayoutParams(LayoutParams.WRAP_CONTENT,
        LayoutParams.WRAP_CONTENT))
    val keyWord = searchResult.keyWord
    keyWordLabel.setText(clean(keyWord.slice(0, 20)))
    searchResultsRow.addView(keyWordLabel)

    /*
     * Assume roughly 35 characters is allowed as a total for all value buttons.
     * Show either 3 buttons or 4 buttons depending on how many characters are in the results.
     */
    val maxCharsForAllValues = 35
    val allValues = searchResult.valueSet.strings
    val numCharsInFirstResults = (0 /: allValues.slice(0, 4))(_ + _.length)
    val maxNumButtons = if (numCharsInFirstResults > maxCharsForAllValues) 3 else 4

    val values = allValues.slice(0, maxNumButtons)
    val maxCharsPerValue = maxCharsForAllValues / values.size
    values.foreach { value =>
      val btn = buttonForValue(searchResult.quizGroupHeader, keyWord, value, maxCharsPerValue)
      searchResultsRow.addView(btn)
    }
    searchResultsLayout.addView(searchResultsRow)
  }

  private[this] def buttonForValue(quizGroupHeader: QuizGroupHeader, keyWord: String,
      value: String, maxCharsPerValue: Int): Button = {
    val btnTag = new Button(this)
    btnTag.setLayoutParams(new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT))
    val truncatedValue = value.slice(0, maxCharsPerValue)
    btnTag.setText(clean(truncatedValue))
    btnTag.setOnClickListener(new OnClickListener() {
      def onClick(view: View) { addWordToQuiz(quizGroupHeader, keyWord, value) }
    })
    btnTag
  }

  private[this] def addWordToQuiz(quizGroupHeader: QuizGroupHeader, keyWord: String,
      value: String) {
    val cleanedKeyWord = clean(keyWord)
    val cleanedValue = clean(value)
    quiz = quiz.addQuizItemToFrontOfTwoGroups(quizGroupHeader, cleanedKeyWord, cleanedValue)
    status.setText(cleanedKeyWord + " - " + cleanedValue + " added to front of quiz")
  }

  private[this] def clearResults() {
    status.setText("")
    searchResultsLayout.removeAllViews()
  }
}