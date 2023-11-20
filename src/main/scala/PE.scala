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
/**
  * The processing element in Systolic array
  * Weight and Partial Sum reuse the same bus, even they are not the same width
  */

class PE(actWidth: Int = 16, weightWidth: Int = 16, accWidth: Int = 32) extends Module{
    val io = IO(new Bundle{
        val inAct       = Input(SInt(actWidth.W))
        val inWtPS      = Input(SInt(accWidth.W))
        val outWtPS     = Output(SInt(accWidth.W))
        val outAct      = Output(SInt(actWidth.W))
        val inwtValid   = Input((Bool()))
    })

    // Avoid reset by using Reg instead of Reginit
    val weightReg   = Reg(SInt(weightWidth.W))
    val actReg      = Reg(SInt(actWidth.W))
    val parSumReg   = Reg(SInt(accWidth.W))

    actReg    := io.inAct;
    parSumReg := io.inWtPS + weightReg * actReg;
    io.outAct := actReg;

    when(io.inwtValid === 1.U){
        weightReg   := io.inWtPS
        io.outWtPS  := weightReg
    } .otherwise {
        weightReg   := weightReg
        io.outWtPS  := parSumReg
    }
}


