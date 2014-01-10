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

import android.widget.{LinearLayout, TextView, Button}
import android.util.TypedValue
import android.view.{View, Gravity, ViewGroup}
import android.view.ViewGroup.LayoutParams
import android.graphics.{Typeface, Color}
import android.content.Context
import android.os.IBinder
import android.view.inputmethod.InputMethodManager
import com.oranda.libanius.dependencies.AppDependencyAccess

/**
 * Utility class for manipulating UI controls.
 */
object Widgets extends AppDependencyAccess {

  def constructChoiceButtons(androidContext: Context, choiceValues: List[String]): List[Button] = {

    def constructChoiceButton(choiceValue: String): Button = {

      val choiceButton = new Button(androidContext)

      def calcPixelsForDp(dp: Int): Int =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp,
            androidContext.getResources.getDisplayMetrics).toInt

      val params = new LinearLayout.LayoutParams( LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT );
      params.setMargins(0, 0, 0, calcPixelsForDp(25))
      choiceButton.setLayoutParams(params)

      choiceButton.setGravity(Gravity.CENTER)
      choiceButton.setTextColor(Color.BLACK)
      choiceButton.setTextSize(TypedValue.COMPLEX_UNIT_SP, 30)
      choiceButton.setBackgroundColor(Color.LTGRAY)
      choiceButton.setText(choiceValue)

      choiceButton
    }

    choiceValues.map(constructChoiceButton(_))
  }

  def constructPrevLabel(androidContext: Context, text: String): TextView = {
    val prevOptionLabel = new TextView(androidContext)
    prevOptionLabel.setText(text)
    prevOptionLabel.setSingleLine(true)
    val params = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
    prevOptionLabel.setLayoutParams(params)
    prevOptionLabel.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14)
    prevOptionLabel.setTextColor(Color.WHITE)
    val fontFamily: String = null
    prevOptionLabel.setTypeface(Typeface.create(fontFamily, Typeface.ITALIC))
    prevOptionLabel
  }

  def constructPrevOptionLabels(androidContext: Context, values: List[String]): List[TextView] =
    values.map(constructPrevLabel(androidContext, _))


  protected[libanius] def setColorsForButtons(choiceButtons: List[Button],
      prevOptionLabels: Seq[TextView], correctResponse: String, clickedButton: Button) {

    choiceButtons.find(_.getText == correctResponse).foreach { correctButton =>
      choiceButtons.foreach { button =>
        setButtonColorOnResponse(button, correctButton, clickedButton)
      }
    }
    setColorsForPrevOptions(prevOptionLabels, correctResponse)
  }

  protected[libanius] def setButtonColorOnResponse(optionButton: Button,
      CORRECT_BUTTON: Button, CLICKED_BUTTON: Button) {

    optionButton match {
      case CORRECT_BUTTON => l.log("Setting button for " + optionButton.getText + " to green")
                             optionButton.setBackgroundColor(Color.GREEN)
      case CLICKED_BUTTON => l.log("Setting button for " + optionButton.getText + " to red")
                             optionButton.setBackgroundColor(Color.RED)
      case _ =>
    }
  }

  protected[libanius] def setLabelColorOnResponse(optionButton: Button,
      prevOptionLabel: TextView, CORRECT_BUTTON: Button, CLICKED_BUTTON: Button) {

    optionButton match {
      case CORRECT_BUTTON => prevOptionLabel.setTextColor(Color.GREEN)
      case CLICKED_BUTTON => prevOptionLabel.setTextColor(Color.RED)
      case _ =>
    }
  }

  protected[libanius] def setColorsForPrevOptions(prevOptionLabels: Seq[TextView],
      correctResponse: String) {
    val correctLabel = prevOptionLabels.find(_.getText.toString.startsWith(correctResponse + " ="))
    correctLabel.foreach(_.setTextColor(Color.GREEN))
  }

  protected[libanius] def closeOnscreenKeyboard(ctx: Context, windowToken: IBinder) {
    val inputMethodService = ctx.getSystemService(Context.INPUT_METHOD_SERVICE).
        asInstanceOf[InputMethodManager]
    inputMethodService.hideSoftInputFromWindow(windowToken, 0)
  }

  protected[libanius] def showOnscreenKeyboard(ctx: Context, view: View) {
    val inputMethodService = ctx.getSystemService(Context.INPUT_METHOD_SERVICE).
        asInstanceOf[InputMethodManager]
    inputMethodService.showSoftInput(view, 0)
  }
}
