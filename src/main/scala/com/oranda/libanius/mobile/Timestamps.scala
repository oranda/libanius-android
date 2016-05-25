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

package com.oranda.libanius.mobile

/*
 * This class is used to help calculate the speed of user response by keeping a
 * list of timestamps of correct responses.
 */
trait Timestamps {

  private[this] var timestampsLastCorrectAnswers = List[Long]()

  def updateTimestamps(thereJustOccurredACorrectAnswer: Boolean): Unit =
    if (thereJustOccurredACorrectAnswer) {
      val currentTime = System.currentTimeMillis
      timestampsLastCorrectAnswers ::= currentTime
      /*
       * Purge timestamps older than one minute. This leaves the length of the
       * list as a measure of the number of correct answers per minute.
       */
      timestampsLastCorrectAnswers = timestampsLastCorrectAnswers.filter(_ > currentTime - 60000)
    }

  def answerSpeed = timestampsLastCorrectAnswers.size
}
