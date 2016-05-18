/*
 * Libanius
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

package com.oranda.libanius.mobile.actors

import akka.actor.Actor
import android.content.Context
import android.media.{SoundPool, AudioManager}
import com.oranda.libanius.R
import com.oranda.libanius.dependencies.AppDependencyAccess
import SoundPlayer._

class SoundPlayer(implicit ctx: Context) extends Actor with AppDependencyAccess {

  private[this] val audioManager: AudioManager =
    ctx.getApplicationContext.getSystemService(Context.AUDIO_SERVICE).asInstanceOf[AudioManager]
  private[this] implicit val soundPool: SoundPool = new SoundPool(10, AudioManager.STREAM_MUSIC, 0)
  private[this] var soundPoolMap = Map[SoundSampleName, SoundSampleData]()

  override def receive = {
    case Load() => loadSounds()
    case Play(soundSampleId: SoundSampleName) => play(soundSampleId)
    case _ => l.logError("SoundPlayer received an unknown command")
  }

  def loadSounds(): Unit = {
    soundPoolMap += (CORRECT -> SoundSampleData.load(R.raw.correct0))
    soundPoolMap += (INCORRECT -> SoundSampleData.load(R.raw.incorrect0))
    soundPool.setOnLoadCompleteListener(new SoundPool.OnLoadCompleteListener {
      def onLoadComplete(soundPool: SoundPool, sampleId: Int, status: Int): Unit = {
        val soundSample = soundPoolMap.find(_._2.soundSample == sampleId) foreach {
          case (name: SoundSampleName, data: SoundSampleData) =>
            soundPoolMap += (name -> data.setLoaded)
        }
      }
    })
  }

  def play(soundSampleId: SoundSampleName): Unit =
    soundPoolMap.get(soundSampleId).foreach { soundSampleData =>
      if (soundSampleData.isLoaded) {
        val curVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        soundPool.play(soundSampleData.soundSample, curVolume, curVolume, 1, 0, 1f)
      }
  }
}

object SoundPlayer {
  case class Load()
  case class Play(soundSample: SoundSampleName)

  abstract class SoundSampleName
  case object CORRECT extends SoundSampleName
  case object INCORRECT extends SoundSampleName

  case class SoundSampleData(soundSample: Integer, isLoaded: Boolean = false) {
    def setLoaded = copy(isLoaded = true)
  }

  object SoundSampleData {
    def load(soundSample: Integer)(implicit soundPool: SoundPool, ctx: Context): SoundSampleData =
      SoundSampleData(soundPool.load(ctx, soundSample, 1))
  }
}