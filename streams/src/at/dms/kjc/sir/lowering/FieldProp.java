package at.dms.kjc.sir.lowering;

import at.dms.kjc.*;
import at.dms.kjc.sir.*;

import java.util.*;

/**
 * This class propagates constant assignments to field variables from
 * the init function into other functions.
 * $Id: FieldProp.java,v 1.14 2002-06-19 23:33:11 jasperln Exp $
 */
public class FieldProp implements Constants
{
    /** Maps field names to CTypes. */
    private HashMap types;
    /** Maps field names to JExpression values. */
    private HashMap fields;
    /** List of field names that can't be propagated. */
    private HashSet nofields;
    /** Maps field names to JExpression arrays.  If a particular array
        element is null, that field isn't a constant (or hasn't been
        determined yet). */
    private HashMap arrays;
    /** Maps field names to boolean arrays to track array elements that
        can't be propagated.  If the array element is true, that element
        can't be propagated; if it's false, it can. */
    private HashMap noarrays;
    
    private FieldProp()
    {
        types = new HashMap();
        fields = new HashMap();
        nofields = new HashSet();
        arrays = new HashMap();
        noarrays = new HashMap();
    }
    
    /**
     * Performs a depth-first traversal of an SIRStream tree, and
     * calls propagate() on any SIRSplitJoins as a post-pass.
     */
    public static SIRStream doPropagate(SIRStream str)
    {
        // First, visit children (if any).
        if (str instanceof SIRFeedbackLoop)
        {
            SIRFeedbackLoop fl = (SIRFeedbackLoop)str;
            doPropagate(fl.getBody());
            doPropagate(fl.getLoop());
        }
        if (str instanceof SIRPipeline)
        {
            SIRPipeline pl = (SIRPipeline)str;
            Iterator iter = pl.getChildren().iterator();
            while (iter.hasNext())
            {
                SIRStream child = (SIRStream)iter.next();
                doPropagate(child);
            }
        }
        if (str instanceof SIRSplitJoin)
        {
            SIRSplitJoin sj = (SIRSplitJoin)str;
            Iterator iter = sj.getParallelStreams().iterator();
            while (iter.hasNext())
            {
                SIRStream child = (SIRStream)iter.next();
                doPropagate(child);
            }
        }
        
        // Having recursed, do the flattening, if it's appropriate.
        if (str instanceof SIRFilter)
        {
            // Run propagate twice, just to be sure.
            new FieldProp().propagate((SIRFilter)str);
            new FieldProp().propagate((SIRFilter)str);
            // Run the unroller...
            Unroller unroller = new Unroller(new Hashtable());
            for (int i = 0; i < str.getMethods().length; i++) {
                str.getMethods()[i].accept(unroller);
	    }
            // Then try to propagatate again.
            new FieldProp().propagate((SIRFilter)str);
        }
        
        // All done, return the object.
        return str;
    }
    
    /**
     * Does the actual work on <filter>.
     */
    private void propagate(SIRFilter filter)
    {
        findCandidates(filter);

	System.out.println("--------------------");
	System.out.println("Candidates Fields : ");
	Iterator keyIter = this.types.keySet().iterator();
	while(keyIter.hasNext()) {
	  Object f = keyIter.next();
	  System.out.println("Field: " + f +
			     "  " + this.types.get(f) +
			     " --> " + this.fields.get(f));

	  
	}

	doPropagation(filter);
    }

    /** Helper function to determine if a field has been invalidated. */
    private boolean isFieldInvalidated(String name)
    {
        return nofields.contains(name);
    }

    /** Helper function to determine if an array slot has been invalidated. */
    private boolean isArrayInvalidated(String name, int slot)
    {
        if (isFieldInvalidated(name)) return true;
        boolean[] bary = (boolean[])noarrays.get(name);
        if (bary == null)
            return false;
        return bary[slot];
    }

    /** Helper function to invalidate a particular field. */
    private void invalidateField(String name)
    {
      System.out.println("Invalidating field: " + name);
      nofields.add(name);
      fields.remove(name);
    }

