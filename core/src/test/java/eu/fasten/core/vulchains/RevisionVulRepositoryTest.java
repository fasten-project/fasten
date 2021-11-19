package eu.fasten.core.vulchains;

import eu.fasten.core.maven.data.Revision;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

class RevisionVulRepositoryTest {

    @Test
    void readVulChains() {
        var repo = new RevisionVulRepository("/Users/mehdi/Desktop/MyMac/TUD/FASTEN/Repositories" +
            "/MainRepo/fasten/core/src/test/resources", new Revision("g","a","v",
            new Timestamp(1)));
        repo.readVulChains();

        assertEquals( new HashSet<List<Long>>(){{
            add(new ArrayList<>(Arrays.asList(1L, 2L, 3L, 4L, 5L, 6L)));
            add(new ArrayList<>(Arrays.asList(7L, 8L, 9L, 10L)));
        }}, repo.getVulChains());
    }
}