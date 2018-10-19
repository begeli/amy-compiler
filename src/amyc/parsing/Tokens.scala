package amyc
package parsing

import grammarcomp.grammar.CFGrammar.TerminalClass
import utils.Positioned

sealed class Token extends Positioned

// Defines tokens for Lexer
object Tokens {

  /* Utility tokens */
  case class BAD() extends Token // represents incorrect tokens.
  case class EOF() extends Token // Signifies end-of-file

  /* Keywords */
  case class ABSTRACT() extends Token
  case class BOOLEAN()  extends Token
  case class CASE()     extends Token
  case class CLASS()    extends Token
  case class DEF()      extends Token
  case class ELSE()     extends Token
  case class ERROR()    extends Token
  case class EXTENDS()  extends Token
  case class FALSE()    extends Token
  case class IF()       extends Token
  case class INT()      extends Token
  case class MATCH()    extends Token
  case class OBJECT()   extends Token
  case class STRING()   extends Token
  case class TRUE()     extends Token
  case class UNIT()     extends Token
  case class VAL()      extends Token

  /* Operators */
  case class SEMICOLON()  extends Token // ;
  case class PLUS()       extends Token // +
  case class MINUS()      extends Token // -
  case class TIMES()      extends Token // *
  case class DIV()        extends Token // /
  case class MOD()        extends Token // %
  case class LESSTHAN()   extends Token // <
  case class LESSEQUALS() extends Token // <=
  case class AND()        extends Token // &&
  case class OR()         extends Token // ||
  case class EQUALS()     extends Token // ==
  case class CONCAT()     extends Token // ++
  case class BANG()       extends Token // !

  /* Delimiters and wildcard */
  case class LBRACE()     extends Token // {
  case class RBRACE()     extends Token // }
  case class LPAREN()     extends Token // (
  case class RPAREN()     extends Token // )
  case class COMMA()      extends Token // ,
  case class COLON()      extends Token // :
  case class DOT()        extends Token // .
  case class EQSIGN()     extends Token // =
  case class RARROW()     extends Token // =>
  case class UNDERSCORE() extends Token // _

  // Identifiers
  case class ID(value: String) extends Token with TerminalClass

  // Integer literals
  case class INTLIT(value: Int) extends Token with TerminalClass

  // String literals
  case class STRINGLIT(value: String) extends Token with TerminalClass

  // These three tokens are meant to represent their respective category in the parser
  val IDSENT = ID("")
  val INTLITSENT = INTLIT(0)
  val STRINGLITSENT = STRINGLIT("")  
}
