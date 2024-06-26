import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.*;

public class Pipeline {
    private Stack<Integer> stack = new Stack();//stack stores program counter
    private Instruction[] inFlightInstructions = new Instruction[4];//instructions in the pipeline

    public Memory2 memory;
        
    public int[] registers = new int[16];//register file
    private boolean[] pendingRegisters = new boolean[16];//registers in use by other instructions
    
    private int[] instructionRegisters = new int[64];//register file of instructions

    private Assembler assembler = new Assembler();//assembler object

    public Pipeline(Memory2 memory) {
        registers[14] = 7;//program status
        registers[15] = 0;//program counter
        
        this.memory = memory;

        assembler.assemble();

        fillInstructionCache();

        fillPipeline();
    }

    //fills instruction registers with machine code instructions
    public void fillInstructionCache() {
        //halt instruction signifies end of program
        int iHALT = -201326592;
        for (int i = 0; i < instructionRegisters.length; i++) {
            instructionRegisters[i] = iHALT;
        }
        BufferedReader reader;
		try {
			reader = new BufferedReader(new FileReader("program.txt"));
			String line = reader.readLine();

			for (int i = 0; line != null; i++) {
                instructionRegisters[i] = Integer.valueOf(line);
				// read next line
				line = reader.readLine();
			}

			reader.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
    }

    //initially fills the pipeline with stall instructions
    public void fillPipeline() {
        for (int i = 0; i < inFlightInstructions.length; i++) {
            inFlightInstructions[i] = new Instruction(0);//initialize instructions in pipeline
            inFlightInstructions[i].type = 6;
        }
    }
    //sets the conddition code of all instructions so that they're squashed
    private void squashPipeline() {
        for (int i = 0; i < inFlightInstructions.length; i++) {
            inFlightInstructions[i].cond = 6;//squash instructions in pipeline
        }
    }

    private Instruction stall() {
        Instruction i = new Instruction(-1610612736);
        i.cond = 5;
        return i;
    }

    //fetches instruction according to value in the PC register
    private Instruction fetch() {
        return new Instruction(instructionRegisters[registers[15]++]);
    }

    //decode function decodes an instruction
    private Instruction decode(Instruction i) {
        if (i.cond == 6 || i.cond == 5) {
            return i;
        }
        i.cond = i.instruction >>> 29;
        i.type = i.instruction << 3 >>> 29;
        i.opcode = i.instruction << 6 >>> 28;
        if (i.type == 0) {//integer arithmetic
            if (i.opcode == 10) {//comparison format 1
                i.source1 = i.instruction << 10 >>> 28;
                if (pendingRegisters[i.source1]) {
                    return stall();
                }
                i.source2 = i.instruction << 14 >>> 28;
                if (pendingRegisters[i.source2]) {
                    return stall();
                }
                pendingRegisters[i.source1] = true;
                pendingRegisters[i.source2] = true;
                i.immediate = i.instruction << 18 >>> 18;
            } else if (i.opcode == 11) {//comparison format 2
                i.source1 = i.instruction << 10 >>> 28;
                if (pendingRegisters[i.source1]) {
                    return stall();
                }
                pendingRegisters[i.source1] = true;
                i.immediate = i.instruction << 14 >>> 14;
            } else if (i.opcode % 2 == 0) {//even arithmetic have the same format
                i.destination = i.instruction << 10 >>> 28;
                if (pendingRegisters[i.destination]) {
                    return stall();
                }
                i.source1 = i.instruction << 14 >>> 28;
                if (pendingRegisters[i.source1]) {
                    return stall();
                }
                i.source2 = i.instruction << 18 >>> 28;
                if (pendingRegisters[i.source2]) {
                    return stall();
                }
                pendingRegisters[i.destination] = true;
                pendingRegisters[i.source1] = true;
                pendingRegisters[i.source2] = true;
                i.immediate = i.instruction << 22 >>> 22;
            } else {
                i.destination = i.instruction << 10 >>> 28;
                if (pendingRegisters[i.destination]) {
                    return stall();
                }
                i.source1 = i.instruction << 14 >>> 28;
                if (pendingRegisters[i.source1]) {
                    return stall();
                }
                pendingRegisters[i.destination] = true;
                pendingRegisters[i.source1] = true;
                i.immediate = i.instruction << 22 >>> 22;
            }
        } else if (i.type == 1) {
            if (i.opcode == 8) {
                i.source1 = i.instruction << 10 >>> 28;
                if (pendingRegisters[i.source1]) {
                    return stall();
                }
                i.source2 = i.instruction << 14 >>> 28;
                if (pendingRegisters[i.source2]) {
                    return stall();
                }
                pendingRegisters[i.source1] = true;
                pendingRegisters[i.source2] = true;
                i.immediate = i.instruction << 22 >>> 22;
            } else if  (i.opcode == 9) {
                i.source1 = i.instruction << 10 >>> 28;
                if (pendingRegisters[i.source1]) {
                    return stall();
                }
                pendingRegisters[i.source1] = true;
                i.immediate = i.instruction << 14 >>> 14;
            } else if (i.opcode % 2 == 0) {
                i.destination = i.instruction << 10 >>> 28;
                if (pendingRegisters[i.destination]) {
                    return stall();
                }
                i.source1 = i.instruction << 14 >>> 28;
                if (pendingRegisters[i.source1]) {
                    return stall();
                }
                i.source2 = i.instruction << 18 >>> 28;
                if (pendingRegisters[i.source2]) {
                    return stall();
                }
                pendingRegisters[i.destination] = true;
                pendingRegisters[i.source1] = true;
                pendingRegisters[i.source2] = true;
                i.offset = i.instruction << 22 >>> 22;
            } else {
                i.destination = i.instruction << 10 >>> 28;
                if (pendingRegisters[i.destination]) {
                    return stall();
                }
                i.source1 = i.instruction << 14 >>> 28;
                if (pendingRegisters[i.source1]) {
                    return stall();
                }
                pendingRegisters[i.destination] = true;
                pendingRegisters[i.source1] = true;
                i.immediate = i.instruction << 22 >>> 22;
            }
        } else if (i.type == 2) {
            if (i.opcode == 0 || i.opcode == 2) {
                i.destination = i.instruction << 10 >>> 28;
                if (pendingRegisters[i.destination]) {
                    return stall();
                }
                i.source1 = i.instruction << 14 >>> 28;
                if (pendingRegisters[i.source1]) {
                    return stall();
                }
                i.source2 = i.instruction << 18 >>> 28;
                if (pendingRegisters[i.source2]) {
                    return stall();
                }
                pendingRegisters[i.destination] = true;
                pendingRegisters[i.source1] = true;
                pendingRegisters[i.source2] = true;
                i.offset = i.instruction << 22 >>> 22;
            } else if (i.opcode == 1 || i.opcode == 3) {
                i.destination = i.instruction << 10 >>> 28;
                if (pendingRegisters[i.destination]) {
                    return stall();
                }
                i.source1 = i.instruction << 14 >>> 28;
                if (pendingRegisters[i.source1]) {
                    return stall();
                }
                pendingRegisters[i.destination] = true;
                pendingRegisters[i.source1] = true;
                i.immediate = i.instruction << 22 >>> 22;
            } else if (i.opcode == 4 || i.opcode == 5) {
                i.destination = i.instruction << 10 >>> 28;
                if (pendingRegisters[i.destination]) {
                    return stall();
                }
                i.source1 = i.instruction << 14 >>> 28;
                if (pendingRegisters[i.source1]) {
                    return stall();
                }
                pendingRegisters[i.destination] = true;
                pendingRegisters[i.source1] = true;
                i.immediate = i.instruction << 22 >>> 22;
            } else {
                i.destination = i.instruction << 10 >>> 28;
                if (pendingRegisters[i.destination]) {
                    return stall();
                }
                i.source1 = i.instruction << 14 >>> 28;
                if (pendingRegisters[i.source1]) {
                    return stall();
                }
                pendingRegisters[i.destination] = true;
                pendingRegisters[i.source1] = true;
                i.immediate = i.instruction << 22 >>> 22;
            }
        } else if (i.type == 3) {
            if (i.opcode < 7) {
                i.destination = i.instruction << 10 >>> 28;
                if (pendingRegisters[i.destination]) {
                    return stall();
                }
                i.source1 = i.instruction << 14 >>> 28;
                if (pendingRegisters[i.source1]) {
                    return stall();
                }
                pendingRegisters[i.destination] = true;
                pendingRegisters[i.source1] = true;
                i.immediate = i.instruction << 18 >>> 18;
            } else if (i.opcode == 7) {
                i.destination = i.instruction << 10 >>> 28;
                if (pendingRegisters[i.destination]) {
                    return stall();
                }
                pendingRegisters[i.destination] = true;
                i.immediate = i.instruction << 14 >>> 14;
            }
        } else if (i.type == 4) {
            i.immediate = i.instruction << 22 >>> 22;
        } else if (i.type == 5){
            //do nothing
        } else {
            System.out.println("bad typecode");
        }
        return i;
    }

    private void setFlags(int operand1, int operand2) {
        if(operand1 == operand2) {
            registers[14] = 0;
        } else if (operand1 > operand2) {
            registers[14] = 1;
        } else if (operand1 < operand2) {
            registers[14] = 3;
        }
    }

    private Instruction execute(Instruction i) {
        i.result = 0;

        if (i.cond == 6 || i.cond == 5) {
            return i;
        } else if (i.type == 0) {//integer arithmetic
            if (i.opcode == 10) {//comparison format 1
                setFlags(registers[i.source1], registers[i.source2]);
            } else if (i.opcode == 11) {//comparison format 2
                setFlags(registers[i.source1], i.immediate);
            }
            if (i.opcode == 0) {
                i.result = registers[i.source1] + registers[i.source2];
            } else if (i.opcode == 1) {
                i.result = registers[i.source1] + i.immediate;
            } else if (i.opcode == 2) {
                i.result = registers[i.source1] - registers[i.source2];
            } else if (i.opcode == 3) {
                i.result = registers[i.source1] - i.immediate;
            } else if (i.opcode == 4) {
                i.result = registers[i.source1] * registers[i.source2];
            } else if (i.opcode == 5) {
                i.result = registers[i.source1] * i.immediate;
            } else if (i.opcode == 6) {
                i.result = registers[i.source1] / registers[i.source2];
            } else if (i.opcode == 7) {
                i.result = registers[i.source1] / i.immediate;
            } else if (i.opcode == 8) {
                i.result = registers[i.source1] % registers[i.source2];
            } else if (i.opcode == 9) {
                i.result = registers[i.source1] % i.immediate;
            }
        } else if (i.type == 1) {
            if (i.opcode == 8) {//comparison format 1
                setFlags(i.source1, i.source2);
            } else if (i.opcode == 9) {//comparison format 2
                setFlags(i.source1, i.immediate);
            }
            if (i.opcode == 0) {
                i.result = registers[i.source1] + registers[i.source2];
            } else if (i.opcode == 1) {
                i.result = registers[i.source1] + i.immediate;
            } else if (i.opcode == 2) {
                i.result = registers[i.source1] - registers[i.source2];
            } else if (i.opcode == 3) {
                i.result = registers[i.source1] - i.immediate;
            } else if (i.opcode == 4) {
                i.result = registers[i.source1] * registers[i.source2];
            } else if (i.opcode == 5) {
                i.result = registers[i.source1] * i.immediate;
            } else if (i.opcode == 6) {
                i.result = registers[i.source1] / registers[i.source2];
            } else if (i.opcode == 7) {
                i.result = registers[i.source1] / i.immediate;
            }
        } else if (i.type == 2) {
            if (i.opcode == 8) {//comparison format 1
                setFlags(i.source1, i.source2);
            } else if (i.opcode == 9) {//comparison format 2
                setFlags(i.source1, i.immediate);
            }
            if (i.opcode == 0) {
                i.result = registers[i.source1] & registers[i.source2];
            } else if (i.opcode == 1) {
                i.result = registers[i.source1] & i.immediate;
            } else if (i.opcode == 2) {
                i.result = registers[i.source1] | registers[i.source2];
            } else if (i.opcode == 3) {
                i.result = registers[i.source1] | i.immediate;
            } else if (i.opcode == 4) {
                i.result = ~registers[i.source1];
            } else if (i.opcode == 5) {
                i.result = ~registers[i.source1];
            } else if (i.opcode == 6) {
                i.result = registers[i.source1] << i.immediate;
            } else if (i.opcode == 7) {
                i.result = registers[i.source1] >>> i.immediate;
            } else if (i.opcode == 8) {
                i.result = registers[i.source1] << i.immediate;
            } else if (i.opcode == 9) {
                i.result = registers[i.source1] >> i.immediate;
            }
        } else if (i.type == 3) {
            if (i.opcode < 6) {
                i.result = registers[i.source1 + i.immediate];
            } else if (i.opcode < 8) {
                if (i.opcode == 6) {
                    i.result = registers[i.source1];
                } else if (i.opcode == 7) {
                    i.result = i.immediate;
                }
            }
        } else if (i.type == 4 || i.type == 5) {
            //do nothing
        } else {
            System.out.println("bad typecode");
        }
        return i;
    }

    private Instruction memory(Instruction i) {
        if (i.cond == 6 || i.cond == 5) {
            return i;
        } else if (i.type == 3) {
            if (i.opcode < 3) {
                Data read = memory.access(i.result, null, 3, true);
                if (read.done) {
                    if (memory.getLevel() == 1)
                        i.result = read.data[0];
                    else    
                        i.result = read.data[i.result % memory.getWords()];

                } else {
                    return stall();
                }
            } else if (i.opcode < 6) {
                int[] d;
                if (memory.getLevel() == 1) {
                    d = new int[1];
                    d[0] = registers[i.destination];
                }
                else {
                    d = memory.getNewLine(i.result);
                    
                    d[i.result%memory.getWords()] = registers[i.destination];
                    
                }

                Data write = memory.access(i.result, d, 3, false);
                if (!write.done) {  
                    return stall();
                }
            }
        }
        return i;
    }

    private Instruction writeback(Instruction i) {
        //write value out to register
        if (i.cond == 5) {
            return i;
        } else if (i.type == 0) {
            if (i.opcode >= 0 && i.opcode < 10) {
                if (i.cond != 6) {
                    registers[i.destination] = i.result;
                }
                pendingRegisters[i.destination] = false;
            }
            if (i.opcode % 2 == 0) {
                pendingRegisters[i.source2] = false;
            }
            pendingRegisters[i.source1] = false;
        } else if (i.type == 1) {
            if (i.opcode >= 0 && i.opcode < 8) {
                if (i.cond != 6) {
                    registers[i.destination] = i.result;
                }
                pendingRegisters[i.destination] = false;
            }
            if (i.opcode % 2 == 0) {
                pendingRegisters[i.source2] = false;
            }
            pendingRegisters[i.source1] = false;

        }  else if (i.type == 2) {
            pendingRegisters[i.destination] = false;

            if (i.cond != 6) {
                registers[i.destination] = i.result;
            }

            pendingRegisters[i.source1] = false;
            if (i.opcode < 4 && i.opcode % 2 == 0) {
                pendingRegisters[i.source2] = false;
            }
        } else if (i.type == 3) {
            if (i.opcode < 3 || i.opcode > 5 && i.cond != 6) {
                registers[i.destination] = i.result;
            }
            pendingRegisters[i.destination] = false;
            pendingRegisters[i.source1] = false;
        } else if (i.type == 4) {
            //check if we take this branch
            if (i.cond != 6 && i.cond == 7 || (i.cond == 0 && registers[14] == 0) || (i.cond == 1 && registers[14] == 1) || (i.cond == 2 && (registers[14] == 0 || registers[14] == 1)) || (i.cond == 3 && registers[14] == 3) || (i.cond == 4 && (registers[14] == 0 || registers[14] == 3))) {
                squashPipeline();
                if (i.opcode == 2) {
                    stack.push(registers[15]);
                    registers[15] = i.immediate;
                } else if (i.opcode == 3) {
                    registers[15] = stack.pop();
                } else {
                    registers[15] = i.immediate;
                } 
            }
            registers[14] = 7;
        }
        return i;
    }

    public boolean notEndOfProgram() {//checks if pipeline is filled with halt instructions or end of program file is reached
        return registers[15] < 64 && (inFlightInstructions[0].instruction != -201326592 || inFlightInstructions[1].instruction != -201326592 || inFlightInstructions[2].instruction != -201326592 || inFlightInstructions[3].instruction != -201326592);
    }

    public Instruction[] cycle() {
        Instruction[] readOut = new Instruction[5];
        readOut[4] = inFlightInstructions[3];

        writeback(inFlightInstructions[3]);

        inFlightInstructions[3] = memory(inFlightInstructions[2]);
        readOut[3] = inFlightInstructions[3];

        //check if memory stage generated a stall
        if (!(inFlightInstructions[3].cond == 5 && inFlightInstructions[2].cond != 5)) {
            inFlightInstructions[2] = execute(inFlightInstructions[1]);
            readOut[2] = inFlightInstructions[2];

            inFlightInstructions[1] = decode(inFlightInstructions[0]);
            readOut[1] = inFlightInstructions[1];

            if (inFlightInstructions[1].cond != 5) {
                inFlightInstructions[0] = fetch();
            }
            readOut[0] = inFlightInstructions[0];
        }

        return readOut;
    }

    public boolean pipeEmpty() {//returns true if branch is filled with stall instructions, used when cycling without the pipeline
        return inFlightInstructions[0].cond == 5 && inFlightInstructions[1].cond == 5 && inFlightInstructions[2].cond == 5 && inFlightInstructions[3].cond == 5;
    }

    public Instruction[] cycleNoPipeline() {
        Instruction[] readOut = new Instruction[5];
        readOut[4] = inFlightInstructions[3];

        writeback(inFlightInstructions[3]);

        inFlightInstructions[3] = memory(inFlightInstructions[2]);
        readOut[3] = inFlightInstructions[3];

        //check if memory stage generated a stall
        if (!(inFlightInstructions[3].cond == 5 && inFlightInstructions[2].cond != 5)) {
            inFlightInstructions[2] = execute(inFlightInstructions[1]);
            readOut[2] = inFlightInstructions[2];

            inFlightInstructions[1] = decode(inFlightInstructions[0]);
            readOut[1] = inFlightInstructions[1];

            if (pipeEmpty()) {
                inFlightInstructions[0] = fetch();
            } else {
                inFlightInstructions[0] = stall();
            }
            readOut[0] = inFlightInstructions[0];
        }

        return readOut;
    }
}
