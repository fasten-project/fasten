package eu.fasten.core.search;

import static org.junit.Assert.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Arrays;
import java.util.Random;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.Test;

import eu.fasten.core.data.ArrayImmutableDirectedGraph;
import eu.fasten.core.search.SearchEngine.Result;
import eu.fasten.core.search.TopKProcessor.Update;
import it.unimi.dsi.bits.Fast;
import it.unimi.dsi.fastutil.longs.Long2DoubleOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongList;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectRBTreeSet;

public class SearchEngineTest {


    @Test
    void testBfs() {
		final ArrayImmutableDirectedGraph.Builder builder = new ArrayImmutableDirectedGraph.Builder();
		for (int x = 0; x < 5; x++) builder.addInternalNode(x);
		builder.addArc(0, 1);
		builder.addArc(1, 2);
		builder.addArc(1, 3);
		builder.addArc(2, 4);
		builder.addArc(3, 4);
		builder.addArc(4, 1);
		final ArrayImmutableDirectedGraph graph = builder.build();
		final AtomicLong visitTime = new AtomicLong();
		final AtomicLong visitedArcs = new AtomicLong();
    	final ObjectRBTreeSet<Result> results = SearchEngine.bfs(graph, true, LongList.of(1), x -> true, TrivialScorer.getInstance(), 100, visitTime, visitedArcs);
		// #node #indegree #outdegree #distance (from 1)
		// 1 2 2 0
		// 2 1 1 1
		// 3 1 1 1
		// 4 2 1 2
		final int[] node      = new int[] {2,3,4};
		final int[] indegree  = new int[] {1,1,2};
		final int[] outdegree = new int[] {1,1,1};
		final int[] dist      = new int[] {1,1,2};
		final Long2DoubleOpenHashMap node2score = new Long2DoubleOpenHashMap();
		for (int i = 0; i < node.length; i++)
			node2score.put(node[i], (indegree[i] + outdegree[i]) / Fast.log2(dist[i] + 2));
		//System.err.println(results + "\n" + node2score);
		assertEquals(node.length, results.size());
		for (final Result r: results) {
			assertTrue(node2score.containsKey(r.gid));
			assertEquals(node2score.get(r.gid), r.score);
		}
    }
    
    @Test
    void testSearchEngineTopKProcessor() throws InterruptedException, ExecutionException {
    	final ObjectArrayList<Result> all = new ObjectArrayList<>();
    	final Random random = new Random(0);
    	final int n = 100;
    	final int maxResults = 10;
    	LongSet gids = new LongOpenHashSet();

    	for (int i = 0; i < n; i++) all.add(new Result(random.nextInt(100), random.nextInt(100)));
    	
    	TopKProcessor processor = new TopKProcessor(maxResults, null);
    	final WaitOnTerminateFutureSubscriber<Update> futureSubscriber = new WaitOnTerminateFutureSubscriber<>();
    	processor.subscribe(futureSubscriber);
    	
    	for (int first = 0; first < n; ) {
    		int k = first + 1 + random.nextInt(n - first);
    		while (all.subList(first, k).stream().map(x->x.gid).distinct().count() < k - first) k--;
    		processor.onNext(new ObjectRBTreeSet<>(all.subList(first, k)));
    		first = k;
    	}
    	processor.close();
    	
    	Result[] allArray = all.toArray(new Result[0]);
    	Arrays.sort(allArray);
    	Result[] actual = futureSubscriber.get().current;
    	
    	int i, j;
    	for (i = j = 0; i < allArray.length && j < maxResults; i++) {
    		if (gids.contains(allArray[i].gid)) continue;
    		assertEquals(allArray[i].gid, actual[j].gid);
    		assertEquals(allArray[i].score, actual[j].score);
    		j++;
    	}
    	assertEquals(actual.length, j);	
    }


}
