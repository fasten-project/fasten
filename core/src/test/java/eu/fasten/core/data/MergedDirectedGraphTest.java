package eu.fasten.core.data;

import it.unimi.dsi.fastutil.longs.LongList;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MergedDirectedGraphTest {

    MergedDirectedGraph dg;

    @BeforeEach
    void setUp() {
        dg = new MergedDirectedGraph();
        dg.addVertex(1L);
        dg.addVertex(2L);
        dg.addVertex(3L);
        dg.addVertex(4L);
        dg.addVertex(5L);
        dg.addEdge(1L, 2L);
        dg.addEdge(1L, 3L);
        dg.addEdge(2L, 3L);
        dg.addEdge(2L, 4L);
        dg.addEdge(3L, 4L);
        dg.addEdge(4L, 1L);
        dg.addEdge(4L, 5L);
    }

    @Test
    void successorsTest() {
        assertEquals(LongList.of(2L, 3L), dg.successors(1L));
        assertEquals(LongList.of(3L, 4L), dg.successors(2L));
        assertEquals(LongList.of(4L), dg.successors(3L));
        assertEquals(LongList.of(1L, 5L), dg.successors(4L));
        assertEquals(LongList.of(), dg.successors(5L));
    }

    @Test
    void predecessors() {
        assertEquals(LongList.of(4L), dg.predecessors(1L));
        assertEquals(LongList.of(1L), dg.predecessors(2L));
        assertEquals(LongList.of(1L, 2L), dg.predecessors(3L));
        assertEquals(LongList.of(2L, 3L), dg.predecessors(4L));
        assertEquals(LongList.of(4L), dg.predecessors(5L));
    }
}