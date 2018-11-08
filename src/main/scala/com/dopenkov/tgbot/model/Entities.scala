package com.dopenkov.tgbot.model

import java.time.Instant

import com.dopenkov.tgbot.model.ChatterState.ChatterState

/**
  *
  * @author Dmitry Openkov
  */
case class Chatter(id: String, nick: String, state: ChatterState, realName: String, room: Option[String], chatId: Long)

object ChatterState extends Enumeration {
  type ChatterState = Value
  val NickChanging, General = Value
}

case class Room(name: String, numberOfChatters: Int)
