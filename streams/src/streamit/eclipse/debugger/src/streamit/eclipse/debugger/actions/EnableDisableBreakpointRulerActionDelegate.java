package streamit.eclipse.debugger.actions;

import org.eclipse.jface.action.IAction;
import org.eclipse.jface.text.source.IVerticalRulerInfo;
import org.eclipse.ui.texteditor.AbstractRulerActionDelegate;
import org.eclipse.ui.texteditor.ITextEditor;

/**
 * @author kkuo
 */ 
public class EnableDisableBreakpointRulerActionDelegate extends AbstractRulerActionDelegate {
    
    /**
     * @see AbstractRulerActionDelegate#createAction(ITextEditor, IVerticalRulerInfo)
     */
    protected IAction createAction(ITextEditor editor, IVerticalRulerInfo rulerInfo) {
        return new EnableDisableBreakpointRulerAction(editor, rulerInfo);
    }
}
