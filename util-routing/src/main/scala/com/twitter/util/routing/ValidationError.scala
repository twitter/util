package com.twitter.util.routing

/**
 * Container class for an error that is encountered as part of validating routes via
 * a [[RouterBuilder]].
 *
 * @param msg The message that explains the error.
 */
case class ValidationError(msg: String)
