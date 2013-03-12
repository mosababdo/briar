package net.sf.briar.android.groups;

import static android.view.Gravity.CENTER;
import static android.view.Gravity.CENTER_VERTICAL;
import static android.view.View.INVISIBLE;
import static android.view.View.VISIBLE;
import static android.widget.LinearLayout.HORIZONTAL;
import static android.widget.LinearLayout.VERTICAL;
import static java.text.DateFormat.SHORT;
import static java.util.logging.Level.INFO;
import static java.util.logging.Level.WARNING;
import static net.sf.briar.api.Rating.BAD;
import static net.sf.briar.api.Rating.GOOD;
import static net.sf.briar.api.Rating.UNRATED;

import java.io.UnsupportedEncodingException;
import java.util.concurrent.Executor;
import java.util.logging.Logger;

import net.sf.briar.R;
import net.sf.briar.android.BriarActivity;
import net.sf.briar.android.BriarService;
import net.sf.briar.android.BriarService.BriarServiceConnection;
import net.sf.briar.android.widgets.CommonLayoutParams;
import net.sf.briar.android.widgets.HorizontalBorder;
import net.sf.briar.android.widgets.HorizontalSpace;
import net.sf.briar.api.Rating;
import net.sf.briar.api.android.BundleEncrypter;
import net.sf.briar.api.db.DatabaseComponent;
import net.sf.briar.api.db.DatabaseExecutor;
import net.sf.briar.api.db.DbException;
import net.sf.briar.api.db.NoSuchMessageException;
import net.sf.briar.api.messaging.AuthorId;
import net.sf.briar.api.messaging.GroupId;
import net.sf.briar.api.messaging.MessageId;
import android.content.Intent;
import android.content.res.Resources;
import android.os.Bundle;
import android.text.format.DateUtils;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.google.inject.Inject;

