/*
 * Libanius-Android
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

package com.oranda.libanius.dependencies

import com.oranda.libanius.io.{PlatformIO}
import com.oranda.libanius.util.{StringSplitterFactory}
import android.app.Application
import android.content.Context
import com.oranda.libanius.mobile.io.{AndroidIO}
import com.oranda.libanius.mobile.util.StringSplitterFactoryAndroid
import com.oranda.libanius.mobile.dependencies.LoggerAndroid

class AppDependenciesOverride extends Application {

  /*
   * The Android infrastucture calls this before anything else in the application.
   */
  override def onCreate(): Unit = {
    super.onCreate()
    AppDependenciesOverride.ctx = getApplicationContext()
  }
}

/*
 * Override application dependencies for Android.
 */
object AppDependenciesOverride extends AppDependencies {
  // Unfortunately this has to be a var, but nothing can access it outside this file.
  private var ctx: Context = _

  /*
   * Because these are lazy vals, they will only be initialized when real application
   * code starts running, i.e. after the onCreate() method for the Android
   * application, i.e. after the ctx var has been initialized.
   */
  lazy val l: Logger            = new LoggerAndroid
  lazy val c: ConfigProvider    = new ConfigProviderDefault // no extra settings for now
  lazy val io: PlatformIO       = new AndroidIO(ctx)
  lazy val dataStore: DataStore = new DataStore(io)
  lazy val stringSplitterFactory: StringSplitterFactory = new StringSplitterFactoryAndroid
}
