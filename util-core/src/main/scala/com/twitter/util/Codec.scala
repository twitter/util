package com.twitter.util

import scala.language.higherKinds

trait EncoderCompanion {

  type Enc[T, S] <: Encoder[T, S]

  /**
   * Encode a value of type T into a serialized value of type S based on the implicit environment.
   */
  def encode[T, S](t: T)(implicit enc: Enc[T, S]): S = enc.encode(t)
}

object Encoder extends EncoderCompanion {
  type Enc[T, S] = Encoder[T, S]
}

/**
 * A base trait for encoders of type T into a serialized form S
 */
trait Encoder[T, S] extends (T => S) {
  /*
   * Encode a T into an S
   */
  def encode(t: T): S

  def apply(t: T) = encode(t)
}

trait DecoderCompanion {

  type Dec[T, S] <: Decoder[T, S]

  /**
   * Decode a value of type T from a serialized value of type S based on the implicit environment.
   */
  def decode[T, S](s: S)(implicit dec: Dec[T, S]): T = dec.decode(s)
}

object Decoder extends DecoderCompanion {
  type Dec[T, S] = Decoder[T, S]
}

/**
 * A base trait for decoders for type T from a serialized form S
 */
trait Decoder[T, S] {
  /*
   * Decode a T from an S
   */
  def decode(s: S): T

  def invert(s: S) = decode(s)
}

object Codec extends EncoderCompanion with DecoderCompanion {
  type Enc[T, S] = Codec[T, S]
  type Dec[T, S] = Codec[T, S]
}

/**
 * A base trait for all Codecs that translate a type T into a serialized form S
 */
trait Codec[T, S] extends Bijection[T, S] with Encoder[T, S] with Decoder[T, S]

object BinaryCodec {
  def encode[T](t: T)(implicit enc: Codec[T, Array[Byte]]): Array[Byte] = enc.encode(t)
  def decode[T](a: Array[Byte])(implicit dec: Codec[T, Array[Byte]]): T = dec.decode(a)
}

