sealed class ThingGeneric<V>(
  val vocalise: V
)

sealed class Thing (
  val vocalise: String
)

object Computer : Thing(1010101.toString())

