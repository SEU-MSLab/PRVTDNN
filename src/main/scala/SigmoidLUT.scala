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
import chisel3.util.experimental.loadMemoryFromFile
import firrtl.annotations.MemoryLoadFileType

/**
 * implement the sigmoid function
 * the reason why not add sigmoid in each BA module is that the initial value 
 * need to be copied manually, so each layer need to copy separately
 */

class SigmoidLUT (actWidth: Int = 16, sigmoidDepth: Int = 4096,
                connectNum: Int = 35, file: String = "/tansigData", intNum: Int = 3
                    ) extends Module{
    val io = IO(new Bundle{
        val inAddr      = Input(Vec(connectNum, UInt(actWidth.W)))
        val outData     = Output(Vec(connectNum, SInt(actWidth.W)))
        
    })
        val fileName = file + "_" + sigmoidDepth + ".bin"
        require(intNum >= 2, "intNum must not smaller than 2")
        val sigmoidWidth = log2Ceil(sigmoidDepth)
        // If using Mem, it will not be synthesized, because there are too many MUX
        val memory = SyncReadMem(sigmoidDepth,  SInt(actWidth.W))
        loadMemoryFromFile(memory, fileName, MemoryLoadFileType.Binary)
        val memIndex = Wire(Vec(connectNum, UInt(sigmoidWidth.W)))
        io.inAddr.zip(memIndex)foreach{ case(a,b) =>
            when(a(actWidth-1, actWidth-intNum+1).xorR){
                // handle overflow
                // the largest value is 2047, in Matlab it's 2048
                when(a(actWidth-1) === false.B){ b := (scala.math.pow(2,sigmoidWidth-1)-1).toInt.U}
                // the smallest value is -2048, in Matlab it's -2047
                .otherwise{ b := scala.math.pow(2,sigmoidWidth-1).toInt.U}
            }.otherwise{
                // Ensure 2 bits integer for address index
                b := Cat(a(actWidth-1), a(actWidth-intNum, actWidth-intNum-(sigmoidWidth-2)))
            }
        }
    
        val outReg = Reg(Vec(connectNum, SInt(actWidth.W)))
        outReg.zip(memIndex).map{case(a, b) => a := memory.read(b)}
        io.outData.zip(outReg).map{case(a, b) => a := b}
}
