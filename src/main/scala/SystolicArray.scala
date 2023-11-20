/*
MIT License

Copyright (c) 2023 Microwave System Lab @ Southeast University.

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
*/
import chisel3._
import chisel3.util._ 

class SystolicArray(actWidth: Int = 16, weightWidth: Int = 16, accWidth: Int = 32,
                    meshRows: Int = 4, meshColumns: Int = 4 ) extends Module{
  val io = IO(new Bundle{
    val inAct     = Input(Vec(meshRows, SInt(actWidth.W)))
    val inWeight  = Input(Vec(meshColumns, SInt(weightWidth.W)))
    val outSum    = Output(Vec(meshColumns, SInt((accWidth).W)))
    val inwtValid = Input(Bool())
  })


  // PEs(r)(c) represent the PE in row r, column c, start from 0
  val PEs: Seq[Seq[PE]] = Seq.fill(meshRows, meshColumns)(Module(new PE(actWidth, weightWidth, accWidth)))
  val validReg = RegNext(io.inwtValid)
  

  PEs.foreach(_.foreach(_.io.inwtValid := validReg));

  for(row <- 0 until meshRows){
    for(col <- 0 until meshColumns){
      // The input of first row connect to the input
      if(row == 0)  PEs(row)(col).io.inWtPS := io.inWeight(col)
      else          PEs(row)(col).io.inWtPS := PEs(row-1)(col).io.outWtPS

      // The output of last row connect to the output
      if(row == meshRows-1)   io.outSum(col) := PEs(row)(col).io.outWtPS
      
      if(col == 0)  PEs(row)(col).io.inAct := io.inAct(row)
      else          PEs(row)(col).io.inAct := PEs(row)(col-1).io.outAct

      // leave the last column's outAct alone
    }
  }
}
