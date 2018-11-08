package com.dopenkov.tgbot.storage

import cats.effect.IO
import com.dopenkov.tgbot.model.{Chatter, Room}

/**
  *
  * @author Dmitry Openkov
  */
trait Repository {
  def updateChatter(chatter: Chatter): IO[Chatter]

  def findChatter(userId: String): IO[Option[Chatter]]

  def listChatters(room: String): IO[List[Chatter]]

  def listRooms(): IO[List[Room]]

  def findRoom(roomName: String): IO[Option[Room]]

  def incNumberOfChatters(room: Room, inc: Int): IO[Room]
}


