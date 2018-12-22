package amyc
package analyzer

import utils._
import ast.{Identifier, NominalTreeModule => N, SymbolicTreeModule => S}

// Name analyzer for Amy
// Takes a nominal program (names are plain string, qualified names are string pairs)
// and returns a symbolic program, where all names have been resolved to unique Identifiers.
// Rejects programs that violate the Amy naming rules.
// Also populates and returns symbol table.
object NameAnalyzer extends Pipeline[N.Program, (S.Program, SymbolTable)] {
  def run(ctx: Context)(p: N.Program): (S.Program, SymbolTable) = {
    import ctx.reporter._

    // Step 0: Initialize symbol table
    val table = new SymbolTable

    // Step 1: Add modules to table
    val modNames = p.modules.groupBy(_.name)
    modNames.foreach { case (name, modules) =>
      if (modules.size > 1) {
        fatal(s"Two modules named $name in program", modules.head.position)
      }
    }
    modNames.keys.toList foreach table.addModule

    // Helper method: will transform a nominal type 'tt' to a symbolic type,
    // given we are within module 'inModule'
    def transformType(tt: N.TypeTree, inModule: String): S.Type = {
      tt.tpe match {
        case N.IntType => S.IntType
        case N.BooleanType => S.BooleanType
        case N.StringType => S.StringType
        case N.UnitType => S.UnitType
        case N.ClassType(qn@N.QualifiedName(module, name)) =>
          table.getType(module getOrElse inModule, name) match {
            case Some(symbol) =>
              S.ClassType(symbol)
            case None =>
              fatal(s"Could not find type $qn", tt)
          }
      }
    }

    // Step 2: Check name uniqueness of definitions in each module
    p.modules.foreach {
      case (moduleDef) =>
        moduleDef.defs.groupBy(_.name).foreach {
          case (name, listDefinitions) =>
            if (listDefinitions.size > 1)
              fatal(s"Two definitions named $name in ${moduleDef.name}", listDefinitions.head.position)
        }
    }

    // Step 3: Discover types and add them to symbol table
    p.modules.foreach {
      case (moduleDef) =>
        moduleDef.defs.foreach {
          case N.AbstractClassDef(name) =>
            table.addType(moduleDef.name, name)
          case _ =>
        }
    }

    // Step 4: Discover type constructors, add them to table
    p.modules.foreach {
      case (moduleDef) =>
        moduleDef.defs foreach {
          case N.CaseClassDef(name, fields, parent) =>
            table.addConstructor(moduleDef.name, name, fields.map(tt => transformType(tt, moduleDef.name)), table.getType(moduleDef.name, parent).getOrElse(fatal(s"Type does not exist: ${moduleDef.name}")))
          case _ =>
        }
    }

    // Step 5: Discover functions signatures, add them to table
    p.modules.foreach {
      case (moduleDef) =>
        moduleDef.defs.foreach {
          case N.FunDef(name, params, retType, body) =>
            table.addFunction(moduleDef.name, name, params.map(param => param.tt).map(tt => transformType(tt, moduleDef.name)), transformType(retType, moduleDef.name))
          case _ =>
        }
    }

    // Step 6: We now know all definitions in the program.
    //         Reconstruct modules and analyse function bodies/ expressions

    // This part is split into three transfrom functions,
    // for definitions, FunDefs, and expressions.
    // You will need to have in mind we transform constructs of the
    // NominalTreeModule 'N' to respective constructs of the SymbolicTreeModule 'S'.
    // transformFunDef is given as an example, as well as some code for the other ones

    def transformDef(df: N.ClassOrFunDef, module: String): S.ClassOrFunDef = {
      df match {
        case N.AbstractClassDef(name) =>
          val nameS = table.getType(module, name).get
          S.AbstractClassDef(nameS)
        case N.CaseClassDef(name, _, _) =>
          val (identifier, constructorSignature) = table.getConstructor(module, name).get
          val ConstrSig(argTypes, parent, _) = constructorSignature
          val sArgTypes = argTypes.map(arg => S.TypeTree(arg))
          S.CaseClassDef(identifier, sArgTypes, parent)
        case fd: N.FunDef =>
          transformFunDef(fd, module)
      }
    }.setPos(df)

    def transformFunDef(fd: N.FunDef, module: String): S.FunDef = {
      val N.FunDef(name, params, retType, body) = fd
      val Some((sym, sig)) = table.getFunction(module, name)

      params.groupBy(_.name).foreach { case (name, ps) =>
        if (ps.size > 1) {
          fatal(s"Two parameters named $name in function ${fd.name}", fd)
        }
      }

      val paramNames = params.map(_.name)

      val newParams = params zip sig.argTypes map { case (pd@N.ParamDef(name, tt), tpe) =>
        val s = Identifier.fresh(name)
        S.ParamDef(s, S.TypeTree(tpe).setPos(tt)).setPos(pd)
      }

      val paramsMap = paramNames.zip(newParams.map(_.name)).toMap

      S.FunDef(
        sym,
        newParams,
        S.TypeTree(sig.retType).setPos(retType),
        transformExpr(body)(module, (paramsMap, Map()))
      ).setPos(fd)
    }

    // This function takes as implicit a pair of two maps:
    // The first is a map from names of parameters to their unique identifiers,
    // the second is similar for local variables.
    // Make sure to update them correctly if needed given the scoping rules of Amy
    def transformExpr(expr: N.Expr)
                     (implicit module: String, names: (Map[String, Identifier], Map[String, Identifier])): S.Expr = {
      val (params, locals) = names
      val res = expr match {
        case N.Variable(name) =>
          S.Variable(locals.getOrElse(name, params.getOrElse(name, fatal(s"Variable $name does not exist", expr))))

        case N.IntLiteral(value) =>
          S.IntLiteral(value)
        case N.BooleanLiteral(value) =>
          S.BooleanLiteral(value)
        case N.StringLiteral(value) =>
          S.StringLiteral(value)
        case N.UnitLiteral() =>
          S.UnitLiteral()

        case N.Plus(lhs, rhs) =>
          S.Plus(transformExpr(lhs), transformExpr(rhs))
        case N.Minus(lhs, rhs) =>
          S.Minus(transformExpr(lhs), transformExpr(rhs))
        case N.Times(lhs, rhs) =>
          S.Times(transformExpr(lhs), transformExpr(rhs))
        case N.Div(lhs, rhs) =>
          S.Div(transformExpr(lhs), transformExpr(rhs))
        case N.Mod(lhs, rhs) =>
          S.Mod(transformExpr(lhs), transformExpr(rhs))
        case N.LessThan(lhs, rhs) =>
          S.LessThan(transformExpr(lhs), transformExpr(rhs))
        case N.LessEquals(lhs, rhs) =>
          S.LessEquals(transformExpr(lhs), transformExpr(rhs))
        case N.And(lhs, rhs) =>
          S.And(transformExpr(lhs), transformExpr(rhs))
        case N.Or(lhs, rhs) =>
          S.Or(transformExpr(lhs), transformExpr(rhs))
        case N.Equals(lhs, rhs) =>
          S.Equals(transformExpr(lhs), transformExpr(rhs))
        case N.Concat(lhs, rhs) =>
          S.Concat(transformExpr(lhs), transformExpr(rhs))

        case N.Not(expr) =>
          S.Not(transformExpr(expr))
        case N.Neg(expr) =>
          S.Neg(transformExpr(expr))

        case N.Call(qName, args) =>
          val (sName, constructorSignature) = table.getConstructor(qName.module.getOrElse(module), qName.name).
            getOrElse(table.getFunction(qName.module.getOrElse(module),qName.name).
            getOrElse(fatal(s"Constructor or function couldn't be found with name ${qName.module.getOrElse(module)}.${qName.name}", expr)))
          if (args.size != constructorSignature.argTypes.size)
            fatal(s"Wrong number of arguments in ${qName.module.getOrElse(module)}.${qName.name}", expr)

          S.Call(sName, args.map(arg => transformExpr(arg)))
        case N.Sequence(expr1, expr2) =>
          S.Sequence(transformExpr(expr1), transformExpr(expr2))
        case N.Let(df, value, body) => //
          if (locals.contains(df.name))
            fatal(s"Variable redefinition of ${df.name}", df.position)

          val sName = Identifier.fresh(df.name)
          val sTypeTree = S.TypeTree(transformType(df.tt, module))

          S.Let(S.ParamDef(sName, sTypeTree), transformExpr(value), transformExpr(body)(module, (params, locals + (df.name -> sName))))
        case N.Ite(cond, thenn, elze) =>
          S.Ite(transformExpr(cond), transformExpr(thenn), transformExpr(elze))
        case N.Match(scrut, cases) =>
          // Returns a transformed pattern along with all bindings
          // from strings to unique identifiers for names bound in the pattern.
          // Also, calls 'fatal' if a new name violates the Amy naming rules.
          def transformPattern(pat: N.Pattern): (S.Pattern, List[(String, Identifier)]) = {
            pat match {
              case N.WildcardPattern() =>
                (S.WildcardPattern().setPos(pat), List())
              case N.IdPattern(name) =>
                val identifier = Identifier.fresh(name)
                (S.IdPattern(identifier).setPos(pat), List((name, identifier)))
              case N.LiteralPattern(literal) =>
                val sLiteral = literal match {
                  case N.UnitLiteral() => S.UnitLiteral().setPos(literal)
                  case N.BooleanLiteral(value) => S.BooleanLiteral(value).setPos(literal)
                  case N.IntLiteral(value) => S.IntLiteral(value).setPos(literal)
                  case N.StringLiteral(value) => S.StringLiteral(value).setPos(literal)
                }
                (S.LiteralPattern(sLiteral).setPos(pat), Nil)
              case N.CaseClassPattern(constructor, args) =>
                val sConstructor = table.getConstructor(constructor.module.getOrElse(module), constructor.name)
                val (identifier, constructorSignature) = sConstructor.getOrElse(fatal(s"Constructor does not exist: ${constructor.module.getOrElse(module)}.${constructor.name}"))
                val argsTransformed = args.map(arg => transformPattern(arg))
                val args2 = argsTransformed.map(arg => arg._1)
                val locals = argsTransformed.flatMap(arg => arg._2)

                if (constructorSignature.argTypes.size != args.size)
                  fatal(s"Wrong number of arguments in ${constructor.module.getOrElse(module)}.${constructor.name}", pat)


                if (locals.map(_._1).distinct.size < locals.size)
                  fatal(s"Multiple definitions of in pattern", pat)

                (S.CaseClassPattern(identifier, args2).setPos(pat), locals)
            }
          }

          def transformCase(cse: N.MatchCase) = {
            val N.MatchCase(pat, rhs) = cse
            val (newPat, moreLocals) = transformPattern(pat)

            val changedLocals = locals.keySet.intersect(moreLocals.toMap.keySet)
            if (changedLocals.nonEmpty)
              fatal(s"Pattern identifier ${changedLocals.head} already defined", pat)
            val newLocals: Map[String, Identifier] = locals ++ moreLocals.toMap

            S.MatchCase(newPat, transformExpr(rhs)(module, (params, newLocals))).setPos(cse)
          }

          S.Match(transformExpr(scrut), cases map transformCase)

        case N.Error(msg) => S.Error(transformExpr(msg))
      }
      res.setPos(expr)
    }

    // Putting it all together to construct the final program for step 6.
    val newProgram = S.Program(
      p.modules map { case mod@N.ModuleDef(name, defs, optExpr) =>
        S.ModuleDef(
          table.getModule(name).get,
          defs map (transformDef(_, name)),
          optExpr map (transformExpr(_)(name, (Map(), Map())))
        ).setPos(mod)
      }
    ).setPos(p)

    (newProgram, table)

  }
}