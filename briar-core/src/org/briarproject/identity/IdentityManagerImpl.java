package org.briarproject.identity;

import org.briarproject.api.db.DatabaseComponent;
import org.briarproject.api.db.DbException;
import org.briarproject.api.db.Transaction;
import org.briarproject.api.identity.AuthorId;
import org.briarproject.api.identity.IdentityManager;
import org.briarproject.api.identity.LocalAuthor;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

<<<<<<< 08099714bab27d1ed48a8bee431a35a38098ecec
class IdentityManagerImpl implements IdentityManager {
=======
import javax.inject.Inject;

import static java.util.logging.Level.WARNING;
import static org.briarproject.api.db.StorageStatus.ACTIVE;
import static org.briarproject.api.db.StorageStatus.ADDING;
import static org.briarproject.api.db.StorageStatus.REMOVING;

class IdentityManagerImpl implements IdentityManager, Service {

	private static final Logger LOG =
			Logger.getLogger(IdentityManagerImpl.class.getName());
>>>>>>> Switched Roboguice/Guice out for Dagger 2

	private final DatabaseComponent db;
	private final List<AddIdentityHook> addHooks;
	private final List<RemoveIdentityHook> removeHooks;

	@Inject
	IdentityManagerImpl(DatabaseComponent db) {
		this.db = db;
		addHooks = new CopyOnWriteArrayList<AddIdentityHook>();
		removeHooks = new CopyOnWriteArrayList<RemoveIdentityHook>();
	}

	@Override
	public void registerAddIdentityHook(AddIdentityHook hook) {
		addHooks.add(hook);
	}

	@Override
	public void registerRemoveIdentityHook(RemoveIdentityHook hook) {
		removeHooks.add(hook);
	}

	@Override
	public void addLocalAuthor(LocalAuthor localAuthor) throws DbException {
		Transaction txn = db.startTransaction();
		try {
			db.addLocalAuthor(txn, localAuthor);
			for (AddIdentityHook hook : addHooks)
				hook.addingIdentity(txn, localAuthor);
			txn.setComplete();
		} finally {
			db.endTransaction(txn);
		}
	}

	@Override
	public LocalAuthor getLocalAuthor(AuthorId a) throws DbException {
		LocalAuthor author;
		Transaction txn = db.startTransaction();
		try {
			author = db.getLocalAuthor(txn, a);
			txn.setComplete();
		} finally {
			db.endTransaction(txn);
		}
		return author;
	}

	@Override
	public Collection<LocalAuthor> getLocalAuthors() throws DbException {
		Collection<LocalAuthor> authors;
		Transaction txn = db.startTransaction();
		try {
			authors = db.getLocalAuthors(txn);
			txn.setComplete();
		} finally {
			db.endTransaction(txn);
		}
		return authors;
	}

	@Override
	public void removeLocalAuthor(AuthorId a) throws DbException {
		Transaction txn = db.startTransaction();
		try {
			LocalAuthor localAuthor = db.getLocalAuthor(txn, a);
			for (RemoveIdentityHook hook : removeHooks)
				hook.removingIdentity(txn, localAuthor);
			db.removeLocalAuthor(txn, a);
			txn.setComplete();
		} finally {
			db.endTransaction(txn);
		}
	}
}
