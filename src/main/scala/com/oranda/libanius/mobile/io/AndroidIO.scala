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

package com.oranda.libanius.mobile.io

import android.content.Context
import java.io._
import com.oranda.libanius.dependencies.{AppDependencyAccess}
import com.oranda.libanius.io.PlatformIO
import com.oranda.libanius.R
import com.oranda.libanius.model.quizgroup.QuizGroupHeader

case class AndroidIO(ctx: Context) extends PlatformIO with AppDependencyAccess {

  if (ctx == null) throw new Exception()

  def readFile(fileName: String): Option[String] =
    if (ctx.getFileStreamPath(fileName).exists)
      Option(readInputStream(fileToInputStream(fileName)))
    else {
      l.logError("File not found: " + fileName)
      None
    }

  def resID(resName: String) = ctx.getResources.getIdentifier(resName, "raw", ctx.getPackageName)

  def resourceToInputStream(resName: String) = ctx.getResources.openRawResource(resID(resName))

  def fileToInputStream(fileName: String) = ctx.openFileInput(fileName)

  def readResource(resName: String): Option[String] =
    Option(readInputStream(resourceToInputStream(resName)))

  def save(fileName: String, fileNameBackup: String, strToSave: String): Unit = {
    val file = new File(fileName)
    val file2 = new File(fileNameBackup)
    file2.delete()
    file.renameTo(file2)
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
    ctx.getFilesDir.listFiles.filter(_.getName.endsWith(".qgr")).map(_.getName)

  override def findQgFileNamesFromResources =
    classOf[R.raw].getFields.map(_.getName).filter(_.startsWith("qgr"))

}
