package amyc
package parsing

import grammarcomp.parsing._
import utils.Positioned
import ast.NominalTreeModule._
import Tokens._

// Implements the translation from parse trees to ASTs for the LL1 grammar.
// Corresponds to Parser.msGrammarLL1
// This extends the plain ASTConstructor as some things will be the same.
// You should override whatever has changed.
// Make sure to use ASTConstructor as an example
class ASTConstructorLL1 extends ASTConstructor {

  override def constructQname(pTree: NodeOrLeaf[Token]): (QualifiedName, Positioned) = {
    pTree match {
      case Node('QName ::= _, List(id, qnamecont)) =>
        qnamecont match {
          case Node('QNameCont ::= _, List(_, name)) =>
            val (qnameid, _) = constructName(name)
            val (module, pos) = constructName(id)
            (QualifiedName(Some(module), qnameid), pos)
          case _ =>
            val (name, pos) = constructName(id)
            (QualifiedName(None, name), pos)
        }
    }
  }

  override def constructExpr(ptree: NodeOrLeaf[Token]): Expr = {
    ptree match {
      case Node('ExprPriorityLvl1 ::= _, List(Leaf(vt), param, _, lvl2Expr, _, expr)) =>
        Let(constructParam(param), constructExprPriorityLvl2(lvl2Expr), constructExpr(expr)).setPos(vt)
      case Node('ExprPriorityLvl1 ::= _, List(lvl2Expr, exprCont)) =>
        exprCont match {
          case Node('ExprPriorityLvl1Cont ::= _, List(_, expr)) =>
            val exprCont = constructExpr(expr)
            val start = constructExprPriorityLvl2(lvl2Expr)
            Sequence(start, exprCont).setPos(start)
          case _ => constructExprPriorityLvl2(lvl2Expr)
        }
    }
  }

  def constructExprPriorityLvl2(ptree: NodeOrLeaf[Token]): Expr = {
    ptree match {
      case Node('ExprPriorityLvl2 ::= _, List(lvl3expr, lvl2exprCont)) =>
        lvl2exprCont match {
          case Node('ExprPriorityLvl2Cont ::= _, List(_, _, cases, _)) =>
            val scrut = constructExprPriorityLvl3(lvl3expr)
            Match(scrut, constructCases(cases)).setPos(scrut)
          case _ => constructExprPriorityLvl3(lvl3expr)
        }
    }
  }

  def constructCases(ptree: NodeOrLeaf[Token]): List[MatchCase] = {
    ptree match {
      case Node('Cases ::= _, List(cs, casecont)) =>
        constructCase(cs) :: constructCaseCont(casecont)
    }
  }

  def constructCaseCont(ptree: NodeOrLeaf[Token]): List[MatchCase] = {
    ptree match {
      case Node('CasesCont ::= ('Cases :: Nil), List(cases)) => constructCases(cases)
      case Node('CasesCont ::= _, _) => Nil // epsilon case
    }
  }

  def constructExprPriorityLvl3(ptree: NodeOrLeaf[Token]): Expr = {
    ptree match {
      case Node('ExprPriorityLvl3 ::= _, List(expr4, exprcont3)) =>
        constructOpExpr(constructExprPriorityLvl4(expr4), exprcont3, constructExprPriorityLvl4)
    }
  }

  def constructExprPriorityLvl4(ptree: NodeOrLeaf[Token]): Expr = {
    ptree match {
      case Node('ExprPriorityLvl4 ::= _, List(expr5, exprcont4)) =>
        constructOpExpr(constructExprPriorityLvl5(expr5), exprcont4, constructExprPriorityLvl5)
    }
  }

  def constructExprPriorityLvl5(ptree: NodeOrLeaf[Token]): Expr = {
    ptree match {
      case Node('ExprPriorityLvl5 ::= _, List(expr6, exprcont5)) =>
        constructOpExpr(constructExprPriorityLvl6(expr6), exprcont5, constructExprPriorityLvl6)
    }
  }

  def constructExprPriorityLvl6(ptree: NodeOrLeaf[Token]): Expr = {
    ptree match {
      case Node('ExprPriorityLvl6 ::= _, List(expr7, exprcont6)) =>
        constructOpExpr(constructExprPriorityLvl7 (expr7), exprcont6, constructExprPriorityLvl7)
    }
  }

  def constructExprPriorityLvl7 (ptree: NodeOrLeaf[Token]): Expr = {
    ptree match {
      case Node('ExprPriorityLvl7 ::= _, List(expr8, exprcont7)) =>
        constructOpExpr(constructExprPriorityLvl8(expr8), exprcont7, constructExprPriorityLvl8)
    }
  }

  def constructExprPriorityLvl8(ptree: NodeOrLeaf[Token]): Expr = {
    ptree match {
      case Node('ExprPriorityLvl8 ::= _, List(expr9, exprcont8)) =>
        constructOpExpr(constructExprPriorityLvl9(expr9), exprcont8, constructExprPriorityLvl9)
    }
  }

  def constructExprPriorityLvl9(ptree: NodeOrLeaf[Token]): Expr = {
    ptree match {
      case Node('ExprPriorityLvl9 ::= List(MINUS(), _), List(Leaf(mt), expr10)) =>
        Neg(constructExprPriorityLvl10(expr10)).setPos(mt)
      case Node('ExprPriorityLvl9 ::= List(BANG(), _), List(Leaf(bt), expr10)) =>
        Not(constructExprPriorityLvl10(expr10)).setPos(bt)
      case Node('ExprPriorityLvl9 ::= _, List(expr10)) =>
        constructExprPriorityLvl10(expr10)
    }
  }

  def constructExprPriorityLvl10(ptree: NodeOrLeaf[Token]): Expr = {
    ptree match {
      // If then else
      case Node('ExprPriorityLvl10 ::= (IF() :: _), List(Leaf(it), _, cond, _, _, thenn, _, _, _, elze, _)) =>
        Ite(
          constructExpr(cond),
          constructExpr(thenn),
          constructExpr(elze)
        ).setPos(it)
      // Error
      case Node('ExprPriorityLvl10 ::= (ERROR() :: _), List(Leaf(ert), _, msg, _)) =>
        Error(constructExpr(msg)).setPos(ert)
      // Literals without parentheses
      case Node('ExprPriorityLvl10 ::= List('LiteralDeflated), List(lit)) =>
        constructLiteralDeflated(lit)
      // Unit literal or parenthesized
      case Node('ExprPriorityLvl10 ::= List(LPAREN(), _), List(Leaf(lp@LPAREN()), unitLitOrParanthesized)) =>
        unitLitOrParanthesized match {
          case Node('UnitLitOrParanthesizedExpr ::= List(RPAREN()), List(_)) =>
            UnitLiteral().setPos(lp)
          case Node('UnitLitOrParanthesizedExpr ::= _, List(expr, _)) =>
            constructExpr(expr).setPos(lp)
        }
      // Identifier or call
      case Node('ExprPriorityLvl10 ::= List('Id, _), List(id, call)) =>
        val (qnameid, args) = constructCall(call)

        args match {
          case null =>
            val (name, pos) = constructName(id)
            Variable(name).setPos(pos)
          case _ =>
            qnameid match {
              case None =>
                val (name, pos) = constructName(id)
                val qname = QualifiedName(None, name)
                Call(qname, args).setPos(pos)
              case null =>
                val (name, pos) = constructName(id)
                val qname = QualifiedName(None, name)
                Call(qname, args).setPos(pos)
              case _ =>
                val (module, pos) = constructName(id)
                val qname = QualifiedName(Some(module), qnameid.get)
                Call(qname, args).setPos(pos)
            }
        }
    }
  }

  def constructCall(ptree: NodeOrLeaf[Token]): (Option[String], List[Expr]) = {
    ptree match {
        case Node('Call ::= _, List(qnamecont, _, as, _)) =>
        qnamecont match {
          case Node('QNameCont ::= _, List(_, id)) =>
            val (name, _) = constructName(id)
            val args = constructList(as, constructExpr, hasComma = true)
            (Some(name), args)
          case _ =>
            val args = constructList(as, constructExpr, hasComma = true)
            (None, args)
        }
      case _ => (null, null)
    }
  }

  def constructLiteralDeflated(ptree: NodeOrLeaf[Token]): Literal[_] = {
    ptree match {
      case Node('LiteralDeflated ::= List(INTLITSENT), List(Leaf(it@INTLIT(i)))) =>
        IntLiteral(i).setPos(it)
      case Node('LiteralDeflated ::= List(STRINGLITSENT), List(Leaf(st@STRINGLIT(s)))) =>
        StringLiteral(s).setPos(st)
      case Node('LiteralDeflated ::= _, List(Leaf(tt@TRUE()))) =>
        BooleanLiteral(true).setPos(tt)
      case Node('LiteralDeflated ::= _, List(Leaf(tf@FALSE()))) =>
        BooleanLiteral(false).setPos(tf)
    }
  }

  def constructParenthesizedExpr(ptree: NodeOrLeaf[Token]): Expr ={
    ptree match {
      case Node('ParenthesizedExpr ::= List(LPAREN(), _), List(Leaf(lparen@LPAREN()), parenExprCont)) =>
        parenExprCont match {
          case Node('ParenthesizedExprCont ::= List(RPAREN()), List(_)) =>
            UnitLiteral().setPos(lparen)
          case Node('ParenthesizedExprCont ::= _, List(expr, _)) =>
            constructExpr(expr).setPos(lparen)
        }
    }
  }

  override def constructPattern(pTree: NodeOrLeaf[Token]): Pattern = {
    pTree match {
      case Node('Pattern ::= List(UNDERSCORE()), List(Leaf(ut))) =>
        WildcardPattern().setPos(ut)
      case Node('Pattern ::= List('Literal), List(lit)) =>
        val literal = constructLiteral(lit)
        LiteralPattern(literal).setPos(literal)
      case Node('Pattern ::= List('Id), List(id)) =>
        val (name, pos) = constructName(id)
        IdPattern(name).setPos(pos)
      case Node('Pattern ::= _, List(id, patterncont)) =>
        val (isQNameContEpsilon, qnameid, patterns) = constructPatternCont(patterncont)

        patterns match {
          case null =>
            val (name, pos) = constructName(id)
            IdPattern(name).setPos(pos)
          case _ =>
            if(isQNameContEpsilon){
              val (name, pos) = constructName(id)
              val qname = QualifiedName(None, name)
              CaseClassPattern(qname, patterns).setPos(pos)
            }
            else {
              val (module, pos) = constructName(id)
              val qname = QualifiedName(Some(module), qnameid)
              CaseClassPattern(qname, patterns).setPos(pos)
            }
        }
    }
  }


  def constructPatternCont(pTree: NodeOrLeaf[Token]): (Boolean, String, List[Pattern]) = {
    pTree match {
      case Node('PatternCont ::= _, List(qnamecont, _, patts, _)) =>
        qnamecont match {
          case Node('QNameCont ::= _, List(_, id)) =>
            val (name, _) = constructName(id)
            val patterns = constructList(patts, constructPattern, hasComma = true)
            (false, name, patterns)
          case _ =>
            val patterns = constructList(patts, constructPattern, hasComma = true)
            (true, null, patterns)
        }
      case _ => (true, null, null)
    }
  }

  override def constructOp(ptree: NodeOrLeaf[Token]): (Expr, Expr) => Expr = {
    ptree match {
      case Node(_, List(Leaf(t))) =>
        tokenToExpr(t)
      case Leaf(t) =>
        tokenToExpr(t)
    }
  }

  // Important helper method:
  // Because LL1 grammar is not helpful in implementing left associativity,
  // we give you this method to reconstruct it.
  // This method takes the left operand of an operator (leftopd)
  // as well as the tree that corresponds to the operator plus the right operand (ptree)
  // It parses the right hand side and then reconstruct the operator expression
  // with correct associativity.
  // If ptree is empty, it means we have no more operators and the leftopd is returned.
  // Note: You may have to override constructOp also, depending on your implementation
  def constructOpExpr(leftopd: Expr, ptree: NodeOrLeaf[Token], nextConstructor: (NodeOrLeaf[Token] => Expr)): Expr = {
    ptree match {
      case Node(_, List()) => //epsilon rule of the nonterminals
        leftopd
      case Node(sym ::= _, List(op, rightNode))
        if Set('ExprPriorityLvl3Cont, 'ExprPriorityLvl4Cont, 'ExprPriorityLvl5Cont, 'ExprPriorityLvl6Cont, 'ExprPriorityLvl7Cont, 'ExprPriorityLvl8Cont) contains sym =>
        rightNode match {
          case Node(_, List(nextOpd, suf)) => // 'Expr? ::= Expr? ~ 'OpExpr,
            val nextAtom = nextConstructor(nextOpd)
            constructOpExpr(constructOp(op)(leftopd, nextAtom).setPos(leftopd), suf, nextConstructor) // captures left associativity
        }
    }
  }
}