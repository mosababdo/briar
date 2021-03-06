package org.briarproject.bramble.api.sync;

import org.briarproject.bramble.api.contact.ContactId;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.transport.StreamWriter;

import java.io.InputStream;

@NotNullByDefault
public interface SyncSessionFactory {

	SyncSession createIncomingSession(ContactId c, InputStream in);

	SyncSession createSimplexOutgoingSession(ContactId c, int maxLatency,
			StreamWriter streamWriter);

	SyncSession createDuplexOutgoingSession(ContactId c, int maxLatency,
			int maxIdleTime, StreamWriter streamWriter);
}
