package com.example.domain

import com.typesafe.config.Config

case class TwitterConf(key: String, secret: String, token: String, tokenSecret: String, bearer: String)

object TwitterConf {
  def fromConfig(config: Config) = TwitterConf(
    config.getConfig("twitter").getString("key"),
    config.getConfig("twitter").getString("secret"),
    config.getConfig("twitter").getString("token"),
    config.getConfig("twitter").getString("tokenSecret"),
    config.getConfig("twitter").getString("bearer"),
  )
}
