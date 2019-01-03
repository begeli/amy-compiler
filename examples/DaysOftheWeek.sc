object DaysOftheWeek {
  def mapNoToDay(x: Int): String = {
      x match {
        case 1 => "Monday"
        case 2 => "Tuesday"
        case 3 => "Wednesday"
        case 4 => "Thursday"
        case 5 => "Freedom"
        case 6 => "Saturday"
        case 7 => "Judgement"
        case _ => error("There are only 7 days you goon")
      }
  }

  Std.printString("Enter the number that would correspond the day of the week");
  val dayOftheWeek: Int = Std.readInt();
  Std.printString("Day of the week equivalent to the number you have entered is " ++ mapNoToDay(dayOftheWeek))

}