public class ReadGroupMessageActivity extends BriarActivity
implements OnClickListener {

	static final int RESULT_REPLY = RESULT_FIRST_USER;
	static final int RESULT_PREV = RESULT_FIRST_USER + 1;
	static final int RESULT_NEXT = RESULT_FIRST_USER + 2;

	private static final Logger LOG =
			Logger.getLogger(ReadGroupMessageActivity.class.getName());

	private final BriarServiceConnection serviceConnection =
			new BriarServiceConnection();

	@Inject private BundleEncrypter bundleEncrypter;
	@Inject private DatabaseComponent db;
	@Inject @DatabaseExecutor private Executor dbExecutor;

	private GroupId groupId = null;
	private MessageId messageId = null;
	private AuthorId authorId = null;
	private String authorName = null;
	private Rating rating = UNRATED;
	private boolean read;
	private ImageView thumb = null;
	private ImageButton goodButton = null, badButton = null, readButton = null;
	private ImageButton prevButton = null, nextButton = null;
	private ImageButton replyButton = null;
	private TextView content = null;

	@Override
	public void onCreate(Bundle state) {
		super.onCreate(null);

		Intent i = getIntent();
		byte[] id = i.getByteArrayExtra("net.sf.briar.GROUP_ID");
		if(id == null) throw new IllegalStateException();
		groupId = new GroupId(id);
		String groupName = i.getStringExtra("net.sf.briar.GROUP_NAME");
		if(groupName == null) throw new IllegalStateException();
		setTitle(groupName);
		id = i.getByteArrayExtra("net.sf.briar.MESSAGE_ID");
		if(id == null) throw new IllegalStateException();
		messageId = new MessageId(id);
		boolean anonymous = i.getBooleanExtra("net.sf.briar.ANONYMOUS", false);
		if(!anonymous) {
			id = i.getByteArrayExtra("net.sf.briar.AUTHOR_ID");
			if(id == null) throw new IllegalStateException();
			authorId = new AuthorId(id);
			authorName = i.getStringExtra("net.sf.briar.AUTHOR_NAME");
			if(authorName == null) throw new IllegalStateException();
			String r = i.getStringExtra("net.sf.briar.RATING");
			if(r != null) rating = Rating.valueOf(r);
		}
		String contentType = i.getStringExtra("net.sf.briar.CONTENT_TYPE");
		if(contentType == null) throw new IllegalStateException();
		long timestamp = i.getLongExtra("net.sf.briar.TIMESTAMP", -1);
		if(timestamp == -1) throw new IllegalStateException();
		boolean first = i.getBooleanExtra("net.sf.briar.FIRST", false);
		boolean last = i.getBooleanExtra("net.sf.briar.LAST", false);

		if(state != null && bundleEncrypter.decrypt(state)) {
			read = state.getBoolean("net.sf.briar.READ");
		} else {
			read = false;
			setReadInDatabase(true);
		}

		LinearLayout layout = new LinearLayout(this);
		layout.setLayoutParams(CommonLayoutParams.MATCH_WRAP);
		layout.setOrientation(VERTICAL);

		ScrollView scrollView = new ScrollView(this);
		// Give me all the width and all the unused height
		scrollView.setLayoutParams(CommonLayoutParams.MATCH_WRAP_1);

		LinearLayout message = new LinearLayout(this);
		message.setOrientation(VERTICAL);

		LinearLayout header = new LinearLayout(this);
		header.setLayoutParams(CommonLayoutParams.MATCH_WRAP);
		header.setOrientation(HORIZONTAL);
		header.setGravity(CENTER_VERTICAL);

		thumb = new ImageView(this);
		thumb.setPadding(0, 10, 10, 10);
		if(rating == GOOD) thumb.setImageResource(R.drawable.rating_good);
		else thumb.setImageResource(R.drawable.rating_bad);
		if(rating == UNRATED) thumb.setVisibility(INVISIBLE);
		header.addView(thumb);

		TextView author = new TextView(this);
		// Give me all the unused width
		author.setLayoutParams(CommonLayoutParams.WRAP_WRAP_1);
		author.setTextSize(18);
		author.setMaxLines(1);
		author.setPadding(10, 10, 10, 10);
		Resources res = getResources();
		if(authorName == null) {
			author.setTextColor(res.getColor(R.color.anonymous_author));
			author.setText(R.string.anonymous);
		} else {
			author.setTextColor(res.getColor(R.color.pseudonymous_author));
			author.setText(authorName);
		}
		header.addView(author);

		TextView date = new TextView(this);
		date.setTextSize(14);
		date.setPadding(0, 10, 10, 10);
		long now = System.currentTimeMillis();
		date.setText(DateUtils.formatSameDayTime(timestamp, now, SHORT, SHORT));
		header.addView(date);
		message.addView(header);

		if(contentType.equals("text/plain")) {
			// Load and display the message body
			content = new TextView(this);
			content.setPadding(10, 0, 10, 10);
			message.addView(content);
			loadMessageBody();
		}
		scrollView.addView(message);
		layout.addView(scrollView);

		layout.addView(new HorizontalBorder(this));

		LinearLayout footer = new LinearLayout(this);
		footer.setLayoutParams(CommonLayoutParams.MATCH_WRAP);
		footer.setOrientation(HORIZONTAL);
		footer.setGravity(CENTER);

		goodButton = new ImageButton(this);
		goodButton.setBackgroundResource(0);
		goodButton.setImageResource(R.drawable.rating_good);
		if(authorName == null) goodButton.setEnabled(false);
		else goodButton.setOnClickListener(this);
		footer.addView(goodButton);
		footer.addView(new HorizontalSpace(this));

		badButton = new ImageButton(this);
		badButton.setBackgroundResource(0);
		badButton.setImageResource(R.drawable.rating_bad);
		badButton.setOnClickListener(this);
		if(authorName == null) badButton.setEnabled(false);
		else badButton.setOnClickListener(this);
		footer.addView(badButton);
		footer.addView(new HorizontalSpace(this));

		readButton = new ImageButton(this);
		readButton.setBackgroundResource(0);
		if(read) readButton.setImageResource(R.drawable.content_unread);
		else readButton.setImageResource(R.drawable.content_read);
		readButton.setOnClickListener(this);
		footer.addView(readButton);
		footer.addView(new HorizontalSpace(this));

		prevButton = new ImageButton(this);
		prevButton.setBackgroundResource(0);
		prevButton.setImageResource(R.drawable.navigation_previous_item);
		prevButton.setOnClickListener(this);
		prevButton.setEnabled(!first);
		footer.addView(prevButton);
		footer.addView(new HorizontalSpace(this));

		nextButton = new ImageButton(this);
		nextButton.setBackgroundResource(0);
		nextButton.setImageResource(R.drawable.navigation_next_item);
		nextButton.setOnClickListener(this);
		nextButton.setEnabled(!last);
		footer.addView(nextButton);
		footer.addView(new HorizontalSpace(this));

		replyButton = new ImageButton(this);
		replyButton.setBackgroundResource(0);
		replyButton.setImageResource(R.drawable.social_reply_all);
		replyButton.setOnClickListener(this);
		footer.addView(replyButton);
		layout.addView(footer);

		setContentView(layout);

		// Bind to the service so we can wait for the DB to be opened
		bindService(new Intent(BriarService.class.getName()),
				serviceConnection, 0);
	}

	private void setReadInDatabase(final boolean read) {
		final DatabaseComponent db = this.db;
		final MessageId messageId = this.messageId;
		dbExecutor.execute(new Runnable() {
			public void run() {
				try {
					serviceConnection.waitForStartup();
					db.setReadFlag(messageId, read);
					setReadInUi(read);
				} catch(DbException e) {
					if(LOG.isLoggable(WARNING))
						LOG.log(WARNING, e.toString(), e);
				} catch(InterruptedException e) {
					if(LOG.isLoggable(INFO))
						LOG.info("Interrupted while waiting for service");
					Thread.currentThread().interrupt();
				}
			}
		});
	}

	private void setReadInUi(final boolean read) {
		runOnUiThread(new Runnable() {
			public void run() {
				ReadGroupMessageActivity.this.read = read;
				if(read) readButton.setImageResource(R.drawable.content_unread);
				else readButton.setImageResource(R.drawable.content_read);
			}
		});
	}

	private void loadMessageBody() {
		final DatabaseComponent db = this.db;
		final MessageId messageId = this.messageId;
		dbExecutor.execute(new Runnable() {
			public void run() {
				try {
					serviceConnection.waitForStartup();
					byte[] body = db.getMessageBody(messageId);
					final String text = new String(body, "UTF-8");
					runOnUiThread(new Runnable() {
						public void run() {
							content.setText(text);
						}
					});
				} catch(NoSuchMessageException e) {
					if(LOG.isLoggable(INFO)) LOG.info("Message removed");
					runOnUiThread(new Runnable() {
						public void run() {
							finish();
						}
					});
				} catch(DbException e) {
					if(LOG.isLoggable(WARNING))
						LOG.log(WARNING, e.toString(), e);
				} catch(InterruptedException e) {
					if(LOG.isLoggable(INFO))
						LOG.info("Interrupted while waiting for service");
					Thread.currentThread().interrupt();
				} catch(UnsupportedEncodingException e) {
					throw new RuntimeException(e);
				}
			}
		});
	}

	@Override
	public void onSaveInstanceState(Bundle state) {
		state.putBoolean("net.sf.briar.READ", read);
		bundleEncrypter.encrypt(state);
	}

	@Override
	public void onDestroy() {
		super.onDestroy();
		unbindService(serviceConnection);
	}

	public void onClick(View view) {
		if(view == goodButton) {
			if(rating == BAD) setRatingInDatabase(UNRATED);
			else if(rating == UNRATED) setRatingInDatabase(GOOD);
		} else if(view == badButton) {
			if(rating == GOOD) setRatingInDatabase(UNRATED);
			else if(rating == UNRATED) setRatingInDatabase(BAD);
		} else if(view == readButton) {
			setReadInDatabase(!read);
		} else if(view == prevButton) {
			setResult(RESULT_PREV);
			finish();
		} else if(view == nextButton) {
			setResult(RESULT_NEXT);
			finish();
		} else if(view == replyButton) {
			Intent i = new Intent(this, WriteGroupMessageActivity.class);
			i.putExtra("net.sf.briar.GROUP_ID", groupId.getBytes());
			i.putExtra("net.sf.briar.PARENT_ID", messageId.getBytes());
			startActivity(i);
			setResult(RESULT_REPLY);
			finish();
		}
	}

	private void setRatingInDatabase(final Rating r) {
		final DatabaseComponent db = this.db;
		final AuthorId authorId = this.authorId;
		dbExecutor.execute(new Runnable() {
			public void run() {
				try {
					serviceConnection.waitForStartup();
					db.setRating(authorId, r);
					setRatingInUi(r);
				} catch(DbException e) {
					if(LOG.isLoggable(WARNING))
						LOG.log(WARNING, e.toString(), e);
				} catch(InterruptedException e) {
					if(LOG.isLoggable(INFO))
						LOG.info("Interrupted while waiting for service");
					Thread.currentThread().interrupt();
				}
			}
		});
	}

	private void setRatingInUi(final Rating r) {
		runOnUiThread(new Runnable() {
			public void run() {
				ReadGroupMessageActivity.this.rating = r;
				if(r == GOOD) {
					thumb.setImageResource(R.drawable.rating_good);
					thumb.setVisibility(VISIBLE);
				} else if(r == BAD) {
					thumb.setImageResource(R.drawable.rating_bad);
					thumb.setVisibility(VISIBLE);
				} else {
					thumb.setVisibility(INVISIBLE);
				}
			}
		});
	}
}