# EPFL_CLP_AmyCompiler

# Example Programs for the Virtual Machine
Our extension was related to the execution of the compiler, thus we did not add a new feature to the language itself.
Every program in the examples folder should run directly when we enter the run command in the sbt mode.

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
