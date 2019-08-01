package eu.fasten.analyzer.data.callgraph;

import com.ibm.wala.classLoader.IMethod;
import com.ibm.wala.shrikeBT.IInvokeInstruction;

import java.io.Serializable;

public final class ResolvedCall implements Serializable {

    public final IMethod target;
    public final IMethod source;
    public final IInvokeInstruction.Dispatch invoke;


    public ResolvedCall(IMethod source, IInvokeInstruction.IDispatch invoke, IMethod target) {
        this.source = source;
        this.target = target;
        this.invoke = (IInvokeInstruction.Dispatch) invoke;
    }
}
