package amyc.codegen

import amyc.wasm.Module
import amyc.utils.{Context, Pipeline, Env}
import scala.sys.process._
import java.io._

// Prints all 4 different files from a wasm Module
object CodePrinter extends Pipeline[Module, Unit]{
  def run(ctx: Context)(m: Module) = {
    val outDirName = "wasmout"

    def withExt(ext: String) = s"$outDirName/${m.name}.$ext"

    val (local, inPath) = {
      import Env._
      os match {
        case Linux   => ("./bin/wat2wasm",     "wat2wasm")
        case Windows => ("./bin/wat2wasm.exe", "wat2wasm.exe")
        case Mac     => ("./bin/mac/wat2wasm", "wat2wasm")
      }
    }

    val w2wOptions = s"${withExt("wat")} -o ${withExt("wasm")}"

    val outDir = new File(outDirName)
    if (!outDir.exists()) {
      outDir.mkdir()
    }

    m.writeWasmText(withExt("wat"))

    try {
      try {
        s"$local $w2wOptions".!!
      } catch {
        case _: IOException =>
          s"$inPath $w2wOptions".!!
      }
    } catch {
      case _: IOException =>
        ctx.reporter.fatal(
          "wat2wasm utility was not found under ./bin or in system path, " +
          "or did not have permission to execute"
        )
      case _: RuntimeException =>
        ctx.reporter.fatal(s"wat2wasm failed to translate WebAssembly text file ${withExt("wat")} to binary")
    }

    m.writeHtmlWrapper(withExt("html"), withExt("wasm"))
    m.writeNodejsWrapper(withExt("js"), withExt("wasm"))

  }
}
