# EPFL_CLP_AmyCompiler
The project of the Computer Language Processing (CLP) course given in EPFL. It implements a compiler and an interpreter for Amy Language that is a subset of Scala with limited functionality.

* Amy Language specifications can be found in the file `amy-spec.pdf`

## Example Programs for the Virtual Machine
Our extension was related to the execution of the compiler, thus we did not add a new feature to the language itself.
Every program in the examples folder should run directly when we enter the run command in the sbt mode.

## The Compiler Construction Pipeline
1. Lexer

2. Parser

3. Name Analyzer

4. Type Checker

5. Code Generation

## To run an Amy program:
1. Clone this repository.

2. Install Scala and Node js.

3. Place your Amy program inside the examples folder.

4. Open a command line, navigate here and open sbt by typing `sbt`.

5. To generate web assembly code type `run <dependencies> examples/<your program with .scala extension>` inside sbt, then type `exit` to exit sbt.

6. Type `node wasmout/<your program with .js extension>`

## Example run:
	
```
$ sbt
sbt:amyc> run library/Std.scala examples/Fibonacci.sc
[info] Running amyc.Main library/Std.scala 
examples/Fibonacci.sc
[ Info  ] Grammar is in LL1
-> This program computes the nth fibonacci number 
recursively.
-> Enter the value of n:
<- 10
-> The value of nth fibonacci number is: 55
[success] Total time: 7 s, completed 02-Jan-2019 16:37:59
```
