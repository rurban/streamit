package at.dms.kjc;

import at.dms.kjc.sir.*;
import at.dms.kjc.sir.lowering.*;
import at.dms.kjc.lir.*;
import at.dms.kjc.raw.*;

/**
 * This provides the toplevel interface for StreaMIT.
 */
public class StreaMITMain {
    
    /**
     * Prints out C code for the program being compiled.
     */
    public static void compile(JCompilationUnit[] app) {
	
	//using the raw backend to generate uniprocessor code
	//so set the number of tiles to 1 and 
	//turn partitioning on...
	if (KjcOptions.raw_uni) {
	    KjcOptions.raw = 1;
	    KjcOptions.partition = true;
	}

	if (KjcOptions.altcodegen &&
	    KjcOptions.decoupled)
	    at.dms.util.Utils.fail("The options altcodegen and decoupled are mutually exclusive.");
	    
	if (KjcOptions.magic_net &&
	    KjcOptions.decoupled)
	    at.dms.util.Utils.fail("The options magic_net and decoupled are mutually exclusive.");
	
	System.out.println("/*");
	Kopi2SIR k2s = new Kopi2SIR(app);
	SIRStream stream = null;
	for (int i = 0; i < app.length; i++) {
	    SIRStream top = (SIRStream)app[i].accept(k2s);
	    if (top != null)
		stream = top;
	}

	SemanticChecker.doCheck(stream);
	
	if (KjcOptions.raw != -1) {
	    System.out.println("*/");
	    
	    /* Compiling for raw */
	    RawBackend.run(stream, 
			   k2s.getInterfaces(),
			   k2s.getInterfaceTables());
	    
	}
	else {
	    /* Compiling for uniprocessor */
	    Flattener.flatten(stream, 
			      k2s.getInterfaces(),
			      k2s.getInterfaceTables(),
			      k2s.getStructures());
	}
    }
}
