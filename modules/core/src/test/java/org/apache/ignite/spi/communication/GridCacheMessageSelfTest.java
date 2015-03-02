package org.apache.ignite.spi.communication;

import org.apache.ignite.cache.*;
import org.apache.ignite.configuration.*;
import org.apache.ignite.internal.*;
import org.apache.ignite.internal.managers.communication.*;
import org.apache.ignite.internal.processors.cache.*;
import org.apache.ignite.internal.util.typedef.*;
import org.apache.ignite.plugin.extensions.communication.*;
import org.apache.ignite.testframework.junits.common.*;

import java.nio.*;
import java.util.*;
import java.util.concurrent.*;

import static java.util.concurrent.TimeUnit.*;
import static org.apache.ignite.cache.CacheWriteSynchronizationMode.FULL_SYNC;

/**
 * Messaging test.
 */
public class GridCacheMessageSelfTest extends GridCommonAbstractTest {
    /** Sample count. */
    private static final int SAMPLE_CNT = 3;

    /** */
    private static final byte DIRECT_TYPE = (byte)202;

    /** */
    private static final byte DIRECT_TYPE1 = (byte)203;

    static {
        GridIoMessageFactory.registerCustom(DIRECT_TYPE, new CO<Message>() {
            @Override public Message apply() {
                return new TestMessage();
            }
        });

        GridIoMessageFactory.registerCustom(GridTestMessage.DIRECT_TYPE, new CO<Message>() {
            @Override public Message apply() {
                return new GridTestMessage();
            }
        });

        GridIoMessageFactory.registerCustom(DIRECT_TYPE1, new CO<Message>() {
            @Override public Message apply() {
                return new TestMessage1();
            }
        });
    }

    /** {@inheritDoc} */
    @Override protected IgniteConfiguration getConfiguration(String gridName) throws Exception {
        IgniteConfiguration cfg = super.getConfiguration(gridName);

        cfg.setIncludeEventTypes((int[])null);

        CacheConfiguration ccfg = new CacheConfiguration();

        ccfg.setCacheMode(CacheMode.PARTITIONED);
        ccfg.setAtomicityMode(CacheAtomicityMode.ATOMIC);
        ccfg.setWriteSynchronizationMode(FULL_SYNC);

        ccfg.setBackups(0);

        cfg.setCacheConfiguration(ccfg);

        return cfg;
    }

    /**
     * @throws Exception If failed.
     */
    public void testSendMessage() throws Exception {
        try {
            startGridsMultiThreaded(2);

            doSend();
        }
        finally {
            stopAllGrids();
        }
    }

    /**
     * @throws Exception If failed.
     */
    private void doSend() throws Exception {
        GridIoManager mgr0 = ((IgniteKernal)grid(0)).context().io();
        GridIoManager mgr1 = ((IgniteKernal)grid(1)).context().io();

        String topic = "test-topic";

        final CountDownLatch latch = new CountDownLatch(SAMPLE_CNT);

        mgr1.addMessageListener(topic, new GridMessageListener() {
            @Override public void onMessage(UUID nodeId, Object msg) {
                latch.countDown();
                TestMessage msg1 = (TestMessage) msg;
                info("Test : " + msg1.fieldsCount());
            }
        });

        TestMessage msg = new TestMessage();

        for (int i = 1; i <= SAMPLE_CNT; i++) {
            mgr0.send(grid(1).localNode(), topic, msg, GridIoPolicy.PUBLIC_POOL);

            TestMessage1 mes1 = new TestMessage1();

            mes1.init(new GridTestMessage(grid(1).localNode().id(), i, 0));

            msg.add(mes1);
        }

        assert latch.await(3, SECONDS);
    }

    /** */
    private static class TestMessage extends GridCacheMessage {
        /** */
        @GridDirectCollection(TestMessage1.class)
        private Collection<TestMessage1> entries = new ArrayList<>();

        /**
         * @param entry Entry.
         */
        public void add(TestMessage1 entry) {
            entries.add(entry);
        }

        @Override public byte directType() {
            return DIRECT_TYPE;
        }

        @Override public byte fieldsCount() {
            return 1;
        }

        /** {@inheritDoc} */
        @Override public boolean writeTo(ByteBuffer buf, MessageWriter writer) {
            writer.setBuffer(buf);

            if (!writer.isHeaderWritten()) {
                if (!writer.writeHeader(directType(), fieldsCount()))
                    return false;

                writer.onHeaderWritten();
            }

            switch (writer.state()) {
                case 0:
                    if (!writer.writeCollection("entries", entries, MessageCollectionItemType.MSG))
                        return false;

                    writer.incrementState();
            }

            return true;
        }

        /** {@inheritDoc} */
        @Override public boolean readFrom(ByteBuffer buf, MessageReader reader) {
            reader.setBuffer(buf);

            if (!reader.beforeMessageRead())
                return false;

            switch (reader.state()) {
                case 0:
                    entries = reader.readCollection("entries", MessageCollectionItemType.MSG);

                    if (!reader.isLastRead())
                        return false;

                    reader.incrementState();
            }

            return true;
        }
    }

    /**
     *
     */
    private static class TestMessage1 extends GridCacheMessage {
        /** */
        GridTestMessage mes;

        public void init(GridTestMessage mes) {
            this.mes = mes;
        }

        /** {@inheritDoc} */
        @Override public byte directType() {
            return DIRECT_TYPE1;
        }

        /** {@inheritDoc} */
        @Override public byte fieldsCount() {
            return 1;
        }

        /** {@inheritDoc} */
        @Override public boolean writeTo(ByteBuffer buf, MessageWriter writer) {
            writer.setBuffer(buf);

            if (!writer.isHeaderWritten()) {
                if (!writer.writeHeader(directType(), fieldsCount()))
                    return false;

                writer.onHeaderWritten();
            }

            switch (writer.state()) {
                case 0:
                    if (!mes.writeTo(buf, writer))
                        return false;

                    writer.incrementState();
            }

            return true;
        }

        /** {@inheritDoc} */
        @Override public boolean readFrom(ByteBuffer buf, MessageReader reader) {
            reader.setBuffer(buf);

            if (!reader.beforeMessageRead())
                return false;

            switch (reader.state()) {
                case 0:
                    mes.readFrom(buf, reader);

                    if (!reader.isLastRead())
                        return false;

                    reader.incrementState();
            }

            return true;
        }
    }
}
