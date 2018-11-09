package com.dopenkov.tgbot.storage

import cats.effect.IO
import com.dopenkov.tgbot.model.{Chatter, ChatMessage, Room}

/**
  *
  * @author Dmitry Openkov
  */
trait Repository {
  def updateChatter(chatter: Chatter): IO[Chatter]

  def findChatter(userId: Int): IO[Option[Chatter]]

  def listChatters(room: String): IO[List[Chatter]]

  def listRooms(): IO[List[Room]]

  def findRoom(roomName: String): IO[Option[Room]]

  def incNumberOfChatters(room: Room, inc: Int): IO[Room]

  def newMessage(msg: ChatMessage): IO[ChatMessage]
}


