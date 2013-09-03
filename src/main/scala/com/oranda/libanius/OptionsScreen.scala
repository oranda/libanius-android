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

import android.app.{AlertDialog, Activity}
import android.widget._
import android.os.Bundle
import android.view.{KeyEvent, ViewGroup, View}
import android.content.{Context, Intent}
import scala.concurrent.{Await, future, Future, ExecutionContext}
import ExecutionContext.Implicits.global
import com.oranda.libanius.util.{StringSplitterFactoryAndroid, Util}
import com.oranda.libanius.model.wordmapping._
import scala.collection.immutable.Set
import scala.concurrent.duration._
import java.util.concurrent.TimeoutException
import android.widget.CompoundButton.OnCheckedChangeListener
import android.view.inputmethod.{InputMethodManager, EditorInfo}
import android.view.ViewGroup.LayoutParams
import android.view.View.OnClickListener
import scala.util.Try
import com.oranda.libanius.io.AndroidIO
import com.oranda.libanius.dependencies.{LoggerAndroid, Conf, AppDependencies, DataStore}
import com.oranda.libanius.model.{Quiz, QuizGroup, SearchResult, QuizGroupHeader}

class OptionsScreen extends Activity with TypedActivity {

  private[this] lazy val dataStore = DataStore(AndroidIO(ctx = this))

  private[this] lazy val l = AppDependencies.logger

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

  private[this] def quiz = SharedState.quiz

  override def onCreate(savedInstanceState: Bundle) {
    super.onCreate(savedInstanceState)
    Conf.setUp()
    AppDependencies.init(Conf.setUp(),  new LoggerAndroid,
         new DataStore(new AndroidIO(ctx = this)), new StringSplitterFactoryAndroid)
    l.log("OptionsScreen.onCreate")
    // readQuizMetadata
    initGui()
  }

  def initGui() {
    setContentView(R.layout.optionsscreen)
    addQuizGroupCheckBoxes()
    prepareSearchUi()
  }

  def addQuizGroupCheckBoxes() {

    val activeHeaders = activeQuizGroupHeaders
    l.log("activeHeaders: " + activeHeaders)

    def makeQuizGroupCheckBox(ctx: Context, quizGroupHeader: QuizGroupHeader): CheckBox = {
      val checkBox = new CheckBox(getApplicationContext)
      checkBox.setText(quizGroupHeader.promptType + "->" + quizGroupHeader.responseType)
      checkBox.setOnCheckedChangeListener(new OnCheckedChangeListener() {
        override def onCheckedChanged(buttonView: CompoundButton, isChecked: Boolean) {
          if (isChecked)
            qgLoadingFutures += dataStore.loadQuizGroup(quizGroupHeader,
                SharedState.loadedQuizGroups)
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
    availableQuizGroups.foreach(qgHeader => l.log("qg: " + qgHeader))
    checkBoxes = availableQuizGroups.map(qgHeader =>
      (makeQuizGroupCheckBox(ctx = this, qgHeader), qgHeader)).toMap

    checkBoxes.keys.foreach(addCheckBoxToLayout(_))
  }

  def noBoxesChecked = checkBoxes.filter(_._1.isChecked).isEmpty

  def checkedQuizGroupHeaders: Set[QuizGroupHeader] =
    checkBoxes.filter(_._1.isChecked).map(_._2).toSet

  def activeQuizGroupHeaders: Set[QuizGroupHeader] =
    SharedState.quiz.quizGroups.map(_.header)

  def alert(title: String, message: String) {
    new AlertDialog.Builder(OptionsScreen.this).setTitle(title).setMessage(message).
        setPositiveButton("OK", null).show()
  }

  def gotoQuiz(v: View) {
    if (noBoxesChecked)
      alert("Error", "No boxes checked")
    else {
      getQuizReady()
      val intent = new Intent(getBaseContext(), classOf[QuizScreen])
      startActivity(intent)
    }
  }

  def addWordToQuiz(quizGroupHeader: QuizGroupHeader, keyWord: String, value: String) {
    l.log("addWordToQuiz " + keyWord + " " + value)
    SharedState.updateQuiz(quiz.addWordMappingToFrontOfTwoGroups(quizGroupHeader, keyWord, value))
  }

  def getQuizReady() {
    def waitForQuizToLoadWithQuizGroups {
      l.log("waiting for " +  qgLoadingFutures.size + " qgLoadingFutures")
      val loadedQuizGroups = Await.result(Future.sequence(qgLoadingFutures), 10 seconds)
      qgLoadingFutures = Set() // make sure the futures don't run again
      loadedQuizGroups.foreach(SharedState.updateLoadedQuizGroups(_))
      fillQuizWithCheckedQuizGroups()
    }

    Try(waitForQuizToLoadWithQuizGroups).recover {
      case e: TimeoutException => l.logError("Timed out loading quiz groups")
    }
  }

  def fillQuizWithCheckedQuizGroups() {

    def quizGroupForHeader(header: QuizGroupHeader): Option[QuizGroup] =
      SharedState.loadedQuizGroups.find(_.header == header)

    val checkedQuizGroups = checkedQuizGroupHeaders.flatMap(quizGroupForHeader(_))
    l.log("filling quiz with checkedQuizGroups " + checkedQuizGroups.map(_.header))

    SharedState.updateQuiz(Quiz(checkedQuizGroups))
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

  def closeSoftInput() {
    val inputMethodService = getSystemService(Context.INPUT_METHOD_SERVICE).
        asInstanceOf[InputMethodManager]
    inputMethodService.hideSoftInputFromWindow(searchInputBox.getWindowToken, 0)
  }

  def findAndShowResultsAsync() {
    clearResults()
    closeSoftInput()
    status.setText("Searching...")
    getQuizReady()
    val searchInput = searchInputBox.getText.toString

    l.log("search input is " + searchInput)

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

    def convertToSearchResults(pairs: List[(String, WordMappingValueSet)], quizGroup: QuizGroup) =
      pairs.map(pair => SearchResult(quizGroup.header, WordMappingPair(pair._1, pair._2)))

 	  def resultsBeginningWith(input: String): List[SearchResult] =
      quiz.quizGroups.flatMap(quizGroup =>
        convertToSearchResults(quizGroup.dictionary.mappingsForKeysBeginningWith(input),
            quizGroup)).toList

    def resultsContaining(input: String): List[SearchResult] =
      quiz.quizGroups.flatMap(quizGroup => convertToSearchResults(
          quizGroup.dictionary.mappingsForKeysContaining(input), quizGroup)).toList
	  
    var searchResults = List[SearchResult]()
    if (searchInput.length > 2) {
      searchResults = resultsBeginningWith(searchInput)
      if (searchResults.isEmpty)
        searchResults = resultsBeginningWith(searchInput.dropRight(1))
      if (searchResults.isEmpty)
        searchResults = resultsBeginningWith(searchInput.dropRight(2))
      if (searchResults.isEmpty && searchInput.length > 3)
        searchResults = resultsContaining(searchInput)
	  }
 	  searchResults
  }
  
  def addRow(searchResultsRow: LinearLayout,
      searchResults: List[SearchResult], index: Int) {
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