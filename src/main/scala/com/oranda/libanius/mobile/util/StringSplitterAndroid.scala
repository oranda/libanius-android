/*
 * Libanius-Android
 * Copyright (C) 2012-2015 James McCabe <james@oranda.com>
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

package com.oranda.libanius.mobile.util

import android.text.TextUtils
import java.lang.Character
import com.oranda.libanius.util.StringSplitter

class StringSplitterAndroid(_char: Character) extends StringSplitter(_char) {
  
  val splitter = new TextUtils.SimpleStringSplitter(_char)
  
  override def setString(str: String) {
    splitter.setString(str)
  }
  
  override def hasNext: Boolean = splitter.hasNext
  override def next: String = splitter.next
}