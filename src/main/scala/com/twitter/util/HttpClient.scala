package com.twitter.util

trait HttpClient extends (String => Future[String])