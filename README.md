Hardware Implementation of Polyphase Real-Valued Time-Delay Neural Networks
=======================

## Introduction
This repo contain the source code of Polyphase Real-Valued Time-Delay Neural Networks Implementation designed in Chisel.

### System Architecture
The architecture include 
- **Systolic array**: to do the matrix multiplication.
- **Bias and activation module**: to add biases and apply activation functions, we implement three, ReLU, Sigmoid and Linear.
- **Parameter loading module**: to propagate weights to systolic arrays and biases to bias and activation modules.
- **Pipeline dataflow**: the DPD is a real-time problem, so multiple systolic arrays are cascade to make a fully pipeline neural network implementation at the expense of more area.

<img src="images/system architecture.png" alt="The system architecture" width="800">

### Performance

The following experiment result using 40 MHz 5G-NR signal with a 3.5 GHz GaN power amplifier. The test board is AMD VCK190 evaluation board.

| #   | Network Configuration| Activation function| Number of parameters |Type | NMSE (dB)| ACPR (dBc)| DSP  | URAM | LUTs | Registers |
|-----|-----------------|---------|-----|------|--------|------------------|-----|------|------ |--------|
| N/A | w/o DPD         | N/A     | N/A | N/A  | -25.35 | -35.98/-33.91    | N/A | N/A  | N/A   | N/A    |
| 1   | [12,10,10,8]    | ReLU    | 328 | S/W  | -31.35 | -43.47/-43.78    | N/A | N/A  | N/A   | N/A    |
|     |                 |         |     | H/W  | -31.28 | -43.29/-43.28    | 300 | 0    | 9034  | 11082  |
| 2   | [20,10,10,8]    | ReLU    | 408 | S/W  | -32.14 | -44.07/-44.77    | N/A | N/A  | N/A   | N/A    |
|     |                 |         |     | H/W  | -31.25 | -43.91/-44.06    | 380 | 0    | 11327 | 13549  |
| 3   | [20,10,10,10,8] | ReLU    | 518 | S/W  | -32.51 | -43.49/-44.45    | N/A | N/A  | N/A   | N/A    |
|     |                 |         |     | H/W  | -32.34 | -43.36/-44.33    | 480 | 0    | 14281 | 16877  |
| 4   | [12,10,10,8]    | sigmoid | 328 | S/W  | -30.10 | -43.95/-44.15    | N/A | N/A  | N/A   | N/A    |
|     |                 |         |     | H/W  | -29.52 | -43.97/-44.18    | 300 | 19   | 9035  | 11334  |
| 5   | [20,10,10,8]    | sigmoid | 408 | S/W  | -34.57 | -46.07/-46.58    | N/A | N/A  | N/A   | N/A    |
|     |                 |         |     | H/W  | -34.03 | -45.19/-46.07    | 380 | 19   | 11328 | 13787  |
| 6   | [20,10,10,10,8] | sigmoid | 518 | S/W  | -34.86 | -48.06/-48.38    | N/A | N/A  | N/A   | N/A    |
|     |                 |         |     | H/W  | -35.28 | -47.63/-47.89    | 480 | 29   | 14313 | 16830  |


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
