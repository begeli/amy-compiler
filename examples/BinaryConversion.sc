object BinaryConversion {


  def toBinary(x: Int): String = {
    if (x < 0) {
      error("Only positive numbers can be converted with this method :(")
    }
    else {
      if ((x) <= 1) {
        Std.printInt(x);
        Std.intToString(x)
      }
      else {
        Std.printInt(x);
        toBinary(x / 2) ++ Std.intToString(x % 2)
      }
    }
  }

  Std.printString("Number 7 in binary is " ++ toBinary(7));
  Std.printString("Number 8 in binary is " ++ toBinary(8));
  Std.printString("Negative numbers can't be represented,i.e. -5, because " ++ toBinary(-5))

}