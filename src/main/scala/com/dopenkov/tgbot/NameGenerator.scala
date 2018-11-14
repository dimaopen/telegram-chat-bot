package com.dopenkov.tgbot

import cats.effect.IO
import com.google.gson.Gson
import okhttp3._

/**
  *
  * @author Dmitry Openkov
  */
class NameGenerator(val gson: Gson) {
  private val URL = "https://api.codetunnel.net/random-nick"
  private lazy val client: OkHttpClient = new OkHttpClient()

  def createNickname: IO[Either[String, String]] = {
    val JSON = MediaType.parse("application/json; charset=utf-8")
    val body = RequestBody.create(JSON, """{"sizeLimit": 8}""")
    val request = new Request.Builder().url(URL).post(body).build
    val call: Call = client.newCall(request)
    IO {
      try {
        Right(call.execute)
      } catch {
        case e: Exception =>
          Left(e.getMessage)
      }
    }.map {
      either =>
        either.map {
          response =>
            try {
              Right(gson.fromJson(response.body().string(), classOf[NicknameResponse]))
            } catch {
              case e: Exception => Left(e.getMessage)
            } finally {
              response.close()
            }
        }
    }.map(_.joinRight).map {
      either => either.map(resp => if (resp.success) Right(resp.nickname) else Left(resp.error.message))
    }.map(_.joinRight)
  }

  case class NicknameResponse(success: Boolean, nickname: String, error: GeneratorError, time: Float)

  case class GeneratorError(code: String, message: String)

}

