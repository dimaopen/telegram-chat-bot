package com.dopenkov.aws

import java.util

import com.amazonaws.handlers.RequestHandler2
import com.amazonaws.services.dynamodbv2.{AmazonDynamoDB, AmazonDynamoDBClientBuilder}
import com.amazonaws.services.lambda.runtime.Context
import com.dopenkov.tgbot.{ChatBot, NameGenerator, PengradTelegram}
import com.dopenkov.tgbot.storage.DynamoDBRepository
import com.google.gson.Gson
import com.pengrad.telegrambot.model.Update
import org.apache.logging.log4j.{LogManager, Logger, ThreadContext}

/**
  *
  * @author Dmitry Openkov
  */
class BotHandler extends RequestHandler2 {
  private val logger: Logger = LogManager.getLogger(getClass)
  private val gson = new Gson()
  private val client: AmazonDynamoDB = AmazonDynamoDBClientBuilder.standard.build
  private val bot = new ChatBot(new PengradTelegram(sys.env("TG_BOT_TOKEN")),
    new DynamoDBRepository(client, sys.env("TABLE_NAME")), new NameGenerator(gson))

  def handleRequest(httpEvent: util.Map[String, Object], context: Context): ApiGatewayResponse = {
    ThreadContext.push("test")
    val body = httpEvent.get("body").asInstanceOf[String]
    logger.info("Got body {}", body)
    try {
      val upd: Update = gson.fromJson(body, classOf[Update])
      val io = bot.newEvent(upd)
      io.unsafeRunSync()
    } catch {
      case e: Throwable => logger.error("Error processing incoming message", e)
    } finally {
      ThreadContext.pop()
    }
    ApiGatewayResponse(200, "")
  }
}

case class ApiGatewayResponse(statusCode: Int, headers: util.Map[String, String], body: String,
                              isBase64Encoded: Boolean)

object ApiGatewayResponse {
  def apply(statusCode: Int, body: String) = new ApiGatewayResponse(statusCode, util.Collections.emptyMap(), body, false)
}