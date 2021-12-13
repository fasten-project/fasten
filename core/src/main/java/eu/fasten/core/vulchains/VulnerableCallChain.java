package eu.fasten.core.vulchains;

import eu.fasten.core.data.FastenURI;
import eu.fasten.core.data.vulnerability.Vulnerability;
import java.util.List;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

public class VulnerableCallChain {
    List<Vulnerability> vulnerabilities;
    List<FastenURI> chain;

    public VulnerableCallChain(List<Vulnerability> vulnerabilities, List<FastenURI> chain) {
        this.vulnerabilities = vulnerabilities;
        this.chain = chain;
    }


    @Override
    public boolean equals(Object o) {
        return EqualsBuilder.reflectionEquals(this, o);
    }

    @Override
    public int hashCode() {
        return HashCodeBuilder.reflectionHashCode(this);
    }

    @Override
    public String toString() {
        return ToStringBuilder.reflectionToString(this, ToStringStyle.MULTI_LINE_STYLE);
    }
}
