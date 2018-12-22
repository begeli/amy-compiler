package amyc.virtualMachine

import amyc.codegen.{Utils => CodeGenUtils}
import amyc.utils.{Context, Pipeline}
import amyc.wasm.Instructions._
import amyc.wasm._

import scala.io.StdIn

object VirtualMachine extends Pipeline[Module, Unit] {

  // Sizes for VM structures
  val SIZE_OF_MAIN_STACK: Int = 1000000
  val SIZE_OF_CALL_STACK: Int = 1000000
  val SIZE_OF_DATA_MEMORY: Int = 1000000
  val NUMBER_OF_GLOBALS: Int = 10

  // Number of slots used by each call frame, excluding the locals
  val NUMBER_OF_FRAME_DATA_FIELDS: Int = 3

  // Sentinel value signifying end of program
  val END_OF_PROGRAM: Int = -1

  // Error code for Unreachable instruction
  val UNREACHABLE_CODE: Int = -1

  // Given list of functions, positions main function before others, returns main function and new list
  def orderFunctions(unordered: List[Function]): (Function, List[Function]) = {
    val mainFunction: Function = unordered.filter(_.isMain).head
    val otherFunctions: List[Function] = unordered.filter(!_.isMain)
    (mainFunction, mainFunction :: otherFunctions)
  }

  // Given list of functions, returns a map that holds the starting index for each function name
  def mapFunctionsToIndices(functions: List[Function]): Map[String, Int] = {
    functions.map(function => (function.name, (function.code <:> Return).instructions))
      .flatMap(pair => pair._2.zipWithIndex.map { instrIndexPair =>
        if (instrIndexPair._2 == 0) (instrIndexPair._1, pair._1)
        else (instrIndexPair._1, "")
      }).zipWithIndex.filter(pair => pair._1._2 != "").map(pair => (pair._1._2, pair._2)).toMap
  }

  // Given list of functions, converts them to an array of instructions
  // and inserts Return instruction at the end of each function
  def functionsToInstructionArray(functions: List[Function]): Array[Instructions.Instruction] = {
    functions.flatMap(function => (function.code <:> Return).instructions).toArray
  }

  // Given the pointer of If instruction, returns the indices of matching Else and End instructions
  def findBoundariesOfIfInstruction(ifInstructionPointer: Int, instructionMemory: Array[Instructions.Instruction]): (Int, Int) = {
    // Find the end of if block
    var nestedCounter = 0
    var elseInstructionPointer = ifInstructionPointer

    // Iterate over the instructions between if and else until it finds the matching else instruction
    do {
      val currentInstruction = instructionMemory(elseInstructionPointer)
      if (currentInstruction == If_i32 || currentInstruction == If_void)
        nestedCounter = nestedCounter + 1
      else if (currentInstruction == Else)
        nestedCounter = nestedCounter - 1

      elseInstructionPointer = elseInstructionPointer + 1
    } while (nestedCounter != 0)

    // After this line, elseInstructionPointer points to Else instruction
    elseInstructionPointer = elseInstructionPointer - 1

    // Find the end of else block
    var endInstructionPointer = elseInstructionPointer

    // Iterate over the instructions between else and end until it finds the matching end instructions
    do {
      val currentInstruction = instructionMemory(endInstructionPointer)
      if (currentInstruction == Else)
        nestedCounter = nestedCounter + 1
      else if (currentInstruction == End)
        nestedCounter = nestedCounter - 1

      endInstructionPointer = endInstructionPointer + 1
    } while (nestedCounter != 0)

    // After this line, endInstructionPointer points to End instruction
    endInstructionPointer = endInstructionPointer - 1

    // Return the pair of indices
    (elseInstructionPointer, endInstructionPointer)
  }

