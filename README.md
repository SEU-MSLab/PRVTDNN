Hardware Implementation of Polyphase Real-Valued Time-Delay Neural Networks
=======================

## Introduction
This repo contain the source code of Polyphase Real-Valued Time-Delay Neural Networks Implementation designed in Chisel.

## Prerequisites
* JDK 8 or newer. You can install the JDK as recommended by your operating system, or use the prebuilt binaries from [AdoptOpenJDK](https://adoptopenjdk.net/).
* SBT.  SBT is the most common built tool in the Scala community. You can download it [here](https://www.scala-sbt.org/download.html).  
* Firrtl. Firrtl is the tool to convert firrtl to verilog file. You can download it [here](https://github.com/llvm/circt/releases), and put it to your environment variable $PATH.

## How to get started
```sh
git clone git@github.com:SEU-MSlab/PRVTDNN.git
cd PRVTDNN
```
Generate the Verilog file with 
```sh
sbt run
```
Run the test with
```sh
sbt test
```

You should see a whole bunch of output that ends with something like the following lines
```
[info] Tests: succeeded 1, failed 0, canceled 0, ignored 0, pending 0
[info] All tests passed.
[success] Total time: 5 s, completed Dec 16, 2020 12:18:44 PM
```
