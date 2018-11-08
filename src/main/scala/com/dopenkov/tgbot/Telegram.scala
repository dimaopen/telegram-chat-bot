package com.dopenkov.tgbot

import cats.effect.IO
import com.pengrad.telegrambot.request.SendMessage
import com.pengrad.telegrambot.response.SendResponse

/**
  *
  * @author Dmitry Openkov
  */
trait Telegram {

  def execute(request: SendMessage): IO[SendResponse]

}
