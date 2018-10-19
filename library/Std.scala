/** This module contains basic functionality for Amy,
  * including stub implementations for some built-in functions
  * (implemented in WASM or JavaScript)
  */
object Std {
  def printInt(i: Int): Unit = {
    error("") // Stub implementation
  }
  def printString(s: String): Unit = {
    error("") // Stub implementation
  }
  def printBoolean(b: Boolean): Unit = {
    printString(booleanToString(b))
  }

  def readString(): String = {
    error("") // Stub implementation
  }

  def readInt(): Int = {
    error("") // Stub implementation
  }

  def intToString(i: Int): String = {
    if (i < 0) {
      "-" ++ intToString(-i)
    } else {
      val rem: Int = i % 10;
      val div: Int = i / 10;
      if (div == 0) { digitToString(rem) }
      else { intToString(div) ++ digitToString(rem) }
    }
  }
  def digitToString(i: Int): String = {
    error("") // Stub implementation
  }
  def booleanToString(b: Boolean): String = {
    if (b) { "true" } else { "false" }
  }
}
