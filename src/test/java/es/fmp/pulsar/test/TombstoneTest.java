package es.fmp.pulsar.test;

import static org.junit.Assert.*;

import static org.junit.Assert.assertThat;

import static org.hamcrest.Matchers.*;

import java.net.URISyntaxException;
import java.util.concurrent.TimeUnit;

import org.apache.pulsar.client.admin.PulsarAdmin;
import org.apache.pulsar.client.admin.PulsarAdminBuilder;
import org.apache.pulsar.client.admin.PulsarAdminException;
import org.apache.pulsar.client.api.ClientBuilder;
import org.apache.pulsar.client.api.Consumer;
import org.apache.pulsar.client.api.Message;
import org.apache.pulsar.client.api.MessageId;
import org.apache.pulsar.client.api.MessageRoutingMode;
import org.apache.pulsar.client.api.Producer;
import org.apache.pulsar.client.api.PulsarClient;
import org.apache.pulsar.client.api.PulsarClientException;
import org.apache.pulsar.client.api.Schema;
import org.apache.pulsar.client.api.SubscriptionInitialPosition;
import org.apache.pulsar.client.api.TypedMessageBuilder;
import org.apache.pulsar.client.impl.MessageIdImpl;
import org.apache.pulsar.client.impl.TopicMessageIdImpl;
import org.apache.pulsar.common.policies.data.TenantInfo;
import org.jeasy.random.EasyRandom;
import org.junit.BeforeClass;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.ImmutableSet;

/**
 * @author Miguelez
 *
 */
public class TombstoneTest {

	public static final Logger logger = LoggerFactory.getLogger(TombstoneTest.class);
//	static {
//        System.setProperty(org.slf4j.impl.SimpleLogger.DEFAULT_LOG_LEVEL_KEY, "INFO");
//        logger = LoggerFactory.getLogger(TombstoneTest.class);
//	}

	public static final String PULSAR_ADDRESS = "pulsar://localhost:6650";
	public static final String PULSAR_ADMIN_ADDRESS = "http://localhost:8081";
	public static final String CLUSTER = "standalone";
	public static final String TENANT = "test";
	public static final String NAMESPACE = TENANT + "/test";
	public static final String TOPIC = "persistent://" + NAMESPACE + "/dummy-objects";

	private static PulsarClient client;
	private static PulsarAdmin adminClient;

	protected EasyRandom entityGenerator = new EasyRandom();

	private static void createTenant() throws PulsarAdminException {
		if (!adminClient.tenants().getTenants().contains(TENANT)) {
			TenantInfo ti = TenantInfo.builder().allowedClusters(ImmutableSet.of(CLUSTER)).build();
			adminClient.tenants().createTenant(TENANT, ti);
			logger.info("Tenant created: {}", TENANT);
		} else {
			logger.info("Tenant already created: {}", TENANT);
		}
	}

	private static void createNamespace() throws PulsarAdminException {
		if (!adminClient.namespaces().getNamespaces(TENANT).contains(NAMESPACE)) {
			adminClient.namespaces().createNamespace(NAMESPACE);
			logger.info("Namespace created: {}", NAMESPACE);
		} else {
			logger.info("Namespace already created: {}", NAMESPACE);
		}
	}

	private static void deleteTopic() throws PulsarAdminException {
		if (adminClient.topics().getList(NAMESPACE).contains(TOPIC)) {
			adminClient.topics().delete(TOPIC, true);
			logger.info("Previous topic deleted : {}", TOPIC);
		}
	}

	@BeforeClass
	public static void setup() throws URISyntaxException, PulsarClientException, PulsarAdminException {
		PulsarAdminBuilder adminBuilder = PulsarAdmin.builder();
		adminClient = adminBuilder.serviceHttpUrl(PULSAR_ADMIN_ADDRESS).build();

		ClientBuilder builder = PulsarClient.builder();
		client = builder.serviceUrl(PULSAR_ADDRESS).build();

		createTenant();
		createNamespace();
		// Cleanup previous execution
		deleteTopic();
	}

	@SuppressWarnings("unchecked")
	private <T> void produce(T value, boolean delete, boolean explicit) throws PulsarClientException {
		Schema<T> schema = value instanceof byte[] ? (Schema<T>) Schema.BYTES
				: Schema.AVRO((Class<T>) value.getClass());

		try (Producer<T> producer = client.newProducer(schema).topic(TOPIC)
				.messageRoutingMode(MessageRoutingMode.SinglePartition).create()) {
			TypedMessageBuilder<T> msg = producer.newMessage();
			if (value instanceof DummyObject) {
				msg = msg.key(((DummyObject) value).getUuid());
			}

			if (delete) {
				if (explicit) {
					msg = msg.value(null);
				}
			} else {
				msg = msg.value(value);
			}

			msg.send();
		}
	}

	private <T> T consume(Class<T> type) throws PulsarClientException {
		@SuppressWarnings("unchecked")
		Schema<T> schema = type == byte[].class ? (Schema<T>) Schema.BYTES : Schema.AVRO(type);
		Message<T> lastMsg = null;

		try (Consumer<T> consumer = client.newConsumer(schema).topic(TOPIC).subscriptionName("test")
				.subscriptionInitialPosition(SubscriptionInitialPosition.Earliest).subscribe()) {

			MessageId lastMsgId = consumer.getLastMessageId();
			if (hasMessage(lastMsgId)) {
				consumer.seek(lastMsgId);
				Message<T> msg = consumer.receive(2, TimeUnit.SECONDS);

				if (msg != null && (lastMsg == null || lastMsg.getPublishTime() < msg.getPublishTime())) {
					lastMsg = msg;
				}
			}
		}

		return lastMsg != null ? lastMsg.getValue() : null;
	}

	public static boolean hasMessage(MessageId msgId) {
		if (msgId instanceof MessageIdImpl) {
			return ((MessageIdImpl) msgId).getEntryId() >= 0;
		} else if (msgId instanceof TopicMessageIdImpl) {
			return hasMessage(((TopicMessageIdImpl) msgId).getInnerMessageId());
		} else {
			return false;
		}
	}

	@SuppressWarnings("unchecked")
	private <T> void test(T data, boolean explicit) throws Exception {

		Class<T> type = (Class<T>) data.getClass();
		produce(data, false, false);

		T result = consume(type);

		assertNotNull(result);
		assertThat(result, is(data));

		produce(data, true, explicit);

		result = consume(type);

		assertNull(result);
	}

	@Test
	public void testRawImplicitNullValue() throws Exception {
		byte[] data = new byte[10];
		entityGenerator.nextBytes(data);

		/*
		 * Fails with assertion error (result data is empty byte[] instead of null)
		 */
		test(data, false);
	}

	@Test
	public void testRawExplicitNullValue() throws Exception {
		byte[] data = new byte[10];
		entityGenerator.nextBytes(data);

		/*
		 * Works with Pulsar >= 2.7.0. Fails with NullPointerException otherwise.
		 */
		test(data, true);
	}

	@Test
	public void testSchemaImplicitNullValue() throws Exception {
		DummyObject data = entityGenerator.nextObject(DummyObject.class);

		/*
		 * Fails with EOFException
		 */
		test(data, false);
	}

	@Test
	public void testSchemaExplicitNullValue() throws Exception {
		DummyObject data = entityGenerator.nextObject(DummyObject.class);

		/*
		 * Works with Pulsar >= 2.7.0. Fails with NullPointerException otherwise.
		 */
		test(data, true);
	}

}
