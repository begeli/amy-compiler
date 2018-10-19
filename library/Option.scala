object O {
  abstract class Option
  case class None() extends Option
  case class Some(v: Int) extends Option

  def isDefined(o: Option): Boolean = {
    o match {
      case None() => false
      case _ => true
    }
  }

  def get(o: Option): Int = {
    o match {
      case Some(i) => i
      case None() => error("get(None)")
    }
  }

  def getOrElse(o: Option, i: Int): Int = {
    o match {
      case None() => i
      case Some(oo) => oo
    }
  }

  def orElse(o1: Option, o2: Option): Option = {
    o1 match {
      case Some(_) => o1
      case None() => o2
    }
  }

  def toList(o: Option): L.List = {
    o match {
      case Some(i) => L.Cons(i, L.Nil())
      case None() => L.Nil()
    }
  }
}
