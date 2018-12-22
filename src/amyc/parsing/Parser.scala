package amyc
package parsing

import grammarcomp.grammar.CFGrammar._
import grammarcomp.grammar.GrammarDSL._
import grammarcomp.grammar.GrammarUtils.InLL1
import grammarcomp.grammar._
import grammarcomp.parsing._
import amyc.utils._
import ast.NominalTreeModule._
import Tokens._

// The parser for Amy
// Absorbs tokens from the Lexer and then uses grammarcomp to generate parse trees.
// Defines two different grammars, a naive one which does not obey operator precedence (for demonstration purposes)
// and an LL1 grammar that implements the true syntax of Amy
object Parser extends Pipeline[Stream[Token], Program] {

  /* This grammar does not implement the correct syntax of Amy and is not LL1
   * It is given as an example
   */
  val amyGrammar = Grammar('Program, List[Rules[Token]](
    'Program ::= 'ModuleDefs,
    'ModuleDefs ::= 'ModuleDef ~ 'ModuleDefs | epsilon(),
    'ModuleDef ::= OBJECT() ~ 'Id ~ LBRACE() ~ 'Definitions ~ 'OptExpr ~ RBRACE() ~ EOF(),
    'Definitions ::= 'Definition ~ 'Definitions | epsilon(),
    'Definition ::= 'AbstractClassDef | 'CaseClassDef | 'FunDef,
    'AbstractClassDef ::= ABSTRACT() ~ CLASS() ~ 'Id,
    'CaseClassDef ::= CASE() ~ CLASS() ~ 'Id ~ LPAREN() ~ 'Params ~ RPAREN() ~ EXTENDS() ~ 'Id,
    'FunDef ::= DEF() ~ 'Id ~ LPAREN() ~ 'Params ~ RPAREN() ~ COLON() ~ 'Type ~ EQSIGN() ~ LBRACE() ~ 'Expr ~ RBRACE(),
    'Params ::= epsilon() | 'Param ~ 'ParamList,
    'ParamList ::= epsilon() | COMMA() ~ 'Param ~ 'ParamList,
    'Param ::= 'Id ~ COLON() ~ 'Type,
    'OptExpr ::= 'Expr | epsilon(),
    'Type ::= INT() | STRING() | BOOLEAN() | UNIT() | 'QName,
    'QName ::= 'Id | 'Id ~ DOT() ~ 'Id,
    'Expr ::= 'Id | 'Literal | 'Expr ~ 'BinOp ~ 'Expr | BANG() ~ 'Expr | MINUS() ~ 'Expr |
      'QName ~ LPAREN() ~ 'Args ~ RPAREN() | 'Expr ~ SEMICOLON() ~ 'Expr |
      VAL() ~ 'Param ~ EQSIGN() ~ 'Expr ~ SEMICOLON() ~ 'Expr |
      IF() ~ LPAREN() ~ 'Expr ~ RPAREN() ~ LBRACE() ~ 'Expr ~ RBRACE() ~ ELSE() ~ LBRACE() ~ 'Expr ~ RBRACE() |
      'Expr ~ MATCH() ~ LBRACE() ~ 'Cases ~ RBRACE() |
      ERROR() ~ LPAREN() ~ 'Expr ~ RPAREN() |
      LPAREN() ~ 'Expr ~ RPAREN(),
    'Literal ::= TRUE() | FALSE() | LPAREN() ~ RPAREN() | INTLITSENT | STRINGLITSENT,
    'BinOp ::= PLUS() | MINUS() | TIMES() | DIV() | MOD() | LESSTHAN() | LESSEQUALS() |
      AND() | OR() | EQUALS() | CONCAT(),
    'Cases ::= 'Case | 'Case ~ 'Cases,
    'Case ::= CASE() ~ 'Pattern ~ RARROW() ~ 'Expr,
    'Pattern ::= UNDERSCORE() | 'Literal | 'Id | 'QName ~ LPAREN() ~ 'Patterns ~ RPAREN(),
    'Patterns ::= epsilon() | 'Pattern ~ 'PatternList,
    'PatternList ::= epsilon() | COMMA() ~ 'Pattern ~ 'PatternList,
    'Args ::= epsilon() | 'Expr ~ 'ExprList,
    'ExprList ::= epsilon() | COMMA() ~ 'Expr ~ 'ExprList,
    'Id ::= IDSENT
  ))

  // You can start from the example and work your way from there.
  // Make sure you use the warning that tells you which part is not in LL1
  val amyGrammarLL1 = Grammar('Program, List[Rules[Token]](
    'Program ::= 'ModuleDefs,
    'ModuleDefs ::= epsilon() | 'ModuleDef ~ 'ModuleDefs,
    'ModuleDef ::= OBJECT() ~ 'Id ~ LBRACE() ~ 'Definitions ~ 'OptExpr ~ RBRACE() ~ EOF(),
    'Definitions ::= epsilon() | 'Definition ~ 'Definitions,
    'Definition ::= 'AbstractClassDef | 'CaseClassDef | 'FunDef,
    'AbstractClassDef ::= ABSTRACT() ~ CLASS() ~ 'Id,
    'CaseClassDef ::= CASE() ~ CLASS() ~ 'Id ~ LPAREN() ~ 'Params ~ RPAREN() ~ EXTENDS() ~ 'Id,
    'FunDef ::= DEF() ~ 'Id ~ LPAREN() ~ 'Params ~ RPAREN() ~ COLON() ~ 'Type ~ EQSIGN() ~ LBRACE() ~ 'ExprPriorityLvl1 ~ RBRACE(),
    'Params ::= epsilon() | 'Param ~ 'ParamList,
    'ParamList ::= epsilon() | COMMA() ~ 'Param ~ 'ParamList,
    'Param ::= 'Id ~ COLON() ~ 'Type,
    'OptExpr ::= epsilon() | 'ExprPriorityLvl1,
    'Type ::= INT() | STRING() | BOOLEAN() | UNIT() | 'QName,
    'QName ::= 'Id ~ 'QNameCont,
    'QNameCont ::= epsilon() | DOT() ~ 'Id,
    'Call ::= epsilon() | 'QNameCont ~ LPAREN() ~ 'Args ~ RPAREN(),
    'ExprPriorityLvl1 ::= VAL() ~ 'Param ~ EQSIGN() ~ 'ExprPriorityLvl2 ~ SEMICOLON() ~ 'ExprPriorityLvl1 | 'ExprPriorityLvl2 ~ 'ExprPriorityLvl1Cont,
    'ExprPriorityLvl1Cont ::= epsilon() | SEMICOLON() ~ 'ExprPriorityLvl1,
    'ExprPriorityLvl2 ::= 'ExprPriorityLvl3 ~ 'ExprPriorityLvl2Cont,
    'ExprPriorityLvl2Cont ::= epsilon() | MATCH() ~ LBRACE() ~ 'Cases ~ RBRACE(),
    'ExprPriorityLvl3 ::= 'ExprPriorityLvl4 ~ 'ExprPriorityLvl3Cont,
    'ExprPriorityLvl3Cont ::= epsilon() | OR() ~ 'ExprPriorityLvl3,
    'ExprPriorityLvl4 ::= 'ExprPriorityLvl5 ~ 'ExprPriorityLvl4Cont,
    'ExprPriorityLvl4Cont ::= epsilon() | AND() ~ 'ExprPriorityLvl4,
    'ExprPriorityLvl5 ::= 'ExprPriorityLvl6 ~ 'ExprPriorityLvl5Cont,
    'ExprPriorityLvl5Cont ::= epsilon() | EQUALS() ~ 'ExprPriorityLvl5,
    'ExprPriorityLvl6 ::= 'ExprPriorityLvl7 ~ 'ExprPriorityLvl6Cont,
    'ExprPriorityLvl6Cont ::= epsilon() | LESSTHAN() ~ 'ExprPriorityLvl6 | LESSEQUALS() ~ 'ExprPriorityLvl6,
    'ExprPriorityLvl7 ::= 'ExprPriorityLvl8 ~ 'ExprPriorityLvl7Cont,
    'ExprPriorityLvl7Cont ::= epsilon() | PLUS() ~ 'ExprPriorityLvl7 | MINUS() ~ 'ExprPriorityLvl7 | CONCAT() ~ 'ExprPriorityLvl7,
    'ExprPriorityLvl8 ::= 'ExprPriorityLvl9 ~ 'ExprPriorityLvl8Cont,
    'ExprPriorityLvl8Cont ::= epsilon() | TIMES() ~ 'ExprPriorityLvl8 | DIV() ~ 'ExprPriorityLvl8 | MOD() ~ 'ExprPriorityLvl8,
    'ExprPriorityLvl9 ::= 'ExprPriorityLvl10 | MINUS() ~ 'ExprPriorityLvl10 | BANG() ~ 'ExprPriorityLvl10,
    'ExprPriorityLvl10 ::= IF() ~ LPAREN() ~ 'ExprPriorityLvl1 ~ RPAREN() ~ LBRACE() ~ 'ExprPriorityLvl1 ~ RBRACE() ~ ELSE() ~ LBRACE() ~ 'ExprPriorityLvl1 ~ RBRACE() |
      ERROR() ~ LPAREN() ~ 'ExprPriorityLvl1 ~ RPAREN() | 'Id ~ 'Call | 'LiteralDeflated | LPAREN() ~ 'UnitLitOrParanthesizedExpr,
    'Literal ::= TRUE() | FALSE() | LPAREN() ~ RPAREN() | INTLITSENT | STRINGLITSENT,
    'LiteralDeflated ::= TRUE() | FALSE() | INTLITSENT | STRINGLITSENT,
    'UnitLitOrParanthesizedExpr ::= RPAREN() | 'ExprPriorityLvl1 ~ RPAREN(),
    'BinOp ::= PLUS() | MINUS() | TIMES() | DIV() | MOD() | LESSTHAN() | LESSEQUALS() | AND() | OR() | EQUALS() | CONCAT(),
    'Cases ::= 'Case ~ 'CasesCont,
    'CasesCont ::= epsilon() | 'Cases,
    'Case ::= CASE() ~ 'Pattern ~ RARROW() ~ 'ExprPriorityLvl1,
    'Pattern ::= UNDERSCORE() | 'Literal | 'Id ~ 'PatternCont,
    'PatternCont ::= epsilon() | 'QNameCont ~ LPAREN() ~ 'Patterns ~ RPAREN(),
    'Patterns ::= epsilon() | 'Pattern ~ 'PatternList,
    'PatternList ::= epsilon() | COMMA() ~ 'Pattern ~ 'PatternList,
    'Args ::= epsilon() | 'ExprPriorityLvl1 ~ 'ExprList,
    'ExprList ::= epsilon() | COMMA() ~ 'ExprPriorityLvl1 ~ 'ExprList,
    'Id ::= IDSENT,
  ))

  def run(ctx: Context)(tokens: Stream[Token]): Program = {
    val (grammar, constructor) = (amyGrammarLL1, new ASTConstructorLL1)

    import ctx.reporter._
    implicit val gc = new GlobalContext()
    implicit val pc = new ParseContext()

    GrammarUtils.isLL1WithFeedback(grammar) match {
      case InLL1() =>
      info("Grammar is in LL1")
      case other =>
        warning(other)
    }

    val feedback = ParseTreeUtils.parseWithTrees(grammar, tokens.toList)
    feedback match {
      case s: Success[Token] =>
        constructor.constructProgram(s.parseTrees.head)
      case err@LL1Error(_, Some(tok)) =>
        fatal(s"Parsing failed: $err", tok.obj.position)
      case err =>
        fatal(s"Parsing failed: $err")
    }
  }

}