    /** Helper function to invalidate a particular array slot.
        This requires that the array have already been noticed. */
    private void invalidateArray(String name, int slot)
    {
        // Stop early if the field has already been invalidated.
        if (isFieldInvalidated(name)) return;
        boolean[] bary = (boolean[])noarrays.get(name);
        JExpression[] exprs = (JExpression[])arrays.get(name);
        if (bary == null)
        {
            bary = new boolean[exprs.length];
            noarrays.put(name, bary);
        }
        bary[slot] = true;
        exprs[slot] = null;
    }

    /** Helper function to invalidate whatever a particular expression
        points to.  No effect if the expression isn't a field-access
        or array-access expression. */
    private void invalidateExpression(JExpression expr)
    {
        if (expr instanceof JFieldAccessExpression)
        {
            JFieldAccessExpression fae = 
                (JFieldAccessExpression)expr;
            String name = fae.getIdent();
            if (!(fae.getPrefix() instanceof JThisExpression))
                return;
            invalidateField(name);
        }
    }

    /** Helper function to determine if a field can be propagated. */
    private boolean canFieldPropagate(String name)
    {
        if (isFieldInvalidated(name)) return false;
        if (!fields.containsKey(name)) return false;
        return true;
    }

    /** Helper function to determine if an array slot can be propagated. */
    private boolean canArrayPropagate(String name, int slot)
    {
        if (isFieldInvalidated(name)) return false;
        if (isArrayInvalidated(name, slot)) return false;
        JExpression[] exprs = (JExpression[])arrays.get(name);
        if (exprs == null) return false;
        if (exprs[slot] == null) return false;
        return true;
    }   

    /** Helper function returning the constant value of a field. */
    private JExpression propagatedField(String name)
    {
        return (JExpression)fields.get(name);
    }
    
    /** Helper function returning the constant value of an array slot. */
    private JExpression propagatedArray(String name, int slot)
    {
        JExpression[] exprs = (JExpression[])arrays.get(name);
        return exprs[slot];
    }

    /** Force a literal JExpression type to a particular other type. */
    private JExpression forceLiteralType(JExpression expr, CType type)
    {
        switch(type.getTypeID())
        {
        case TID_FLOAT:
            if (expr instanceof JDoubleLiteral)
                return new JFloatLiteral(expr.getTokenReference(),
                                         (float)expr.doubleValue());
            return expr;
        }
        return expr;
    }

    /** Notice that a field variable has a constant assignment. */
    private void noticeFieldAssignment(String name, JExpression value)
    {
        // If the field has already been invalidated, stop now.
        if (isFieldInvalidated(name)) return;
        // If the field has a value, invalidate it.
        if (canFieldPropagate(name))
        {
            invalidateField(name);
            return;
        }
        // Otherwise, add the name/value pair to the hash table.
        value = forceLiteralType(value, (CType)types.get(name));
        fields.put(name, value);
    }

    /** Notice that an array slot has a constant assignment. */
    private void noticeArrayAssignment(String name, int slot,
                                       JExpression value)
    {
        // Same as before...
        if (isArrayInvalidated(name, slot)) return;
        if (canArrayPropagate(name, slot))
        {
            invalidateArray(name, slot);
            return;
        }
        // Okay, populate the array slot.  The expression array
        // needs to already exist.
        CType atype = (CType)types.get(name);
        value = forceLiteralType(value, ((CArrayType)atype).getBaseType());
        JExpression[] exprs = (JExpression[])arrays.get(name);
        exprs[slot] = value;
    }

    /** Notice that an array exists with a fixed size. */
    private void noticeArrayCreation(String name, int size)
    {
        // Punt if the field has been invalidated, or if the array
        // entry already exists.
        if (isFieldInvalidated(name)) return;
        if (arrays.containsKey(name)) return;
        JExpression[] exprs = new JExpression[size];
        arrays.put(name, exprs);
    }

