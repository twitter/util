/*
 * Copyright 2012 Twitter, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License. You may obtain
 * a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.twitter.logging

import java.util.{logging => javalog}

/**
 * Wrapper around {@link java.util.logging.LogRecord}.
 *
 * The only difference is around log time where messages by Java are formatted using
 * {@link java.text.MessageFormat}, whereas this class formats using a regular
 * {@link java.text.StringFormat}.
 */
class LogRecord(level: javalog.Level, msg: String) extends javalog.LogRecord(level, msg)
