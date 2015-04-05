/*
 * Libanius
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
  private[this] val soundPool: SoundPool = new SoundPool(10, AudioManager.STREAM_MUSIC, 0)
  private[this] var soundPoolMap = Map[SoundSample, Int]()
  private[this] var correctSoundLoaded = false
  private[this] var incorrectSoundLoaded = false

  override def receive = {
    case Load() =>
      loadSound()
    case Play(soundSample: SoundSample) =>
      val curVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
      soundPool.play(soundPoolMap(soundSample), curVolume, curVolume, 1,  0, 1f)
    case _ =>
      l.logError("SoundPlayer received an unknown command")
  }

  def loadSound() {
    soundPoolMap += (CORRECT -> soundPool.load(ctx, R.raw.correct0, 1))
    soundPoolMap += (INCORRECT -> soundPool.load(ctx, R.raw.incorrect0, 1))
    soundPool.setOnLoadCompleteListener(new SoundPool.OnLoadCompleteListener {
      def onLoadComplete(soundPool: SoundPool, sampleId: Int, status: Int) {
        sampleId match {
          case R.raw.correct0 =>
            correctSoundLoaded = true
          case R.raw.incorrect0 =>
            incorrectSoundLoaded = true
          case _ =>
            l.logError("sampleId not recognized: " + sampleId)
        }
      }
    })
  }
}

object SoundPlayer {
  case class Load()
  case class Play(soundSample: SoundSample)

  abstract class SoundSample()
  case object CORRECT extends SoundSample
  case object INCORRECT extends SoundSample
}