    /** Look for candidate fields in a filter. */
    private void findCandidates(SIRFilter filter)
    {
        JFieldDeclaration[] fields = filter.getFields();
        for (int i = 0; i < fields.length; i++)
        {
            fields[i].accept(new SLIREmptyVisitor() {
                    public void visitFieldDeclaration(JFieldDeclaration self,
                                                      int modifiers,
                                                      CType type,
                                                      String ident,
                                                      JExpression expr)
                    {
		      // add entry to name->type mapping
		      types.put(ident, type);
                    }
                });
        }

        JMethodDeclaration[] meths = filter.getMethods();
        for (int i = 0; i < meths.length; i++)
        {
            meths[i].accept(new SLIREmptyVisitor() {
                    public void visitAssignmentExpression
                        (JAssignmentExpression self,
                         JExpression left,
                         JExpression right)
                    {
                        super.visitAssignmentExpression(self, left, right);
                        if (left instanceof JFieldAccessExpression)
                        {
                            JFieldAccessExpression fae =
                                (JFieldAccessExpression)left;
                            String name = fae.getIdent();
                            // Look inside of fae; the left-hand side should
                            // be this.
                            if (!(fae.getPrefix() instanceof JThisExpression))
                                return;
                            // Okay; what's the right hand side?  Notice
                            // if it's a literal or (new type[]); invalidate
                            // otherwise.
                            if (right instanceof JLiteral)
                                noticeFieldAssignment(name, right);
                            else if (right instanceof JNewArrayExpression)
                            {
                                JNewArrayExpression nae =
                                    (JNewArrayExpression)right;
                                JExpression[] dims = nae.getDims();
                                if (dims.length == 1 &&
                                    dims[0] instanceof JIntLiteral)
                                {
                                    noticeArrayCreation
                                        (name,
                                         ((JIntLiteral)dims[0]).intValue());
                                }
                                else
                                    invalidateField(name);
                            }
                            else
                                invalidateField(name);
                        }
                        else if (left instanceof JArrayAccessExpression)
                        {
                            JArrayAccessExpression aae =
                                (JArrayAccessExpression)left;
                            // Check that the prefix is a FieldAccessExpr.
                            // Same rules as above.
                            JExpression prefix = aae.getPrefix();
                            if (!(prefix instanceof JFieldAccessExpression))
                                return;
                            JFieldAccessExpression fae =
                                (JFieldAccessExpression)prefix;
                            String name = fae.getIdent();
                            if (!(fae.getPrefix() instanceof JThisExpression))
                                return;
                            // Also check that the offset is constant.
                            // If it's not, invalidate the whole thing.
                            JExpression accessor = aae.getAccessor();
                            if (!(accessor instanceof JIntLiteral))
                            {
                                invalidateField(name);
                                return;
                            }
                            int slot = ((JIntLiteral)accessor).intValue();
                            // Now look at the right-hand side.
                            if (right instanceof JLiteral)
                                noticeArrayAssignment(name, slot, right);
                            else
                                invalidateArray(name, slot);
                        }
                    }
                    public void visitCompoundAssignmentExpression
                        (JCompoundAssignmentExpression self,
                         int oper,
                         JExpression left,
                         JExpression right)
                    {
                        super.visitCompoundAssignmentExpression
                            (self, oper, left, right);
                        // Instant death.
                        invalidateExpression(left);
                    }
                    public void visitPrefixExpression
                        (JPrefixExpression self,
                         int oper,
                         JExpression expr)
                    {
                        super.visitPrefixExpression(self, oper, expr);
                        // Again, instant death.
                        invalidateExpression(expr);
                    }
                    public void visitPostfixExpression
                        (JPostfixExpression self,
                         int oper,
                         JExpression expr)
                    {
                        super.visitPostfixExpression(self, oper, expr);
                        // Again, instant death.
                        invalidateExpression(expr);
                    }
                });
        }
    }

