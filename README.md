# AWS Telegram "Anonymous chat" Bot in Scala

## Description
Telegram bot allows to chat the telegram users anonymously. It is written in Scala and supposed to be hosted on AWS 
as a lambda function and using DynamoDB as a storage.

## How to build and deploy
You need [sbt](https://www.scala-sbt.org/) for building the project 
and [serverless toolkit](https://serverless.com/) for deploying it to AWS. 

1. clone this project
```shell
git clone <this project url>
```
2. Package it with sbt
```shell
sbt universal:packageBin
```
3. Deploy it with serverless
```shell
sls deploy
```
After deploying you get the following information from sls cli
```
Service Information
service: chat-tg-service
stage: dev
region: us-east-1
stack: chat-tg-service-dev
api keys:
  None
endpoints:
  POST - https://XXXXXXXX.execute-api.us-east-1.amazonaws.com/dev/hook
functions:
  tg-web-hook: chat-tg-service-dev-tg-web-hook
```

The POST endpoint is the telegram webhook for incoming messages.

## Populating the database
You need to create several rooms for your users so that they can choose a room to chat.
That can be done via aws cli using the predefined data file located at ./resources/rooms.json.
Use the following command.

```shell
aws --region=us-east-1 dynamodb batch-write-item --request-items file://resources/rooms.json
``` 

## Setting up your telegram bot

You need to create a telegram bot ([https://core.telegram.org/bots](https://core.telegram.org/bots) and
[set a webhook](https://core.telegram.org/bots/api#setWebhook) to receive incoming updates. 