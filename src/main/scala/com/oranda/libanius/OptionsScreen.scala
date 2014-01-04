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
import android.view.inputmethod.{InputMethodManager, EditorInfo}
import android.view.View.OnClickListener
import android.view.ViewGroup.LayoutParams
import android.view.{KeyEvent, View, ViewGroup}
import android.widget.CompoundButton.OnCheckedChangeListener
import android.widget._
import scala.concurrent.{Await, future, Future, ExecutionContext}
import ExecutionContext.Implicits.global
import com.oranda.libanius.util.Util
import com.oranda.libanius.model.wordmapping._
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

  private[this] lazy val quizGroupLayout: LinearLayout = findView(TR.checkboxesLayout)

  private[this] lazy val searchInputBox: EditText = findView(TR.searchInput) 
  private[this] lazy val searchResults0Row: LinearLayout = findView(TR.searchResults0)
  private[this] lazy val searchResults1Row: LinearLayout = findView(TR.searchResults1)
  private[this] lazy val searchResults2Row: LinearLayout = findView(TR.searchResults2)
  private[this] lazy val status: TextView = findView(TR.status)

  private[this] var checkBoxes = Map[CheckBox, QuizGroupHeader]()

  private[this] lazy val searchResultRows = List(searchResults0Row, searchResults1Row,
      searchResults2Row)

  private[this] var quiz: LazyQuiz = _  // set in onCreate

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
    runOnUiThread(new Runnable { override def run() { initGui() } })
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
      quizGroupLayout.addView(checkBox, params)
    }

    val availableQuizGroups = dataStore.findAvailableQuizGroups
    checkBoxes = availableQuizGroups.map(qgHeader =>
        (makeQuizGroupCheckBox(qgHeader), qgHeader)).toMap

    checkBoxes.keys.foreach(addCheckBoxToLayout(_))
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

  def addWordToQuiz(quizGroupHeader: QuizGroupHeader, keyWord: String, value: String) {
    quiz = quiz.addQuizItemToFrontOfTwoGroups(quizGroupHeader, keyWord, value)
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
    status.setText("Searching...")
    getQuizReady()
    val searchInput = searchInputBox.getText.toString

    /*
     * Instead of using Android's AsyncTask, use a Scala Future. It's more concise and general,
     * but we need to remind Android to use the UI thread when the result is returned.
     */
    future {
      Util.stopwatch(searchDictionary(searchInput), "search dictionary")
    } map { searchResults =>
      runOnUiThread(new Runnable { override def run() { showSearchResults(searchResults) } })
    }

    def showSearchResults(searchResults: List[SearchResult]) {
      if (searchResults.isEmpty)
        status.setText("No results found")
      else {
        status.setText("")
        for (rowNum <- 0 until searchResultRows.size)
          addRow(searchResultRows(rowNum), searchResults, rowNum)
      }
    }
  }

  def searchDictionary(searchInput: String): List[SearchResult] = {
    import Dictionary._  // make special search utilities available

    // Keep trying different ways of searching the dictionary until one finds something.
    if (searchInput.length <= 2) Nil
    else tryUntilResults(List(
      searchFunction { quiz.resultsBeginningWith(searchInput) },
      searchFunction { quiz.resultsBeginningWith(searchInput.dropRight(1)) },
      searchFunction { quiz.resultsBeginningWith(searchInput.dropRight(2)) },
      searchFunction { if (searchInput.length > 3) quiz.resultsContaining(searchInput) else Nil }
    ))
  }

  def addRow(searchResultsRow: LinearLayout, searchResults: List[SearchResult], index: Int) {
    if (searchResults.size > index) {
      val keyWordBox = new TextView(this)
      keyWordBox.setLayoutParams(new LayoutParams(LayoutParams.WRAP_CONTENT,
          LayoutParams.WRAP_CONTENT))
      val keyWord = searchResults(index).keyWord
      keyWordBox.setText(keyWord)
      searchResultsRow.addView(keyWordBox)
             
      val maxNumButtons = 4
      val values = searchResults(index).valueSet.strings.slice(0, maxNumButtons)
      values.foreach { value =>
        val btnTag = new Button(this)
        btnTag.setLayoutParams(new LayoutParams(LayoutParams.WRAP_CONTENT,
            LayoutParams.WRAP_CONTENT))
        btnTag.setText(value)
        btnTag.setOnClickListener(new OnClickListener() {
          def onClick(view: View) {
            addWordToQuiz(searchResults(index).quizGroupHeader, keyWord, value)
          }
        })
        searchResultsRow.addView(btnTag)
      }
    }     
  }

  def clearResults() {
    status.setText("")
    searchResultRows.foreach(_.removeAllViews())
  }
}