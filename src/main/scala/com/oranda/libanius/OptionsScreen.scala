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

import android.app.{AlertDialog, Activity}
import android.content.{Context, Intent}
import android.os.Bundle
import android.util.Log
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
import scala.concurrent.duration._
import java.util.concurrent.TimeoutException
import scala.util.Try
import com.oranda.libanius.model.{Quiz, QuizGroup, SearchResult, QuizGroupHeader}
import com.oranda.libanius.dependencies.AppDependencyAccess
import com.oranda.libanius.mobile.SharedState
import com.oranda.libanius.mobile.actors.LibaniusActorSystem
import com.oranda.libanius.actors.{NoMessage, Message, CollectMessage}

class OptionsScreen extends Activity with TypedActivity with AppDependencyAccess {

  private[this] lazy val quizGroupLayout: LinearLayout = findView(TR.checkboxesLayout)

  private[this] lazy val searchInputBox: EditText = findView(TR.searchInput) 
  private[this] lazy val searchResults0Row: LinearLayout = findView(TR.searchResults0)
  private[this] lazy val searchResults1Row: LinearLayout = findView(TR.searchResults1)
  private[this] lazy val searchResults2Row: LinearLayout = findView(TR.searchResults2)
  private[this] lazy val status: TextView = findView(TR.status)

  private[this] var checkBoxes = Map[CheckBox, QuizGroupHeader]()
  private[this] var qgLoadingFutures: Set[Future[QuizGroup]] = Set()

  private[this] lazy val searchResultRows = List(searchResults0Row, searchResults1Row,
      searchResults2Row)

  private[this] var quiz: Quiz = _  // set in onCreate

  // A cache of quiz groups. It needs to be global because it must be updated when the quiz is.
  // TODO: use function-local memoization instead!
  var loadedQuizGroups = List[QuizGroup]()

  protected[libanius] def updateLoadedQuizGroups(quizGroup: QuizGroup) {
    loadedQuizGroups = loadedQuizGroups.filter(_.header != quizGroup.header)
    loadedQuizGroups :+= quizGroup
  }

  override def onCreate(savedInstanceState: Bundle) {
    super.onCreate(savedInstanceState)

    l.log("OptionsScreen: onCreate ")
    implicit val system: ActorSystem = LibaniusActorSystem.system
    val recipientName = getClass.getSimpleName
    LibaniusActorSystem.mailCentre ! CollectMessage(recipientName,
      actor(new Act {
        become {
          case Message(quizReceived: Quiz) =>
            l.log("received quiz " + quizReceived.numItems + " and setting it in OptionScreen")
            quiz = quizReceived
            runGuiOnUiThread()
          case NoMessage() =>
            quiz = Quiz()
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

    val activeHeaders = activeQuizGroupHeaders

    def makeQuizGroupCheckBox(quizGroupHeader: QuizGroupHeader): CheckBox = {
      val checkBox = new CheckBox(getApplicationContext)
      checkBox.setText(quizGroupHeader.promptType + "->" + quizGroupHeader.responseType)
      checkBox.setOnCheckedChangeListener(new OnCheckedChangeListener() {
        override def onCheckedChanged(buttonView: CompoundButton, isChecked: Boolean) {
          if (isChecked)
            qgLoadingFutures += dataStore.loadQuizGroup(quizGroupHeader, loadedQuizGroups)
        }
      })

      if (activeHeaders.contains(quizGroupHeader))
        checkBox.setChecked(true)
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

  def activeQuizGroupHeaders: Set[QuizGroupHeader] = quiz.quizGroups.map(_.header)

  def alert(title: String, message: String) {
    new AlertDialog.Builder(OptionsScreen.this).setTitle(title).setMessage(message).
        setPositiveButton("OK", null).show()
  }

  def gotoQuiz(v: View) {
    if (noBoxesChecked)
      alert("Error", "No boxes checked")
    else {
      getQuizReady()
      LibaniusActorSystem.sendMessageTo("QuizScreen", quiz)
      l.log("in OptionsScreen, sending quiz")
      val intent = new Intent(getBaseContext(), classOf[QuizScreen])
      startActivity(intent)
    }
  }

  def addWordToQuiz(quizGroupHeader: QuizGroupHeader, keyWord: String, value: String) {
    quiz = quiz.addWordMappingToFrontOfTwoGroups(quizGroupHeader, keyWord, value)
  }

  def getQuizReady() {
    def waitForQuizToLoadWithQuizGroups {
      val loadedQuizGroups = Await.result(Future.sequence(qgLoadingFutures), 10 seconds)
      qgLoadingFutures = Set() // make sure the futures don't run again
      loadedQuizGroups.foreach(updateLoadedQuizGroups(_))
      fillQuizWithCheckedQuizGroups()
    }

    Try(waitForQuizToLoadWithQuizGroups).recover {
      case e: TimeoutException => l.logError("Timed out loading quiz groups")
    }
  }

  def fillQuizWithCheckedQuizGroups() {
    def quizGroupForHeader(header: QuizGroupHeader): Option[QuizGroup] =
      loadedQuizGroups.find(_.header == header)

    val checkedQuizGroups = checkedQuizGroupHeaders.flatMap(quizGroupForHeader(_))
    quiz = Quiz(checkedQuizGroups)
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

  def closeOnScreenKeyboard() {
    val inputMethodService = getSystemService(Context.INPUT_METHOD_SERVICE).
        asInstanceOf[InputMethodManager]
    inputMethodService.hideSoftInputFromWindow(searchInputBox.getWindowToken, 0)
  }

  def findAndShowResultsAsync() {
    clearResults()
    closeOnScreenKeyboard()
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
            addWordToQuiz(searchResults(index).quizGroupHeader, keyWord, value) // TODO: SearchResult
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