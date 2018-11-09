package com.dopenkov.tgbot.storage

import java.time.Instant

import cats.effect.IO
import com.dopenkov.tgbot.model.{ChatMessage, Chatter, Room}
import com.google.gson.annotations.Since

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

  def findMessages(room: String, since: Instant, limit: Int): IO[List[ChatMessage]]
}