    /** Replace previously-notice candidate fields. */
    private void doPropagation(SIRFilter filter)
    {
        JMethodDeclaration[] meths = filter.getMethods();
        for (int i = 0; i < meths.length; i++)
        {
            meths[i].accept(new SLIRReplacingVisitor() {
                    public Object visitAssignmentExpression
                        (JAssignmentExpression self,
                         JExpression left,
                         JExpression right)
                    {
                        // Don't visit the left-hand side of the
                        // expression.
                        return new JAssignmentExpression
                            (self.getTokenReference(),
                             left,
                             (JExpression)right.accept(this));
                    }
                    
                    public Object visitFieldExpression
                        (JFieldAccessExpression self,
                         JExpression left,
                         String ident)
                    {
                        Object orig =
                            super.visitFieldExpression(self, left, ident);
                        if (canFieldPropagate(ident))
                            return propagatedField(ident);
                        else
                            return orig;
                    }

                    public Object visitArrayAccessExpression
                        (JArrayAccessExpression self,
                         JExpression pfx,
                         JExpression acc)
                    {
                        // Recurse so we have something to return.
                        Object orig =
                            super.visitArrayAccessExpression(self, pfx, acc);
                        // Take a harder look at what we have...
                        if (!(pfx instanceof JFieldAccessExpression))
                            return orig;
                        JFieldAccessExpression fae =
                            (JFieldAccessExpression)pfx;
                        if (!(fae.getPrefix() instanceof JThisExpression))
                            return orig;
                        // Okay, the base is a FAE with this.  Yay.
                        // Save its name.
                        String name = fae.getIdent();
                        // Now, is the offset an integer literal?
                        if (!(acc instanceof JIntLiteral))
                            return orig;
                        // Yay, we win (hopefully).
                        int slot = ((JIntLiteral)acc).intValue();
                        if (canArrayPropagate(name, slot))
                            return propagatedArray(name, slot);
                        else
                            return orig;
                    }
                });
            // Also run some simple algebraic simplification now.
            meths[i].accept(new Propagator(findLocals(meths[i])));
	    // Raise Variable Declarations to beginning of block
	    //meths[i].accept(new VarDeclRaiser());
        }
    }

    private Hashtable findLocals(JMethodDeclaration meth)
    {
        final Hashtable yes = new Hashtable();
        
        // This looks a lot like the thing we had for fields, except
        // that it looks for local variables.
        meth.accept(new SLIREmptyVisitor() {
                private HashSet no = new HashSet();
                
                public void visitAssignmentExpression
                    (JAssignmentExpression self,
                     JExpression left,
                     JExpression right)
                {
                        super.visitAssignmentExpression(self, left, right);
                        if (left instanceof JLocalVariableExpression)
                        {
                            JLocalVariableExpression lve =
                                (JLocalVariableExpression)left;
                            JLocalVariable lv = lve.getVariable();
                            if (no.contains(lv)) return;
                            if (yes.containsKey(lv))
                            {
                                yes.remove(lv);
                                no.add(lv);
                                return;
                            }
                            if (right instanceof JLiteral)
                                yes.put(lv, right);
                            else
                                no.add(lv);
                        }
                }
                public void visitCompoundAssignmentExpression
                    (JCompoundAssignmentExpression self,
                     int oper,
                     JExpression left,
                     JExpression right)
                {
                    super.visitCompoundAssignmentExpression
                        (self, oper, left, right);
                    // Instant death.
                    if (left instanceof JLocalVariableExpression)
                    {
                        JLocalVariable lv =
                            ((JLocalVariableExpression)left).getVariable();
                        yes.remove(lv);
                        no.add(lv);
                    }
                }
                public void visitPrefixExpression
                    (JPrefixExpression self,
                     int oper,
                     JExpression expr)
                {
                    super.visitPrefixExpression(self, oper, expr);
                    // Again, instant death.
                    if (expr instanceof JLocalVariableExpression)
                    {
                        JLocalVariable lv =
                            ((JLocalVariableExpression)expr).getVariable();
                        yes.remove(lv);
                        no.add(lv);
                    }
                }
                public void visitPostfixExpression
                    (JPostfixExpression self,
                     int oper,
                     JExpression expr)
                {
                    super.visitPostfixExpression(self, oper, expr);
                    // Again, instant death.
                    if (expr instanceof JLocalVariableExpression)
                    {
                        JLocalVariable lv =
                            ((JLocalVariableExpression)expr).getVariable();
                        yes.remove(lv);
                        no.add(lv);
                    }
                }
            });
        return yes;
    }









