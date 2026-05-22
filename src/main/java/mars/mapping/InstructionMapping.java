package mars.mapping;

import java.util.ArrayList;
import java.util.List;

/*
 * InstructionMapping — associates one C source line with a list of MIPS instruction addresses.
 *
 * Author: MARS C Extension
 */
public class InstructionMapping {

    private final int         cSourceLine;    // 1-based C source line number
    private final List<Integer> mipsAddresses; // list of MIPS word addresses

    public InstructionMapping(int cSourceLine) {
        this.cSourceLine   = cSourceLine;
        this.mipsAddresses = new ArrayList<Integer>();
    }

    public void addMipsAddress(int address) {
        mipsAddresses.add(address);
    }

    public int         getCSourceLine()   { return cSourceLine; }
    public List<Integer> getMipsAddresses() { return mipsAddresses; }

    @Override
    public String toString() {
        return "C line " + cSourceLine + " → MIPS " + mipsAddresses;
    }
}