  override def run(ctx: Context)(m: Module): Unit = {

    // Define instruction memory and PC
    val (mainFunction, functions) = orderFunctions(m.functions)
    val instructionMemory: Array[Instructions.Instruction] = functionsToInstructionArray(functions)
    var programCounter = 0 // Always points to next instruction to be executed

    // Define main stack, the working stack of WebAssembly
    val mainStack: Array[Int] = new Array[Int](SIZE_OF_MAIN_STACK)
    var msPointer = 0 // Always points to the first unused slot in main stack

    // Define function call stack
    val callStack: Array[Int] = new Array[Int](SIZE_OF_CALL_STACK)
    var csPointer = 0 // Always points to the base slot (ie return address) of the current function

    /*
     *  Each call frame holds the following:
     *  (0th slot) return address of the function
     *  (1st slot) number of locals of the function's caller
     *  (2nd slot) number of locals of the function itself
     *  (rest) locals belonging to the function
     */

    // Initialize call frame for main function
    callStack(0) = END_OF_PROGRAM // Return address for main
    callStack(1) = 0 // Number of locals of caller (taken to be 0 for main)
    callStack(2) = mainFunction.locals // Number of locals of main

    // Define data memory, linear memory of bytes
    val dataMemory: Array[Byte] = new Array[Byte](SIZE_OF_DATA_MEMORY)

    // Define memory for globals, 0th global is always used for memory boundary
    val globals: Array[Int] = new Array[Int](NUMBER_OF_GLOBALS)

    // Map that holds the index for each function
    val functionNamesAndStartingIndices: Map[String, Int] = mapFunctionsToIndices(functions)

    // Map to record the index for each label, this is done as instructions are executed
    var labelsAndIndices: Map[String, Integer] = Map()

    // Map to record the index of matching if/else/end instructions.
    var ifElseEndIndices: Map[Int, (Int, Int)] = Map()

    // Execution loop
    while (true) {
      if (programCounter == END_OF_PROGRAM) return

      val instruction: Instructions.Instruction = instructionMemory(programCounter)
      programCounter = executeInstruction(instruction)
    }

    // Function to match and handle WASM instructions, returns next value of PC
    def executeInstruction(instruction: Instructions.Instruction): Int = {
      instruction match {

        // Load an int32 constant to the stack
        case Const(value) =>
          mainStack(msPointer) = value
          msPointer = msPointer + 1
          programCounter + 1

        // Numeric binary operations

        case Add =>
          msPointer = msPointer - 1
          val value1 = mainStack(msPointer)

          msPointer = msPointer - 1
          val value2 = mainStack(msPointer)

          mainStack(msPointer) = value2 + value1
          msPointer = msPointer + 1
          programCounter + 1

        case Sub =>
          msPointer = msPointer - 1
          val value1 = mainStack(msPointer)

          msPointer = msPointer - 1
          val value2 = mainStack(msPointer)

          mainStack(msPointer) = value2 - value1
          msPointer = msPointer + 1
          programCounter + 1

        case Mul =>
          msPointer = msPointer - 1
          val value1 = mainStack(msPointer)

          msPointer = msPointer - 1
          val value2 = mainStack(msPointer)

          mainStack(msPointer) = value2 * value1
          msPointer = msPointer + 1
          programCounter + 1

        case Div =>
          msPointer = msPointer - 1
          val value1 = mainStack(msPointer)

          msPointer = msPointer - 1
          val value2 = mainStack(msPointer)

          mainStack(msPointer) = value2 / value1
          msPointer = msPointer + 1
          programCounter + 1

        case Rem =>
          msPointer = msPointer - 1
          val value1 = mainStack(msPointer)

          msPointer = msPointer - 1
          val value2 = mainStack(msPointer)

          mainStack(msPointer) = value2 % value1
          msPointer = msPointer + 1
          programCounter + 1

        // Logical binary operations

        case And =>
          msPointer = msPointer - 1
          val value1 = !(mainStack(msPointer) == 0)

          msPointer = msPointer - 1
          val value2 = !(mainStack(msPointer) == 0)

          mainStack(msPointer) = if (value2 && value1) 1 else 0
          msPointer = msPointer + 1
          programCounter + 1

        case Or =>
          msPointer = msPointer - 1
          val value1 = !(mainStack(msPointer) == 0)

          msPointer = msPointer - 1
          val value2 = !(mainStack(msPointer) == 0)

          mainStack(msPointer) = if (value2 || value1) 1 else 0
          msPointer = msPointer + 1
          programCounter + 1

        // Comparison operations

        case Eqz =>
          msPointer = msPointer - 1
          val value = mainStack(msPointer)

          mainStack(msPointer) = if (value == 0) 1 else 0
          msPointer = msPointer + 1
          programCounter + 1

        case Lt_s =>
          msPointer = msPointer - 1
          val value1 = mainStack(msPointer)

          msPointer = msPointer - 1
          val value2 = mainStack(msPointer)

          mainStack(msPointer) = if (value2 < value1) 1 else 0
          msPointer = msPointer + 1
          programCounter + 1

        case Le_s =>
          msPointer = msPointer - 1
          val value1 = mainStack(msPointer)

          msPointer = msPointer - 1
          val value2 = mainStack(msPointer)

          mainStack(msPointer) = if (value2 <= value1) 1 else 0
          msPointer = msPointer + 1
          programCounter + 1

        case Eq =>
          msPointer = msPointer - 1
          val value1 = mainStack(msPointer)

          msPointer = msPointer - 1
          val value2 = mainStack(msPointer)

          mainStack(msPointer) = if (value2 == value1) 1 else 0
          msPointer = msPointer + 1
          programCounter + 1

        // Discards the item on top of the stack
        case Drop =>
          msPointer = msPointer - 1
          programCounter + 1

        // Instruction to signify the end of a function
        case Return =>
          val newProgramCounter = callStack(csPointer) // Set aside return address
          csPointer = csPointer - callStack(csPointer + 1) - NUMBER_OF_FRAME_DATA_FIELDS // Discard call frame
          newProgramCounter // Set PC to return address

        // Conditional instructions
        case If_i32 | If_void =>
          msPointer = msPointer - 1
          val ifConditionValue = mainStack(msPointer)

          // Try to get indices from map if they exist, run the helper function to find and add them to map otherwise
          val elseEndIndicesOption = ifElseEndIndices.get(programCounter)
          val (elseInstruction, endInstruction) = elseEndIndicesOption.getOrElse{
            val elseEndPair = findBoundariesOfIfInstruction(programCounter, instructionMemory)
            ifElseEndIndices += (programCounter -> (elseEndPair._1, elseEndPair._2))
            elseEndPair
          }

          // This method executes the instruction between two indices
          def blockExecutor(indexOfFirstInstr: Int, indexOfLastInstruction: Int): Int = {
            // Program Counter points to first instruction of if block
            programCounter = indexOfFirstInstr
            var isBranch = false

            // Run the instructions inside the block until it is the last instruction or branch instruction
            while (programCounter != indexOfLastInstruction && !isBranch) {
              val currentInstruction = instructionMemory(programCounter)

              if (currentInstruction.isInstanceOf[Br]) isBranch = true
              else programCounter = executeInstruction(currentInstruction)
            }

            // Return the new program counter depending on last instruction of block
            if (instructionMemory(programCounter).isInstanceOf[Br]) executeInstruction(instructionMemory(programCounter))
            else endInstruction + 1
          }

          // Run either if or else block depending on the condition value
          if (ifConditionValue != 0) blockExecutor(programCounter + 1, elseInstruction)
          else blockExecutor(elseInstruction + 1, endInstruction)

        case End =>
          programCounter + 1 // Just fall through

        case Loop(label) =>
          labelsAndIndices += (label -> programCounter) // Record label and its index
          programCounter + 1 // Fall through

        case Br(label) =>
          labelsAndIndices(label) + 1 // Look up and branch to index of label

        case Call(name) =>
          name match {
            // Built-in functions
            case "Std_printInt" =>
              val value = mainStack(msPointer - 1)
              println(value)
              programCounter + 1

            case "Std_printString" =>
              val start = mainStack(msPointer - 1)
              var stringVal = ""
              var stringCounter = start
              // loop and and accumulate chars until null terminator
              while (dataMemory(stringCounter) != 0) {
                stringVal = stringVal ++ dataMemory(stringCounter).toChar.toString
                stringCounter = stringCounter + 1
              }
              println(stringVal)
              programCounter + 1

            case "Std_readInt" =>
              val value: Int = StdIn.readInt()
              mainStack(msPointer) = value
              msPointer = msPointer + 1
              programCounter + 1

            case "Std_readString" =>
              val value: String = StdIn.readLine()
              val mkStringCode: Code = CodeGenUtils.mkString(value) // generate code that writes the string to memory
            val previousProgramCounter = programCounter
              mkStringCode.instructions.foreach(executeInstruction) // stop program execution, execute generated code
              previousProgramCounter + 1 // resume program execution from where we left off

            // User defined functions
            case _ =>
              val targetFunction = functions.filter(_.name == name).head

              // prepare the new call frame
              val returnAddress = programCounter + 1
              val numOfLocalsOfCurrent = callStack(csPointer + 2)
              val numOfLocalsOfCallee = targetFunction.args + targetFunction.locals

              csPointer = csPointer + NUMBER_OF_FRAME_DATA_FIELDS + numOfLocalsOfCurrent
              callStack(csPointer) = returnAddress
              callStack(csPointer + 1) = numOfLocalsOfCurrent
              callStack(csPointer + 2) = numOfLocalsOfCallee

              // pop arguments from main stack and place on call stack
              var argCounter = targetFunction.args
              while (argCounter > 0) {
                msPointer = msPointer - 1
                val localValue = mainStack(msPointer)
                callStack(argCounter + csPointer + NUMBER_OF_FRAME_DATA_FIELDS - 1) = localValue
                argCounter = argCounter - 1
              }

              // jump to index of called function
              functionNamesAndStartingIndices(targetFunction.name)
          }

        case Unreachable =>
          System.exit(UNREACHABLE_CODE) // Exit with error code
          END_OF_PROGRAM

        // Operations for locals (parameters, local variables)
        case GetLocal(index) =>
          val value = callStack(index + NUMBER_OF_FRAME_DATA_FIELDS + csPointer)
          mainStack(msPointer) = value
          msPointer = msPointer + 1
          programCounter + 1

        case SetLocal(index) =>
          msPointer = msPointer - 1
          val value = mainStack(msPointer)

          callStack(index + NUMBER_OF_FRAME_DATA_FIELDS + csPointer) = value
          programCounter + 1

        // Operations for globals
        case GetGlobal(index) =>
          mainStack(msPointer) = globals(index)
          msPointer = msPointer + 1
          programCounter + 1

        case SetGlobal(index) =>
          msPointer = msPointer - 1
          val value = mainStack(msPointer)
          globals(index) = value
          programCounter + 1

        // Memory operations

        // Store an i32 value to memory
        // Expects value on top of the stack, and address under it
        case Store =>
          msPointer = msPointer - 1
          val value: Int = mainStack(msPointer)

          msPointer = msPointer - 1
          val address: Int = mainStack(msPointer)
          dataMemory(address + 3) = ((value & 0xFF000000) >> 3 * 8).toByte
          dataMemory(address + 2) = ((value & 0x00FF0000) >> 2 * 8).toByte
          dataMemory(address + 1) = ((value & 0x0000FF00) >> 1 * 8).toByte
          dataMemory(address + 0) = ((value & 0x000000FF) >> 0 * 8).toByte

          programCounter + 1

        // Load an i32 value from memory
        // Expects address on top of the stack
        case Load =>
          msPointer = msPointer - 1
          val address: Int = mainStack(msPointer)

          val byte3: Int = (dataMemory(address + 0) & 0xFF) << 0
          val byte2: Int = (dataMemory(address + 1) & 0xFF) << 8
          val byte1: Int = (dataMemory(address + 2) & 0xFF) << 16
          val byte0: Int = (dataMemory(address + 3) & 0xFF) << 24

          val value = byte0 + byte1 + byte2 + byte3

          mainStack(msPointer) = value
          msPointer = msPointer + 1

          programCounter = programCounter + 1
          programCounter

        // Store a single byte value to memory (the least significant byte of the operand)
        // Expects value on top of the stack, and address under it
        case Store8 =>
          msPointer = msPointer - 1
          val value: Int = mainStack(msPointer)
          val leastSignificantByte: Int = value & 0x000000FF

          msPointer = msPointer - 1
          val address: Int = mainStack(msPointer)

          dataMemory(address) = leastSignificantByte.toByte
          programCounter + 1

        // Load an i32 value from memory, zero extend it to the size of an i32
        // Expects address on top of the stack
        case Load8_u =>
          msPointer = msPointer - 1
          val address: Int = mainStack(msPointer)

          val value: Int = dataMemory(address) & 0x000000FF
          mainStack(msPointer) = value
          msPointer = msPointer + 1

          programCounter + 1
      }
    }
  }
}
