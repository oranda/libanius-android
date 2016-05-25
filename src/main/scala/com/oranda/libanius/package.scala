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

package com.oranda

import android.widget.{Button, LinearLayout, TextView}
import android.content.Context

package object libanius {

  implicit def toRunnable[A](f: => A): Runnable = new Runnable() { def run() = f }

  def showStatus(text: String)(implicit statusLabel: TextView): Unit =
    statusLabel.setText(text)

  def clearStatus()(implicit statusLabel: TextView): Unit =
    showStatus("")

  class WidgetFactory(ctx: Context) {
    def newLinearLayout = new LinearLayout(ctx)
    def newTextView = new TextView(ctx)
    def newButton = new Button(ctx)
  }
}
