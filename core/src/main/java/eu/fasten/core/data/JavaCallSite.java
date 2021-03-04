package eu.fasten.core.data;

import eu.fasten.core.data.metadatadb.codegen.enums.ReceiverType;

public class JavaCallSite {

    private String receiverNamespace;

    private ReceiverType callType;

    public JavaCallSite(String receiverNamespace, ReceiverType callType) {
        this.receiverNamespace = receiverNamespace;
        this.callType = callType;
    }

    public String getReceiverNamespace() {
        return receiverNamespace;
    }

    public ReceiverType getCallType() {
        return callType;
    }

    public static ReceiverType getReceiverType(String type) {
        switch (type) {
            case "static":
                return ReceiverType.static_;
            case "dynamic":
                return ReceiverType.dynamic;
            case "virtual":
                return ReceiverType.virtual;
            case "interface":
                return ReceiverType.interface_;
            case "special":
                return ReceiverType.special;
            default:
                throw new IllegalArgumentException("Unknown call type: " + type);
        }
    }
}
