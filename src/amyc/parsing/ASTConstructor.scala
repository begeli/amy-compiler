package amyc
package parsing

import grammarcomp.parsing._
import utils.Positioned
import ast.NominalTreeModule._
import Tokens._

// Will construct Amy trees from grammarcomp parse Trees.
// Corresponds to Parser.msGrammar
class ASTConstructor {

  def constructProgram(ptree: NodeOrLeaf[Token]): Program = {
    ptree match {
      case Node('Program ::= _, List(mods)) =>
        val modules = constructList(mods, constructModule)
        val p = Program(modules)
        if (modules.nonEmpty) p.setPos(modules.head) else p
    }
  }

  def constructModule(pTree: NodeOrLeaf[Token]): ModuleDef = {
    pTree match {
      case Node('ModuleDef ::= _, List(Leaf(obj), name, _, defs, optExpr, _, _)) =>
        ModuleDef(
          constructName(name)._1,
          constructList(defs, constructDef),
          constructOption(optExpr, constructExpr)
        ).setPos(obj)
    }
  }

  def constructName(ptree: NodeOrLeaf[Token]): (String, Positioned) = {
    ptree match {
      case Node('Id ::= _, List(Leaf(id@ID(name)))) =>
        (name, id)
    }
  }

  def constructDef(pTree: NodeOrLeaf[Token]): ClassOrFunDef = {
    pTree match {
      case Node('Definition ::= _, List(df)) =>
        constructDef0(df)
    }
  }

  def constructDef0(pTree: NodeOrLeaf[Token]): ClassOrFunDef = {
    pTree match {
      case Node('AbstractClassDef ::= _, List(Leaf(abs), _, name)) =>
        AbstractClassDef(constructName(name)._1).setPos(abs)
      case Node('CaseClassDef ::= _, List(Leaf(cse), _, name, _, params, _, _, parent)) =>
        CaseClassDef(
          constructName(name)._1,
          constructList(params, constructParam, hasComma = true).map(_.tt),
          constructName(parent)._1
        ).setPos(cse)
      case Node('FunDef ::= _, List(Leaf(df), name, _, params, _, _, retType, _, _, body, _)) =>
        FunDef(
          constructName(name)._1,
          constructList(params, constructParam, hasComma = true),
          constructType(retType),
          constructExpr(body)
        ).setPos(df)
    }
  }

  def constructParam(pTree: NodeOrLeaf[Token]): ParamDef = {
    pTree match {
      case Node('Param ::= _, List(nm, _, tpe)) =>
        val (name, pos) = constructName(nm)
        ParamDef(name, constructType(tpe)).setPos(pos)
    }
  }

  def constructType(pTree: NodeOrLeaf[Token]): TypeTree = {
    pTree match {
      case Node('Type ::= _, List(Leaf(tp))) =>
        TypeTree((tp: @unchecked) match {
          case INT() => IntType
          case STRING() => StringType
          case BOOLEAN() => BooleanType
          case UNIT() => UnitType
        }).setPos(tp)
      case Node('Type ::= _, List(qn)) =>
        val (qname, pos) = constructQname(qn)
        TypeTree(ClassType(qname)).setPos(pos)
    }
  }

  def constructQname(pTree: NodeOrLeaf[Token]): (QualifiedName, Positioned) = {
    pTree match {
      case Node('QName ::= _, List(id)) =>
        val (name, pos) = constructName(id)
        (QualifiedName(None, name), pos)
      case Node('QName ::= _, List(mod, _, nm)) =>
        val (module, pos) = constructName(mod)
        val (name, _) = constructName(nm)
        (QualifiedName(Some(module), name), pos)
    }
  }

  def tokenToExpr(t: Token): (Expr, Expr) => Expr = {
    (t: @unchecked) match {
      case PLUS() => Plus
      case MINUS() => Minus
      case TIMES() => Times
      case DIV() => Div
      case MOD() => Mod
      case LESSTHAN() => LessThan
      case LESSEQUALS() => LessEquals
      case AND() => And
      case OR() => Or
      case EQUALS() => Equals
      case CONCAT() => Concat
      case SEMICOLON() => Sequence
    }
  }

  def constructOp(ptree: NodeOrLeaf[Token]): (Expr, Expr) => Expr = {
    ptree match {
      case Node(_, List(Leaf(t))) =>
        tokenToExpr(t)
    }
  }

  def constructExpr(ptree: NodeOrLeaf[Token]): Expr = {
    ptree match {
      case Node('Expr ::= List('Id), List(id)) =>
        val (name, pos) = constructName(id)
        Variable(name).setPos(pos)
      case Node('Expr ::= List('Literal), List(lit)) =>
        constructLiteral(lit)
      case Node('Expr ::= List('Expr, 'BinOp, 'Expr), List(e1, op, e2)) =>
        val pe1 = constructExpr(e1)
        val pe2 = constructExpr(e2)
        constructOp(op)(pe1, pe2).setPos(pe1)
      case Node('Expr ::= List(BANG(), _), List(Leaf(bt), e)) =>
        Not(constructExpr(e)).setPos(bt)
      case Node('Expr ::= List(MINUS(), _), List(Leaf(mt), e)) =>
        Neg(constructExpr(e)).setPos(mt)
      case Node('Expr ::= ('QName :: _), List(name, _, as, _)) =>
        val (qname, pos) = constructQname(name)
        val args = constructList(as, constructExpr, hasComma = true)
        Call(qname, args).setPos(pos)
      case Node('Expr ::= List('Expr, SEMICOLON(), 'Expr), List(e1, _, e2)) =>
        val expr1 = constructExpr(e1)
        val expr2 = constructExpr(e2)
        Sequence(expr1, expr2).setPos(expr1)
      case Node('Expr ::= (VAL() :: _), List(Leaf(vt), param, _, value, _, body)) =>
        Let(constructParam(param), constructExpr(value), constructExpr(body)).setPos(vt)
      case Node('Expr ::= (IF() :: _), List(Leaf(it), _, cond, _, _, thenn, _, _, _, elze, _)) =>
        Ite(
          constructExpr(cond),
          constructExpr(thenn),
          constructExpr(elze)
        ).setPos(it)
      case Node('Expr ::= (_ :: MATCH() :: _), List(sc, _, _, cases, _)) =>
        val scrut = constructExpr(sc)
        Match(scrut, constructList1(cases, constructCase))
      case Node('Expr ::= (ERROR() :: _), List(Leaf(ert), _, msg, _)) =>
        Error(constructExpr(msg)).setPos(ert)
      case Node('Expr ::= List(LPAREN(), 'Expr, RPAREN()), List(Leaf(lp), expr, _)) =>
        constructExpr(expr).setPos(lp)
    }
  }

  def constructLiteral(pTree: NodeOrLeaf[Token]): Literal[_] = {
    pTree match {
      case Node('Literal ::= List(INTLITSENT), List(Leaf(it@INTLIT(i)))) =>
        IntLiteral(i).setPos(it)
      case Node('Literal ::= List(STRINGLITSENT), List(Leaf(st@STRINGLIT(s)))) =>
        StringLiteral(s).setPos(st)
      case Node('Literal ::= _, List(Leaf(tt@TRUE()))) =>
        BooleanLiteral(true).setPos(tt)
      case Node('Literal ::= _, List(Leaf(tf@FALSE()))) =>
        BooleanLiteral(false).setPos(tf)
      case Node('Literal ::= _, List(Leaf(lp@LPAREN()), Leaf(RPAREN()))) =>
        UnitLiteral().setPos(lp)
    }
  }

  def constructCase(pTree: NodeOrLeaf[Token]): MatchCase = {
    pTree match {
      case Node('Case ::= _, List(Leaf(ct), pat, _, expr)) =>
        MatchCase(constructPattern(pat), constructExpr(expr)).setPos(ct)
    }
  }

  def constructPattern(pTree: NodeOrLeaf[Token]): Pattern = {
    pTree match {
      case Node('Pattern ::= List(UNDERSCORE()), List(Leaf(ut))) =>
        WildcardPattern().setPos(ut)
      case Node('Pattern ::= List('Literal), List(lit)) =>
        val literal = constructLiteral(lit)
        LiteralPattern(literal).setPos(literal)
      case Node('Pattern ::= List('Id), List(id)) =>
        val (name, pos) = constructName(id)
        IdPattern(name).setPos(pos)
      case Node('Pattern ::= ('QName :: _), List(qn, _, patts, _)) =>
        val (qname, pos) = constructQname(qn)
        val patterns = constructList(patts, constructPattern, hasComma = true)
        CaseClassPattern(qname, patterns).setPos(pos)
    }
  }

  /** Extracts a List of elements of a generic type A, possibly separated by commas,
    * from a parse tree, by repeating a given parser.
    *
    * The form of the parse tree has to be specific:
    * (t, ts) if there is no comma, and
    * (COMMA(), t, ts) if there is a comma,
    * where t is the tree corresponding to the first element and ts to the rest.
    *
    * @param ptree The input parse tree
    * @param constructor A transformer for an individual object
    * @param hasComma Whether the elements of the list are separated by a COMMA()
    * @tparam A The type of List elements
    * @return A list of parsed elements of type A
    */
  def constructList[A](ptree: NodeOrLeaf[Token], constructor: NodeOrLeaf[Token] => A, hasComma: Boolean = false): List[A] = {
    ptree match {
      case Node(_, List()) => List()
      case Node(_, List(t, ts)) =>
        constructor(t) :: constructList(ts, constructor, hasComma)
      case Node(_, List(Leaf(COMMA()), t, ts)) if hasComma =>
        constructor(t) :: constructList(ts, constructor, hasComma)
    }
  }

  /** Extracts a List of elements of a generic type A, possibly separated by commas,
    * from a parse tree, by repeating a given parser. The list has to be nonempty.
    *
    * The form of the parse tree has to be specific:
    * t if the list has one element,
    * (t, ts) if there is no comma,
    * and (t, COMMA(), ts) if there is a comma,
    * where t is the tree corresponding to the first element and ts to the rest.
    *
    * @param ptree The input parse tree
    * @param constructor A transformer for an individual object
    * @param hasComma Whether the elements of the list are separated by a COMMA()
    * @tparam A The type of List elements
    * @return A list of parsed elements of type A
    */
  def constructList1[A](ptree: NodeOrLeaf[Token], constructor: NodeOrLeaf[Token] => A, hasComma: Boolean = false): List[A] = {
    ptree match {
      case Node(_, List(t)) => List(constructor(t))
      case Node(_, List(t, ts)) =>
        constructor(t) :: constructList1(ts, constructor, hasComma)
      case Node(_, List(t, Leaf(COMMA()), ts)) if hasComma =>
        constructor(t) :: constructList1(ts, constructor, hasComma)
    }
  }

  /** Optionally extract an element from a parse tree.
    *
    * @param ptree The input parse tree
    * @param constructor The extractor of the element if it is present
    * @tparam A The type of the element
    * @return The element wrapped in Some(), or None if the production is empty.
    */
  def constructOption[A](ptree: NodeOrLeaf[Token], constructor: NodeOrLeaf[Token] => A): Option[A] = {
    ptree match {
      case Node(_, List()) => None
      case Node(_, List(t)) =>
        Some(constructor(t))
    }
  }

  /** Optionally extract an element from a parse tree.
    *
    * The parse tree has to have a specific form: empty production will result in None,
    * and an operator (which will be ignored) followed by the element we need to extract
    * in case of Some.
    *
    * @param ptree The input parse tree
    * @param constructor The extractor of the element if it is present
    * @tparam A The type of the element
    * @return The element wrapped in Some(), or None if the production is empty.
    */
  def constructOpOption[A](ptree: NodeOrLeaf[Token], constructor: NodeOrLeaf[Token] => A): Option[A] = {
    ptree match {
      case Node(_, List()) => None
      case Node(_, List(_, t)) =>
        Some(constructor(t))
    }
  }

}
