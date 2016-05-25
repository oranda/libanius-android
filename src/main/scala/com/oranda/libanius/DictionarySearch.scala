/*
 * Libanius
 * Copyright (C) 2012-2016 James McCabe <james@oranda.com>
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

import android.widget.{Button, LinearLayout, TextView}
import scala.concurrent._
import com.oranda.libanius.util.Util
import java.net.{NoRouteToHostException, SocketException, UnknownHostException}
import com.oranda.libanius.net.RestResponseException
import java.io.IOException
import com.oranda.libanius.model.{SearchResult, Quiz}
import scala.util.Try
import scala.concurrent.future
import android.view.ViewGroup.LayoutParams
import android.app.Activity
import com.oranda.libanius.dependencies.AppDependencyAccess
import com.oranda.libanius.model.quizgroup.QuizGroupHeader
import android.view.View.OnClickListener
import android.view.View
import scala.util.Failure
import scala.util.Success
import scala.concurrent.{future, ExecutionContext}
import ExecutionContext.Implicits.global
import android.content.Context
import com.oranda.libanius.mobile.actors.LibaniusActorSystem
import com.oranda.libanius.model.quizitem.QuizItem


class DictionarySearch(
    quiz: Quiz,
    searchInput: String,
    implicit val statusLabel: TextView,
    searchResultsLayout: LinearLayout,
    widgetFactory: WidgetFactory)
  extends Activity with AppDependencyAccess {

  import DictionarySearch._

  def findAndShowResultsAsync(): Unit = {

    l.log("Searching for results for " + searchInput)
    showStatus("Searching locally and remotely...")

    val searchLocal = future {
      Util.stopwatch(quiz.searchLocalDictionary(searchInput), "search local dictionary")
    }

    val searchRemote = future {
      Util.stopwatch(quiz.searchRemoteDictionary(searchInput), "search remote dictionary")
    }

    searchLocal map {
      searchResultsLocal =>
        showResults(searchResultsLocal, 2)
        setStatus("Searching remotely...")
        searchRemote map { searchResultsRemote =>
          showResults(searchResultsRemote, 1)
        }
    }
  }

  private[this] def statusMessageForException(ex: Throwable) = ex match {
    case _: UnknownHostException | _: SocketException |
         _: RestResponseException | _: NoRouteToHostException => "Internet connection failure"
    case _: IOException => "Error accessing data"
    case _ => "Unknown error"
  }

  private[this] def showResults(results: Try[List[SearchResult]], maxResults: Int): Unit =
    runOnUiThread {
      results match {
        case Success(results) =>
          if (results.isEmpty) setStatus("No results found")
          else showSearchResults(results.slice(0, maxResults))
        case Failure(ex) =>
          statusMessageForException(ex)
      }
    }

  private[this] def showSearchResults(searchResults: List[SearchResult]): Unit = {
    clearStatus()
    for (searchResult <- searchResults)
      addRow(searchResult)
  }

  private[this] def addRow(searchResult: SearchResult): Unit = {
    val searchResultsRow = widgetFactory.newLinearLayout
    val keyWordLabel = widgetFactory.newTextView
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

  private[this] def buttonForValue(
      quizGroupHeader: QuizGroupHeader,
      keyWord: String,
      value: String,
      maxCharsPerValue: Int): Button = {
    val btnTag = widgetFactory.newButton
    btnTag.setLayoutParams(new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT))
    val truncatedValue = value.slice(0, maxCharsPerValue)
    btnTag.setText(clean(truncatedValue))
    val cleanedKeyWord = clean(keyWord)
    val cleanedValue = clean(value)
    btnTag.setOnClickListener(new OnClickListener() {
      def onClick(view: View): Unit = {
        val newQuizItem = QuizItem(cleanedKeyWord, cleanedValue)
        LibaniusActorSystem.sendQuizItem(quizGroupHeader, newQuizItem)
      }
    })
    btnTag
  }

  private[this] def setStatus(text: String) = runOnUiThread { showStatus(text) }
}

object DictionarySearch {
  import com.oranda.libanius.util.StringUtil.RichString
  def clean(word: String) =
    word.removeAll(",").removeAll("\\.").removeAll(";").removeAll(":").trim
}
