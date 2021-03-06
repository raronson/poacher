package com.ambiata.poacher.mr

import org.apache.thrift.protocol.TCompactProtocol

/**
 * This should be the _only_ use of Thrift serialisation/deserialisation.
 * There are some _serious_ dangers lurking (such as calling clear), and we need to be able to control them in one place.
 */
case class ThriftSerialiser() {
  val serialiser = new TSerializerCopy(new TCompactProtocol.Factory)
  val deserialiser = new TDeserializerCopy(new TCompactProtocol.Factory)

  def toBytes[A](a: A)(implicit ev: A <:< ThriftLike): Array[Byte] =
    serialiser.serialize(ev(a))

  /** WARNING: this returns a mutable [[ByteView]], please be _very_ careful! */
  def toByteViewUnsafe[A](a: A)(implicit ev: A <:< ThriftLike): ByteView =
    serialiser.serializeToView(ev(a))

  def fromBytes1[A](empty: () => A, bytes: Array[Byte])(implicit ev: A <:< ThriftLike): A =
    fromBytesUnsafe(empty(), bytes)

  /** WARNING: This mutates the thrift value _in place_ - use with care and only for performance */
  def fromBytesUnsafe[A](empty: A, bytes: Array[Byte])(implicit ev: A <:< ThriftLike): A = {
    fromBytesViewUnsafe(empty, bytes, 0, bytes.length)
  }

  def fromBytesViewUnsafe[A](empty: A, bytes: Array[Byte], offset: Int, length: Int)(implicit ev: A <:< ThriftLike): A = {
    val e = ev(empty)
    e.clear()
    deserialiser.deserialize(e, bytes, offset, length)
    e.asInstanceOf[A]
  }
}
