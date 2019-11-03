# PlayFramework scala library to send GELF messages to Graylog

[![Download](https://api.bintray.com/packages/nitrotm/maven/play-gelf/images/download.svg) ](https://bintray.com/nitrotm/maven/play-gelf/_latestVersion)
[![Build Status](https://travis-ci.com/nitrotm/play-gelf.svg?branch=master)](https://travis-ci.com/nitrotm/play-gelf)
[![License](https://img.shields.io/github/license/nitrotm/graylog-plugin-influxdb)](https://www.apache.org/licenses/LICENSE-2.0.txt)

A library to integrate PlayFramework with [Graylog](https://www.graylog.org) GELF inputs.

It uses [play-json](https://github.com/playframework/play-json/) to serialize [GELF messages](https://docs.graylog.org/en/3.1/pages/gelf.html) and [netty](https://netty.io/) to communicate over TCP/TCP+SSL.

Currently other GELF inputs are unsupported (namely AMQP, UDP, Kafka or HTTP).


## Getting Started

To get started, you can add play-gelf as a dependency in your project:

```
resolvers += Resolver.bintrayRepo("nitrotm", "maven")

libraryDependencies += "org.tmsrv" %% "play-gelf" % "1.0.3"
```


## Logback appender

A logback appender can be configured to forward log messages, in logback.xml:

```
  <appender name="GELF" class="org.tmsrv.play.gelf.GELFLogbackAppender">
    <host>localhost</host>
    <port>12201</port>
    <connectTimeout>1000</connectTimeout>
    <retry>5</retry>
    <retryDelay>1000</retryDelay>
    <ssl>true</ssl>
    <keystore>keystore.jks</keystore>
    <keystorePassword>pass</keystorePassword>
    <keystoreAlias>name</keystoreAlias>
    <keystoreAliasPassword>pass</keystoreAliasPassword>
  </appender>

  <appender name="ASYNCGELF" class="ch.qos.logback.classic.AsyncAppender">
    <appender-ref ref="GELF" />
  </appender>

  <root level="INFO">
    <appender-ref ref="ASYNCGELF" />
    ...
  </root>
```


## API

Alternatively, it's possible to send custom GELF messages programmatically:

```
  // a sender over plain TCP
  val gelfSender = new GELFSenderWithRetry(
    new GELFSenderFactory(new GELFSenderTCP("localhost", 12201))
  )

  // a sender over SSL/TCP with client certificate authentication
  val gelfSender = new GELFSenderWithRetry(
    new GELFSenderFactory(
      new GELFSenderSSLOverTCP(
        GELFCryptography.sslContext(
          GELFCryptography.loadKeyStore(new java.io.FileInputStream("keystore.jks"), "pass"),
          "alias",
          "pass"
        ),
        "localhost",
        12201
      )
    )
  )

  // send a simple text message
  gelfSender.send("My text")

  // send a message with custom fields
  gelfSender.send("Message text", Json.obj("field1" -> 5, "field2" -> "ERROR"))

  // record an exception
  val e: Exception = ...

  gelfSender.send(e)

```

### GELFSender

A GELF message sender with following methods:

  - `isActive: Future[Boolean]`: check if the sender is connect/alive.
  - `shutdown(): Future[Unit]`: gracefully shutdown sender.
  - `send(shortMessage: String): Future[Unit]`: send a simple text message.
  - `send(shortMessage: String, fields: JsObject): Future[Unit]`: send a simple text message with custom fields.
  - `send(t: Throwable): Future[Unit]`: send an exception with detailed stack trace.
  - `send(t: Throwable, fields: JsObject): Future[Unit]`: send an exception with stack trace and custom fields.
  - `send(timestamp: Long, shortMessage: String, fullMessage: Option[String], fields: Option[JsObject], level: Option[Int], facility: Option[Int], file: Option[String], line: Option[Int]): Future[Unit]`: send a generic GELF message.

### GELFSenderWithRetry

A GELF message sender that retries sending the message if the underlying sender fails.

  - `factory: GELFSenderFactory`: a factory to spawn senders.
  - `retry: Int`: number of times a message will be resent if the sender fails (defaults to `2`).
  - `delay: Long`: delay between retry in milliseconds (defaults to `1000`).

### GELFSenderFactory

Spawn and maintain a GELF message sender instance.

### GELFSenderTCP

Encodes GELF messages to JSON and transmit over a TCP/IP socket, with the following parameters:

  - `serverHostname: String`: graylog server hostname (defaults to `localhost`).
  - `serverPort: Int`: graylog server GELF input port (defaults to `12201`).
  - `connectTimeout: Int`: connection timeout in milliseconds (defaults to `1000`).
  - `clientName: String`: client/source name (defaults to `InetAddress.getLocalHost().getHostName()`).
  - `version: GELFVersion.Value`: GELF message version (defaults to `GELFVersion.V1_1`).
  - `delimiter: Byte`: GELF message frame delimiter (defaults to `0`).

If the connection was closed (ie. `isActive` returns `false` or `send` fails with an exception), the sender must be discarded and a new one must be created.

### GELFSenderSSLOverTCP

Add TLS to `GELFSenderTCP`, with optional client certificate authentication.

  - `sslContext: SslContext`: netty ssl context (see below).
  - `serverHostname: String`: graylog server hostname (defaults to `localhost`).
  - `serverPort: Int`: graylog server GELF input port (defaults to `12201`).
  - `connectTimeout: Int`: connection timeout in milliseconds (defaults to `1000`).
  - `clientName: String`: client/source name (defaults to `InetAddress.getLocalHost().getHostName()`).
  - `version: GELFVersion.Value`: GELF message version (defaults to `GELFVersion.V1_1`).
  - `delimiter: Byte`: GELF message frame delimiter (defaults to `0`).

To build an `SSLContext` without client authentication:

```
val sslContext = GELFCryptography.sslContext()
```

To build an `SSLContext` with client authentication:

```
val sslContext = GELFCryptography.sslContext(
  GELFCryptography.loadKeyStore(new java.io.FileInputStream("keystore.jks"), "keystorePass"),
  "keyAlias",
  "keyPass"
)
```


## Build

Using sbt:

```
sbt package
```


## Release

Using sbt:

```
sbt release
```
