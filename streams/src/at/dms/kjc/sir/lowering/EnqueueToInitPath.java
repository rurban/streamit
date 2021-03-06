package at.dms.kjc.sir.lowering;

import java.util.*;
import at.dms.kjc.*;
import at.dms.kjc.iterator.*;
import at.dms.kjc.sir.*;
import at.dms.compiler.JavaStyleComment;
import at.dms.compiler.TokenReference;

/**
 * Create initPath() functions for feedback loops that don't have them.
 * Looks for feedback loops that don't have any initPath() function.
 * If so, searches the init function for enqueue statements, and uses
 * those to build an appropriate initPath() function.  This pass must
 * be run after constant propagation and loop unrolling, and assumes
 * that enqueue statements are outside of any control flow.
 *
 * @author  David Maze &lt;dmaze@cag.lcs.mit.edu&gt;
 * @version $Id: EnqueueToInitPath.java,v 1.7 2007-02-23 19:42:08 dimock Exp $
 */
public class EnqueueToInitPath
{
    /**
     * Recursively search for and transform feedback loops.
     *
     * @param str  SIR stream to search
     */
    public static void doInitPath(SIRStream str)
    {
        SIRIterator iter = IterFactory.createFactory().createIter(str);
        iter.accept(new EmptyStreamVisitor() {
                public void postVisitFeedbackLoop(SIRFeedbackLoop self,
                                                  SIRFeedbackLoopIter iter)
                {
                    doRewrite(self);
                }
            });
    }

    /**
     * Do the initPath transformation for a single feedback loop.
     *
     * @param self  SIR feedback loop to rewrite
     */
    public static void doRewrite(SIRFeedbackLoop self)
    {
        // First off, look for an initPath function.
        if (self.getInitPath() != null)
            return;
        
        // Okay, none of that.  Find the init function,
        JMethodDeclaration init = self.getInit();
        // and walk its code looking for enqueue statements.
        final List<JExpression> values = new java.util.ArrayList<JExpression>();
        final String[] enqTypeWrapper = new String[1];
        init.getBody().accept(new SLIREmptyVisitor() {
                public void visitBlockStatement(JBlock self,
                                                JavaStyleComment[] comments) {
                    super.visitBlockStatement(self, comments);
                    for (Iterator iter = self.getStatementIterator(); iter.hasNext(); )
                        {
                            JStatement stmt = (JStatement)iter.next();
                            // What we're looking for is function calls that
                            // begin with enqueue.  So:
                            if (!(stmt instanceof JExpressionStatement))
                                continue;
                            JExpression expr = ((JExpressionStatement)stmt).getExpression();
                            if (!(expr instanceof JMethodCallExpression))
                                continue;
                            JMethodCallExpression mce = (JMethodCallExpression)expr;
                            if (!(mce.getIdent().startsWith("enqueue")))
                                continue;
                            // Okay, we win.  We want to remove this statement
                            iter.remove();
                            // and save the value and type.
                            values.add(mce.getArgs()[0]);
                            enqTypeWrapper[0] = mce.getIdent().substring(7); // len("enqueue")
                        }
                }});

        if (values.size() == 0) {
            // it is possible that there were no enqueue statements:
            // enqueuing can also be handled by a prework function in the
            // loop -- which is more flexible: wider range of types, more values...
            return;
        }

        String enqType = enqTypeWrapper[0];

        // Now build the new function.
        List stmts = new java.util.ArrayList();
        JFormalParameter params[] = new JFormalParameter[1];
        params[0] = new JFormalParameter(null, // token reference
                                         JLocalVariable.DES_PARAMETER,
                                         CStdType.Integer,
                                         "i", // name
                                         true); // final
        for (int i = 0; i < values.size(); i++)
            {
                // Assemble statement:
                // "if (i == [i]) return [values[i]];"
                JExpression value = values.get(i);
                TokenReference where = value.getTokenReference();
                JStatement rtn = new JReturnStatement(where, value, null);
                JExpression cond =
                    new JEqualityExpression(where,
                                            true, // ==
                                            new JLocalVariableExpression(where, params[0]),
                                            new JIntLiteral(where, i));
                JStatement stmt = new JIfStatement(where, cond, rtn, null, null);
                stmts.add(stmt);
            }
        
        // Figure out both the type and default case from the
        // name of the enqueue function.
        CType rtnType;
        if (enqType.equals("Int"))
            {
                rtnType = CStdType.Integer;
                stmts.add(new JReturnStatement(null, new JIntLiteral(0), null));
            }
        else if (enqType.equals("Float"))
            {
                rtnType = CStdType.Float;
                stmts.add(new JReturnStatement(null, new JFloatLiteral(0.0f), null));
            }
        else if (enqType.equals("Double"))
            {
                rtnType = CStdType.Double;
                stmts.add(new JReturnStatement(null, new JDoubleLiteral(null, 0.0), null));
            }
        else
            {
                rtnType = CStdType.Object;
                stmts.add(new JReturnStatement(null, new JNullLiteral(null), null));
            }
        
        JBlock body = new JBlock(null, stmts, null);
        JMethodDeclaration decl =
            new JMethodDeclaration(null, // token reference
                                   0, // modifiers
                                   rtnType,
                                   "initPath" + enqType, // ident
                                   params,
                                   CClassType.EMPTY, // exceptions,
                                   body,
                                   null, // javadoc
                                   null); // comments
        self.setInitPath(decl);

        // We also know the delay rate for the method (it's the number
        // of values); set that.
        self.setDelay(new JIntLiteral(values.size()));
    }
}
