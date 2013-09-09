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

package com.oranda.libanius.android.io

import com.oranda.libanius.model.QuizGroupHeader
import android.content.Context
import java.io._
import com.oranda.libanius.dependencies.{AppDependencyAccess}
import com.oranda.libanius.io.PlatformIO
import com.oranda.libanius.android.R

case class AndroidIO(ctx: Context) extends PlatformIO with AppDependencyAccess {

  if (ctx == null) throw new Exception()

  def readFile(fileName: String): Option[String] =
    if (ctx.getFileStreamPath(fileName).exists)
      Some(readInputStream(fileToInputStream(fileName)))
    else {
      l.logError("File not found: " + fileName)
      None
    }

  def resID(resName: String) = ctx.getResources.getIdentifier(resName, "raw", ctx.getPackageName)

  def resourceToInputStream(resName: String) =
    ctx.getResources.openRawResource(resID(resName))

  def fileToInputStream(fileName: String) = ctx.openFileInput(fileName)

  def readResource(resName: String): Option[String] =
    Some(readInputStream(resourceToInputStream(resName)))

  def save(fileName: String, fileNameBackup: String, strToSave: String) {
    val file = new File(fileName)
    val file2 = new File(fileNameBackup)
    file2.delete()
    file.renameTo(file2) // TODO: check again if this works on Android
    writeToFile(conf.fileQuiz, strToSave)
  }

  def writeToFile(fileName: String, data: String) = {
    val fOut: FileOutputStream = ctx.openFileOutput(fileName, Context.MODE_PRIVATE)
    fOut.write(data.getBytes())  
    fOut.close()
  }

  override def readQgMetadataFromFile(qgFileName: String): Option[QuizGroupHeader] =
    readQgMetadata(fileToInputStream(qgFileName))

  override def readQgMetadataFromResource(qgResName: String): Option[QuizGroupHeader] =
    readQgMetadata(resourceToInputStream(qgResName))

  override def findQgFileNamesFromFilesDir =
    ctx.getFilesDir.listFiles.filter(_.getName.endsWith(".qg")).map(_.getName)

  override def findQgFileNamesFromResources =
    classOf[R.raw].getFields.map(_.getName).filter(_.startsWith("qg"))

}
