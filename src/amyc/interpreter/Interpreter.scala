package amyc
package interpreter

import utils._
import ast.SymbolicTreeModule._
import ast.Identifier
import analyzer.SymbolTable

// An interpreter for Amy programs, implemented in Scala
object Interpreter extends Pipeline[(Program, SymbolTable), Unit] {

  // A class that represents a value computed by interpreting an expression
  abstract class Value {
    def asInt: Int = this.asInstanceOf[IntValue].i
    def asBoolean: Boolean = this.asInstanceOf[BooleanValue].b
    def asString: String = this.asInstanceOf[StringValue].s

    override def toString: String = this match {
      case IntValue(i) => i.toString
      case BooleanValue(b) => b.toString
      case StringValue(s) => s
      case UnitValue => "()"
      case CaseClassValue(constructor, args) =>
        constructor.name + "(" + args.map(_.toString).mkString(", ") + ")"
    }
  }
  case class IntValue(i: Int) extends Value
  case class BooleanValue(b: Boolean) extends Value
  case class StringValue(s: String) extends Value
  case object UnitValue extends Value
  case class CaseClassValue(constructor: Identifier, args: List[Value]) extends Value

  def run(ctx: Context)(v: (Program, SymbolTable)): Unit = {
    val (program, table) = v

    // These built-in functions do not have an Amy implementation in the program,
    // instead their implementation is encoded in this map
    val builtIns: Map[(String, String), (List[Value]) => Value] = Map(
      ("Std", "printInt")    -> { args => println(args.head.asInt); UnitValue },
      ("Std", "printString") -> { args => println(args.head.asString); UnitValue },
      ("Std", "readString")  -> { args => StringValue(scala.io.StdIn.readLine()) },
      ("Std", "readInt")     -> { args =>
        val input = scala.io.StdIn.readLine()
        try {
          IntValue(input.toInt)
        } catch {
          case ne: NumberFormatException =>
            ctx.reporter.fatal(s"""Could not parse "$input" to Int""")
        }
      },
      ("Std", "intToString")   -> { args => StringValue(args.head.asInt.toString) },
      ("Std", "digitToString") -> { args => StringValue(args.head.asInt.toString) }
    )

    // Utility functions to interface with the symbol table.
    def isConstructor(name: Identifier) = table.getConstructor(name).isDefined
    def findFunctionOwner(functionName: Identifier) = table.getFunction(functionName).get.owner.name
    def findFunction(owner: String, name: String) = {
      program.modules.find(_.name.name == owner).get.defs.collectFirst {
        case fd@FunDef(fn, _, _, _) if fn.name == name => fd
      }.get
    }

    // Interprets a function, using evaluations for local variables contained in 'locals'
    // TODO: Complete all missing cases. Look at the given ones for guidance.
    def interpret(expr: Expr)(implicit locals: Map[Identifier, Value]): Value = {
      expr match {
        case Variable(name) =>
          locals(name)
        case IntLiteral(i) =>
          IntValue(i)
        case BooleanLiteral(b) =>
          BooleanValue(b)
        case StringLiteral(s) =>
          StringValue(s)
        case UnitLiteral() =>
          UnitValue
        case Plus(lhs, rhs) =>
          IntValue(interpret(lhs).asInt + interpret(rhs).asInt)
        case Minus(lhs, rhs) =>
          IntValue(interpret(lhs).asInt - interpret(rhs).asInt)
        case Times(lhs, rhs) =>
          IntValue(interpret(lhs).asInt * interpret(rhs).asInt)
        case Div(lhs, rhs) =>
          if (interpret(rhs).asInt != 0)
            IntValue(interpret(lhs).asInt / interpret(rhs).asInt)
          else {
            ctx.reporter.fatal("")
          }
        case Mod(lhs, rhs) =>
          if (interpret(rhs).asInt != 0)
            IntValue(interpret(lhs).asInt % interpret(rhs).asInt)
          else
            ctx.reporter.fatal("")
        case LessThan(lhs, rhs) =>
          BooleanValue(interpret(lhs).asInt < interpret(rhs).asInt)
        case LessEquals(lhs, rhs) =>
          BooleanValue(interpret(lhs).asInt <= interpret(rhs).asInt)
        case And(lhs, rhs) =>
          BooleanValue(interpret(lhs).asBoolean && interpret(rhs).asBoolean)
        case Or(lhs, rhs) =>
          BooleanValue(interpret(lhs).asBoolean || interpret(rhs).asBoolean)
        case Equals(lhs, rhs) =>
          // Hint: Take care to implement Amy equality semantics
          interpret(rhs) match { //try it with if and else statements
            case IntValue(i) => BooleanValue(interpret(lhs).asInt == interpret(rhs).asInt)
            case BooleanValue(b) => BooleanValue(interpret(lhs).asBoolean == interpret(rhs).asBoolean)
            case StringValue(s) => BooleanValue(interpret(lhs) eq interpret(rhs)) //check this part again and again...
            case UnitValue => BooleanValue(true)
            case CaseClassValue(constructors, arguments) => BooleanValue(interpret(lhs) eq interpret(rhs))
          }
        case Concat(lhs, rhs) =>
          StringValue(interpret(lhs).asString ++ interpret(rhs).asString)
        case Not(e) =>
          BooleanValue(!interpret(e).asBoolean)
        case Neg(e) =>
          IntValue(-interpret(e).asInt)
        case Call(qname, args) =>
          //val qOwner = findFunctionOwner(qname)
          if (isConstructor(qname)) {
            CaseClassValue(qname, args.map(argument => interpret(argument)))
          }
          else if (builtIns.contains(findFunctionOwner(qname), qname.name)) {
            builtIns(findFunctionOwner(qname), qname.name)(args.map(value => interpret(value)))
          }
          else {
            val funcOwner = findFunctionOwner(qname)
            val parameters = findFunction(funcOwner, qname.name).params.map(elem => elem.name)
            val arguments = args.map(elem => interpret(elem))
            val paraArgPair = parameters.zip(arguments)
            interpret(findFunction(funcOwner, qname.name).body)(paraArgPair.toMap)
          }
          // Hint: Check if it is a call to a constructor first,
          //       then if it is a built-in function (otherwise it is a normal function).
          //       Use the helper methods provided above to retrieve information from the symbol table.
          //       Think how locals should be modified.
        case Sequence(e1, e2) =>
          interpret(e1);
          interpret(e2)
        case Let(df, value, body) =>
          val newDfs = df.name -> interpret(value)
          val newMap = locals + newDfs
          interpret(body)(newMap)
        case Ite(cond, thenn, elze) =>
          if (interpret(cond).asBoolean)
            interpret(thenn)
          else
            interpret(elze)
        case Match(scrut, cases) =>
          // Hint: We give you a skeleton to implement pattern matching
          //       and the main body of the implementation

          val evS = interpret(scrut)

          // Returns a list of pairs id -> value,
          // where id has been bound to value within the pattern.
          // Returns None when the pattern fails to match.
          // Note: Only works on well typed patterns (which have been ensured by the type checker).
          def matchesPattern(v: Value, pat: Pattern): Option[List[(Identifier, Value)]] = {
            ((v, pat): @unchecked) match {
              case (_, WildcardPattern()) =>
                Some(List())
              case (_, IdPattern(name)) =>
                Some(List(name -> v))
              case (IntValue(i1), LiteralPattern(IntLiteral(i2))) =>
                if (i1 != i2)
                  None
                else
                  Some(List())
              case (BooleanValue(b1), LiteralPattern(BooleanLiteral(b2))) =>
                if (b1 != b2)
                  None
                else
                  Some(List())
              case (StringValue(_), LiteralPattern(StringLiteral(_))) =>
                None
              case (UnitValue, LiteralPattern(UnitLiteral())) =>
                Some(List())
              case (CaseClassValue(con1, realArgs), CaseClassPattern(con2, formalArgs)) =>
                if (con1 != con2)
                  None
                else {
                  val realFormalArgPairs = realArgs.zip(formalArgs)
                  val returnList = realFormalArgPairs.map(argPair => matchesPattern(argPair._1, argPair._2))

                  if ( returnList.contains(None))
                    None
                  else {
                    //returnList = returnList.flatten.flatten
                    Some(returnList.flatten.flatten)
                  }
                }
            }
          }

          // Main "loop" of the implementation: Go through every case,
          // check if the pattern matches, and if so return the evaluation of the case expression
          for {
             MatchCase(pat, rhs) <- cases
            moreLocals <- matchesPattern(evS, pat)
          } {
            return interpret(rhs)(locals ++ moreLocals)
          }
          // No case matched: The program fails with a match error
          ctx.reporter.fatal(s"Match error: ${evS.toString}@${scrut.position}")

        case Error(msg) =>
          println("(╯°□°）╯︵ ┻━┻ Something went wrong! Error message: " ++ interpret(msg).asString)
          ctx.reporter.fatal("")
      }
    }

    // Body of the interpreter: Go through every module in order
    // and evaluate its expression if present
    for {
      m <- program.modules
      e <- m.optExpr
    } {
      interpret(e)(Map())
    }
  }
}
