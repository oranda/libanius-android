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

package com.oranda.libanius.mobile.dependencies

import android.util.Log
import com.oranda.libanius.dependencies.Logger

class LoggerAndroid extends Logger {

  override def logImpl(message: String, module: String = "Libanius", t: Option[Throwable] = None): Unit =
    t match {
      case Some(t) => Log.d(module, message, t)
      case _ => Log.d(module, message)
    }
}