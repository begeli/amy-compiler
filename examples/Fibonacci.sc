object Fibonacci {
  def findNthFibonacciNumber(n: Int): Int = {
    if (n < 0) {
      error("Invalid Input: findNthFibonacciNumber: n cannot be negative")
    } else {
      if (n == 0 || n == 1) { n }
      else { findNthFibonacciNumber(n - 1) + findNthFibonacciNumber(n - 2) }
    }
  }

  Std.printString("This program computes the nth fibonacci number recursively.");
  Std.printString("Enter the value of n: ");
  val n: Int = Std.readInt();
  val nthFibonacciNumber: Int = findNthFibonacciNumber(n);
  Std.printString("The value of nth fibonacci number is: " ++ Std.intToString(nthFibonacciNumber))
}