package eu.fasten.core.index;

import java.io.FileReader;
import java.io.IOException;
import java.net.URISyntaxException;
import java.time.Duration;
import java.util.Collections;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;
import org.rocksdb.RocksDBException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.martiansoftware.jsap.FlaggedOption;
import com.martiansoftware.jsap.JSAP;
import com.martiansoftware.jsap.JSAPException;
import com.martiansoftware.jsap.JSAPResult;
import com.martiansoftware.jsap.Parameter;
import com.martiansoftware.jsap.SimpleJSAP;
import com.martiansoftware.jsap.UnflaggedOption;

import eu.fasten.core.data.KnowledgeBase;
import eu.fasten.core.data.RevisionCallGraph;
/** A sample in-memory indexer that reads, compresses and stores in memory
 *  graphs stored in JSON format and answers to impact queries.
 *
 */
public class Indexer {

	private static final Logger LOGGER = LoggerFactory.getLogger(Indexer.class);
	private final KnowledgeBase kb;


	/** Creates an indexer using the given database instance.
	 *
	 * @param db the database instance used by this indexer.
	 */
	public Indexer(final KnowledgeBase kb) {
		this.kb = kb;
	}

	private final boolean[] stopIndexing = new boolean[1];

	public Future<Void> index(final long max, final Consumer<String, String> consumer, final String topic) {
		consumer.subscribe(Collections.singletonList(topic));
		return Executors.newSingleThreadExecutor().submit(() -> {
			long index = kb.size();
			long nIndexed = 0;
			try {
				while(!stopIndexing[0]) {
					final ConsumerRecords<String, String> records = consumer.poll(Duration.ofDays(356));

					for (final ConsumerRecord<String, String> record : records) {
						if (stopIndexing[0]) break;
						final JSONObject json = new JSONObject(record.value());
						try {
							LOGGER.debug("Getting new record with key " + record.key());
							kb.add(new RevisionCallGraph(json, false), index++);
							nIndexed++;
							if (nIndexed >= max) {
								stopIndexing[0] = true;
								break;
							}
						} catch(final IllegalArgumentException e) {
							e.printStackTrace(System.err);
							throw new RuntimeException(e);
						}
					}
				}

				return null;
			}
			finally {
				consumer.close();
			}
		});
	}

	public void index(final long max, final String... files) throws JSONException, IOException, RocksDBException, URISyntaxException {
		long index = kb.size();
		long nIndexed = 0;
		for(final String file: files) {
			LOGGER.debug("Parsing " + file);
			final FileReader reader = new FileReader(file);
			final JSONObject json = new JSONObject(new JSONTokener(reader));
			kb.add(new RevisionCallGraph(json, false), index++);
			nIndexed++;
			if (nIndexed >= max)  break;
			reader.close();
		}
	}


	public static void main(final String[] args) throws JSONException, URISyntaxException, JSAPException, IOException, RocksDBException, InterruptedException, ExecutionException, ClassNotFoundException {
		final SimpleJSAP jsap = new SimpleJSAP( Indexer.class.getName(),
				"Creates or updates a knowledge base (associated to a given database), indexing either a list of JSON files or a Kafka topic where JSON object are published",
				new Parameter[] {
						new FlaggedOption("topic", JSAP.STRING_PARSER, JSAP.NO_DEFAULT, JSAP.NOT_REQUIRED, 't', "topic", "A kafka topic containing the input." ),
						new FlaggedOption("host", JSAP.STRING_PARSER, "localhost", JSAP.NOT_REQUIRED, 'h', "host", "The host of the Kafka server." ),
						new FlaggedOption("port", JSAP.INTEGER_PARSER, "30001", JSAP.NOT_REQUIRED, 'p', "port", "The port of the Kafka server." ),
						new FlaggedOption("max", JSAP.LONG_PARSER, String.valueOf(Long.MAX_VALUE), JSAP.NOT_REQUIRED, 'm', "max", "The maximum number of call graphs that will be indexed." ),
						new UnflaggedOption("kb", JSAP.STRING_PARSER, JSAP.NO_DEFAULT, JSAP.REQUIRED, JSAP.NOT_GREEDY, "The directory of the RocksDB instance containing the knowledge base." ),
						new UnflaggedOption("filename", JSAP.STRING_PARSER, JSAP.NO_DEFAULT, JSAP.NOT_REQUIRED, JSAP.GREEDY, "The name of the file containing the JSON object." ),
		});

		final JSAPResult jsapResult = jsap.parse(args);
		if ( jsap.messagePrinted() ) return;

		final String kbDir = jsapResult.getString("kb");

		final KnowledgeBase kb = KnowledgeBase.getInstance(kbDir);

		final Indexer indexer = new Indexer(kb);

		final long max = jsapResult.getLong("max");

		final Consumer<String, String> consumer;
		if (jsapResult.userSpecified("topic")) {
			// Kafka indexing
			final String topic = jsapResult.getString("topic");
			final Properties props = new Properties();
			props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, jsapResult.getString("host") + ":" + Integer.toString(jsapResult.getInt("port")));
			props.put(ConsumerConfig.GROUP_ID_CONFIG, UUID.randomUUID().toString()); // We want to have a random consumer group.
			props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
			props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
			props.put("auto.offset.reset", "earliest");
			props.put("max.poll.records", Integer.toString(Integer.MAX_VALUE));
			consumer = new KafkaConsumer<>(props);
			final Future<Void> future = indexer.index(max, consumer, topic);
			future.get(); // Wait for indexing to complete
		} else
			// File indexing
			indexer.index(max, jsapResult.getStringArray("filename"));
		kb.close();
	}
}
