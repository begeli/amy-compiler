package amyc.virtualMachine

import amyc.codegen.{Utils => CodeGenUtils}
import amyc.utils.{Context, Pipeline}
import amyc.wasm.Instructions._
import amyc.wasm._

import scala.io.StdIn

object VirtualMachine extends Pipeline[Module, Unit] {
  //Define the number of slots each call frame uses(except for the locals)
  val FRAME_DATA_FIELD_NUM: Int = 3

  //Define EOP which indicates the end of program
  val EOP: Int = -1

  override def run(ctx: Context)(m: Module): Unit = {
    //Define program counter and instruction memory
    //Put main method before other methods
    val (mainMethod, methods) = (m.functions.filter(_.isMain).head, m.functions.filter(_.isMain) ::: m.functions.filter(!_.isMain))
    //Convert methods to array of instructions
    val instMem: Array[Instructions.Instruction] = methods.flatMap(function => (function.code <:> Return).instructions).toArray
    var pc = 0

    //Define method call stack
    val CALL_STACK_SIZE: Int = 4000000
    val callStack: Array[Int] = new Array[Int](CALL_STACK_SIZE)
    var csPointer = 0

    //Define the main stack
    val MAIN_STACK_SIZE: Int = 4000000
    val mainStack: Array[Int] = new Array[Int](MAIN_STACK_SIZE)
    var msPointer = 0

    //Initialize stack frame for main method
    callStack(0) = EOP
    callStack(1) = 0
    callStack(2) = mainMethod.locals

    //Define data memory and global memory
    val DATA_MEMORY_SIZE: Int = 4000000
    val dataMem: Array[Byte] = new Array[Byte](DATA_MEMORY_SIZE)
    val GLOBAL_NUM: Int = 10
    val globals: Array[Int] = new Array[Int](GLOBAL_NUM)

    //Hold the index of each method
    val methodStartIndices: Map[String, Int] = {
      methods.map(function => (function.name, (function.code <:> Return).instructions))
        .flatMap(pair => pair._2.zipWithIndex.map { instrIndexPair =>
          if (instrIndexPair._2 == 0) (instrIndexPair._1, pair._1)
          else (instrIndexPair._1, "")
        }).zipWithIndex.filter(pair => pair._1._2 != "").map(pair => (pair._1._2, pair._2)).toMap
    }

    //Record the index of matching if else end instructions
    var iteEndIndices: Map[Int, (Int, Int)] = Map()

    //Record the index for each label
    var labelsAndIndices: Map[String, Integer] = Map()

    while (true) {
      if (pc == EOP) return
      val instruction: Instructions.Instruction = instMem(pc)
      pc = executeInstruction(instruction)
    }

    def movePC(value: Int): Int = {
      mainStack(msPointer) = value
      msPointer = msPointer + 1
      pc + 1
    }

    def updateMSPointer(): (Int, Int) = {
      msPointer = msPointer - 1
      val value1 = mainStack(msPointer)
      msPointer = msPointer - 1
      val value2 = mainStack(msPointer)
      (value1, value2)
    }

    def updateMSPointerForAndOr(): (Boolean, Boolean) = {
      msPointer = msPointer - 1
      val value1 = !(mainStack(msPointer) == 0)
      msPointer = msPointer - 1
      val value2 = !(mainStack(msPointer) == 0)
      (value1, value2)
    }

    def store(address: Int, value: Int): Unit = {
      dataMem(address + 0) = ((value & 0x000000FF) >> 0 * 8).toByte
      dataMem(address + 1) = ((value & 0x0000FF00) >> 1 * 8).toByte
      dataMem(address + 2) = ((value & 0x00FF0000) >> 2 * 8).toByte
      dataMem(address + 3) = ((value & 0xFF000000) >> 3 * 8).toByte
    }

    def load(address: Int): Int = {
      val byte0: Int = (dataMem(address + 3) & 0xFF) << 24
      val byte1: Int = (dataMem(address + 2) & 0xFF) << 16
      val byte2: Int = (dataMem(address + 1) & 0xFF) << 8
      val byte3: Int = (dataMem(address + 0) & 0xFF) << 0

      byte0 + byte1 + byte2 + byte3
    }

    def updateMSPointerSingleByte(): Int = {
      msPointer = msPointer - 1
      mainStack(msPointer)
    }

    def findMatchingElseInstruction(elsePointer: Int, counter: Int): Int = {
      var countr = counter
      var ep = elsePointer
      do {
        val currentInstruction = instMem(ep)
        if (currentInstruction == If_i32 || currentInstruction == If_void)
          countr = countr + 1
        else if (currentInstruction == Else)
          countr = countr - 1
        ep = ep + 1
      } while (countr != 0)

      ep
    }

    def findMatchingEndInstruction(endPointer: Int, counter: Int): Int = {
      var countr = counter
      var ep = endPointer
      do {
        val currentInstruction = instMem(ep)
        if (currentInstruction == Else)
          countr = countr + 1
        else if (currentInstruction == End)
          countr = countr - 1

        ep = ep + 1
      } while (countr != 0)

      ep
    }

    def executeIfInstructions(): Int = {
      msPointer = msPointer - 1
      val ifCondVal = mainStack(msPointer)
      val (elseInstruction, endInstruction) = iteEndIndices.getOrElse(pc, {
        val elseEndPair = {
          var elsePointer = pc

          elsePointer = findMatchingElseInstruction(elsePointer, 0)
          elsePointer = elsePointer - 1

          var endPointer = elsePointer
          endPointer = findMatchingEndInstruction(endPointer, 0)
          endPointer = endPointer - 1

          (elsePointer, endPointer)
        }
        iteEndIndices += (pc -> (elseEndPair._1, elseEndPair._2))
        elseEndPair
      })

      //Execute the instruction between two indices
      def executeBlock(firstIndex: Int, lastIndex: Int): Int = {
        pc = firstIndex
        var branch = false

        //Execute the instructions until reached the last instruction or branch
        while (pc != lastIndex && !branch) {
          val current = instMem(pc)
          if (current.isInstanceOf[Br]) branch = true
          else pc = executeInstruction(current)
        }

        if (instMem(pc).isInstanceOf[Br]) executeInstruction(instMem(pc))
        else endInstruction + 1
      }

      //Execute if or else
      if (ifCondVal != 0) executeBlock(pc + 1, elseInstruction)
      else executeBlock(elseInstruction + 1, endInstruction)
    }

    def executeNonStdInstructions(name: String): Int = {
      val otherMethod = methods.filter(_.name == name).head
      val returnAddress = pc + 1
      val numOfLocalsCur = callStack(csPointer + 2)
      val numOfLocalsOther = otherMethod.args + otherMethod.locals

      csPointer = csPointer + FRAME_DATA_FIELD_NUM + numOfLocalsCur
      callStack(csPointer) = returnAddress
      callStack(csPointer + 1) = numOfLocalsCur
      callStack(csPointer + 2) = numOfLocalsOther

      var argCounter = otherMethod.args
      while (argCounter > 0) {
        msPointer = msPointer - 1
        callStack(argCounter + csPointer + FRAME_DATA_FIELD_NUM - 1) = mainStack(msPointer)
        argCounter = argCounter - 1
      }
      methodStartIndices(otherMethod.name)
    }

    //Method to match and handle web assembly instructions
    def executeInstruction(instruction: Instructions.Instruction): Int = {
      instruction match {
        case GetLocal(index) =>
          mainStack(msPointer) = callStack(index + FRAME_DATA_FIELD_NUM + csPointer)
          msPointer = msPointer + 1
          pc + 1

        case GetGlobal(index) =>
          mainStack(msPointer) = globals(index)
          msPointer = msPointer + 1
          pc + 1

        case SetLocal(index) =>
          msPointer = msPointer - 1
          callStack(index + FRAME_DATA_FIELD_NUM + csPointer) = mainStack(msPointer)
          pc + 1

        case SetGlobal(index) =>
          msPointer = msPointer - 1
          globals(index) = mainStack(msPointer)
          pc + 1

        case Store =>
          val (value, address) = updateMSPointer()
          store(address, value)
          pc + 1

        case Store8 =>
          val leastSignificantByte: Int = updateMSPointerSingleByte() & 0x000000FF
          msPointer = msPointer - 1
          val address: Int = mainStack(msPointer)
          dataMem(address) = leastSignificantByte.toByte
          pc + 1

        case Load =>
          msPointer = msPointer - 1

          //Load address
          mainStack(msPointer) = load(mainStack(msPointer))
          msPointer = msPointer + 1
          pc = pc + 1
          pc

        case Load8_u =>
          //Load an i32 value from memory and zero extend
          val address = updateMSPointerSingleByte()
          mainStack(msPointer) = dataMem(address) & 0x000000FF
          msPointer = msPointer + 1
          pc + 1

        case Loop(label) =>
          labelsAndIndices += (label -> pc)
          pc + 1

        case Br(label) =>
          labelsAndIndices(label) + 1

        case Call(name) =>
          name match {
            case "Std_readInt" =>
              mainStack(msPointer) = StdIn.readInt()
              msPointer = msPointer + 1
              pc + 1

            case "Std_printInt" =>
              println(mainStack(msPointer - 1))
              pc + 1

            case "Std_readString" =>
              //Write string to memory
              val mkStringCode: Code = CodeGenUtils.mkString(StdIn.readLine())
              val tempPC = pc
              mkStringCode.instructions.foreach(executeInstruction)
              tempPC + 1

            case "Std_printString" =>
              var string = ""
              var counter = mainStack(msPointer - 1)
              //Loop until reached null terminator
              while (dataMem(counter) != 0) {
                string = string ++ dataMem(counter).toChar.toString
                counter = counter + 1
              }
              println(string)
              pc + 1

            case _ =>
              executeNonStdInstructions(name)
          }

        case Const(value) =>
          movePC(value)

        case Add =>
          val (value1, value2) = updateMSPointer()
          movePC(value1 + value2)

        case Sub =>
          val (value1, value2) = updateMSPointer()
          movePC(value2 - value1)

        case Mul =>
          val (value1, value2) = updateMSPointer()
          movePC(value2 * value1)

        case Div =>
          val (value1, value2) = updateMSPointer()
          movePC(value2 / value1)

        case Rem =>
          val (value1, value2) = updateMSPointer()
          movePC(value2 % value1)

        case Lt_s =>
          val (value1, value2) = updateMSPointer()
          movePC(if (value2 < value1) 1 else 0)

        case Le_s =>
          val (value1, value2) = updateMSPointer()
          movePC(if (value2 <= value1) 1 else 0)

        case And =>
          val (value1, value2) = updateMSPointerForAndOr()
          movePC(if (value2 && value1) 1 else 0)

        case Or =>
          val (value1, value2) = updateMSPointerForAndOr()
          movePC(if (value2 || value1) 1 else 0)

        case Eq =>
          val (value1, value2) = updateMSPointer()
          movePC(if (value2 == value1) 1 else 0)

        case Eqz =>
          msPointer = msPointer - 1
          val value = mainStack(msPointer)
          movePC(if (value == 0) 1 else 0)

        case Drop =>
          msPointer = msPointer - 1
          pc + 1

        case Return =>
          val tempReturnAddress = callStack(csPointer)
          csPointer = csPointer - callStack(csPointer + 1) - FRAME_DATA_FIELD_NUM
          tempReturnAddress

        case If_i32 =>
          executeIfInstructions()

        case If_void =>
          executeIfInstructions()

        case End =>
          pc + 1

        case Unreachable =>
          val ERROR_CODE: Int = -1
          System.exit(ERROR_CODE)
          EOP
      }
    }
  }
}