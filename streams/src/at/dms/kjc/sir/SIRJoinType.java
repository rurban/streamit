package at.dms.kjc.sir;

import at.dms.kjc.*;
import at.dms.util.Utils;
import java.io.Serializable;

import streamit.scheduler1.SchedJoinType;

/**
 * This class enumerates the types of joiners.
 */
public class SIRJoinType implements Serializable {
    /**
     * A combining splitter.
     */
    public static final SIRJoinType COMBINE 
	= new SIRJoinType("COMBINE");
    /**
     * An equal-weight round robing splitter.
     */
    public static final SIRJoinType ROUND_ROBIN 
	= new SIRJoinType("ROUND_ROBIN");
    /**
     * A round robin splitter with individual weights for each tape.
     */
    public static final SIRJoinType WEIGHTED_RR 
	= new SIRJoinType("WEIGHTED_ROUND_ROBIN");
    /**
     * A null splitter, providing no tokens on its output.
     */
    public static final SIRJoinType NULL 
	= new SIRJoinType("NULL_SJ");
    /**
     * The name of this type.
     */
    private String name;

    /**
     * Constructs a join type with name <name>.
     */
    private SIRJoinType(String name) {
	this.name = name;
    }

    private Object readResolve() throws Exception {
	if (this.name.equals("COMBINE"))
	    return this.COMBINE;
	if (this.name.equals("ROUND_ROBIN"))
	    return this.ROUND_ROBIN;
	if (this.name.equals("WEIGHTED_ROUND_ROBIN"))
	    return this.WEIGHTED_RR;
	if (this.name.equals("NULL_SJ"))
	    return this.NULL;
	else 
	    throw new Exception();
    }

    public String toString() {
	return name;
    }

    /**
     * Returns an <int> that represents this type to the library/scheduler.
     */
    public int toSchedType() {
	if (this==ROUND_ROBIN) {
	    // there is no scheduler round_robin type
	    return SchedJoinType.WEIGHTED_ROUND_ROBIN;
	} else if (this==WEIGHTED_RR) {
	    return SchedJoinType.WEIGHTED_ROUND_ROBIN;
	} else if (this==NULL) {
	    return SchedJoinType.NULL;
	} else {
	    Utils.fail("Type of joiner \"" + this + 
		       "\"unsupported in library?");
	    return -1;
	}
    }
}