  /**
   * Converts joint field definition/assignment statements to a field decl
   * and a corresponding field assignment statement in the init function. Eg
   * <br>
   * <pre>
   * int i = 5;
   * </pre>
   * into <br>
   * <pre>
   * int i;
   * i = 5;
   * </pre>
   **/
  public static void moveStreamInitialAssignments(SIRStream str) {
    //System.out.println("Type of the str passed to stream initial assignments: " +
    //	       str.getClass().getName());
    // First, visit children (if any).
        if (str instanceof SIRFeedbackLoop)
        {
            SIRFeedbackLoop fl = (SIRFeedbackLoop)str;
            moveStreamInitialAssignments(fl.getBody());
            moveStreamInitialAssignments(fl.getLoop());
        }
        if (str instanceof SIRPipeline)
        {
            SIRPipeline pl = (SIRPipeline)str;
            Iterator iter = pl.getChildren().iterator();
            while (iter.hasNext())
            {
                SIRStream child = (SIRStream)iter.next();
                moveStreamInitialAssignments(child);
            }
        }
        if (str instanceof SIRSplitJoin)
        {
            SIRSplitJoin sj = (SIRSplitJoin)str;
            Iterator iter = sj.getParallelStreams().iterator();
            while (iter.hasNext())
            {
                SIRStream child = (SIRStream)iter.next();
                moveStreamInitialAssignments(child);
            }
        }
        
        // Having recursed, move initial assignments for this filter 
        if (str instanceof SIRFilter)
	{
	  SIRFilter sf = (SIRFilter) str;
	  moveFilterInitialAssignments(sf);
        }
        
        // All done
        return;
  }

  
  public static void moveFilterInitialAssignments(SIRFilter filter) {
      // assignment statements that need to be added to the init function 
    final Vector assignmentStatements = new Vector();
    
    for (int i=0; i<filter.getFields().length; i++) {
      filter.getFields()[i].accept(new SLIRReplacingVisitor() {

	  public Object visitFieldDeclaration(JFieldDeclaration self,
				       int modifiers,
				       CType type,
				       String ident,
				       JExpression expr)
	  {
	    // if this field declaration has an initial value,
	    // make an assignment expression to stick in the
	    // init function
	    //System.out.println("Initial expression for field: " + expr);
	    if (expr != null) {
	      // build up the this.field = initalValue expression


	      JThisExpression thisExpr = new JThisExpression (self.getTokenReference());
	      JFieldAccessExpression fieldExpr;
	      fieldExpr = new JFieldAccessExpression(self.getTokenReference(),
						     thisExpr,
						     ident);
	      
	      JAssignmentExpression assignExpr;
	      assignExpr = new JAssignmentExpression(self.getTokenReference(),
						     fieldExpr,
						     expr);

	      JExpressionStatement assignStmt;
	      assignStmt = new JExpressionStatement(self.getTokenReference(),
						    assignExpr,
						    null);
	      // add the new statement to the list of statements we are going to
	      // add to the init function
	      assignmentStatements.add(assignStmt);

	      // mutate the field so that it has no initializer expression
	      self.getVariable().setExpression(null);
	    }

	    
	      return self;
	  }	    
	}); // end crazy anonymous class
    } // end for loop
    
    // now, we have to add the initializing assignment statements
    // to the beginning of the init function.
    if(filter.getInit()!=null)
	filter.getInit().getBody().addAllStatements(0, assignmentStatements);
    
  }
  

}
