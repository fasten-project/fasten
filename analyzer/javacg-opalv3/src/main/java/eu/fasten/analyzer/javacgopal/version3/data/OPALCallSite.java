package eu.fasten.analyzer.javacgopal.version3.data;

import org.apache.commons.lang3.StringUtils;
import org.json.JSONObject;

public class OPALCallSite {

    final private Integer line;
    final private String type;
    final private String receiver;

    /**
     * Constructs a Call Site from a line, type, and receiver parameters.
     *
     * @param line     lines number
     * @param type     type
     * @param receiver receiver
     */
    public OPALCallSite(Integer line, String type, String receiver) {
        this.line = line;
        this.type = type;
        this.receiver = receiver;
    }

    /**
     * Constructs a Call Site from a String containing line, type, and receiver information.
     *
     * @param callSite String to construct CallSite from
     */
    public OPALCallSite(String callSite) {
        this.line = Integer.valueOf(StringUtils.substringBetween(callSite, "line=", ", type='"));
        this.type = StringUtils.substringBetween(callSite, ", type='", "', receiver='");
        this.receiver = StringUtils.substringBetween(callSite, ", receiver='", "'}");
    }

    /**
     * Constructs a Call Site from a JSONObject containing line, type, and receiver information.
     *
     * @param callSite JSONObject to construct CallSite from
     */
    public OPALCallSite(JSONObject callSite) {
        this.line = callSite.getInt("line");
        this.type = callSite.getString("type");
        this.receiver = callSite.getString("receiver");
    }

    public Integer getLine() {
        return line;
    }

    public String getType() {
        return type;
    }

    public String getReceiver() {
        return receiver;
    }

    /**
     * Checks if any of passed types are of the same type as this CallSite.
     *
     * @param types types to check against
     * @return true if any of types matched this CallSite type, else false
     */
    public boolean is(String... types) {
        for (final var type : types) {
            if (this.type.equals(type)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public String toString() {
        return "{" +
                "line=" + line +
                ", type='" + type + '\'' +
                ", receiver='" + receiver + '\'' +
                '}';
    }
}
