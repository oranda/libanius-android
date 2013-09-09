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
  override def onCreate() {
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