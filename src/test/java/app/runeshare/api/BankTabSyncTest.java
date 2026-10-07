package app.runeshare.api;

import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class BankTabSyncTest
{
	private static class SentRequest
	{
		final String tag;
		final Consumer<ApiResponse> respond;

		SentRequest(String tag, Consumer<ApiResponse> respond)
		{
			this.tag = tag;
			this.respond = respond;
		}
	}

	private static class ScheduledRetry
	{
		final Runnable task;
		final long delayMs;
		final CompletableFuture<Void> future = new CompletableFuture<>();

		ScheduledRetry(Runnable task, long delayMs)
		{
			this.task = task;
			this.delayMs = delayMs;
		}
	}

	private final List<SentRequest> sent = new ArrayList<>();
	private final List<ScheduledRetry> scheduled = new ArrayList<>();
	private BankTabSync bankTabSync;

	@Before
	public void setUp()
	{
		bankTabSync = new BankTabSync(
				(bankTab, respond) -> sent.add(new SentRequest(bankTab.getTag(), respond)),
				(task, delayMs) -> {
					final ScheduledRetry retry = new ScheduledRetry(task, delayMs);
					scheduled.add(retry);
					return retry.future;
				});
	}

	@Test
	public void showsSyncingUntilRuneShareAnswers()
	{
		bankTabSync.sync(bankTab("Barrows"));

		assertEquals(BankTabSync.State.SYNCING, bankTabSync.getStatus().getState());
	}

	@Test
	public void showsWhenItSynced()
	{
		bankTabSync.sync(bankTab("Barrows"));
		sent.get(0).respond.accept(response(201));

		assertEquals(BankTabSync.State.SYNCED, bankTabSync.getStatus().getState());
		assertNotNull(bankTabSync.getStatus().getSyncedAt());
	}

	@Test
	public void retriesATransientFailureUntilItSucceeds()
	{
		bankTabSync.sync(bankTab("Barrows"));
		sent.get(0).respond.accept(ApiResponse.noResponse());

		assertEquals(BankTabSync.State.RETRYING, bankTabSync.getStatus().getState());
		assertEquals(30_000, scheduled.get(0).delayMs);

		scheduled.get(0).task.run();
		sent.get(1).respond.accept(response(201));

		assertEquals(2, sent.size());
		assertEquals(BankTabSync.State.SYNCED, bankTabSync.getStatus().getState());
	}

	@Test
	public void givesUpAfterThreeRetries()
	{
		bankTabSync.sync(bankTab("Barrows"));
		for (int attempt = 0; attempt < 3; attempt++)
		{
			sent.get(attempt).respond.accept(response(503));
			scheduled.get(attempt).task.run();
		}
		sent.get(3).respond.accept(response(503));

		assertEquals(3, scheduled.size());
		assertEquals(BankTabSync.State.FAILED, bankTabSync.getStatus().getState());
	}

	@Test
	public void showsTheServersReasonForARejectedTab()
	{
		bankTabSync.sync(bankTab("Barrows"));
		sent.get(0).respond.accept(new ApiResponse(422, null, "Tag is too long"));

		assertEquals(BankTabSync.State.FAILED, bankTabSync.getStatus().getState());
		assertEquals("Tag is too long", bankTabSync.getStatus().getFailure());
		assertTrue(scheduled.isEmpty());
	}

	@Test
	public void aNewerTabReplacesAPendingRetry()
	{
		bankTabSync.sync(bankTab("Barrows"));
		sent.get(0).respond.accept(ApiResponse.noResponse());

		bankTabSync.sync(bankTab("Zulrah"));

		assertTrue(scheduled.get(0).future.isCancelled());

		scheduled.get(0).task.run();

		assertEquals(2, sent.size());
		assertEquals("Zulrah", sent.get(1).tag);
	}

	@Test
	public void aLateResponseForAnOlderTabIsIgnored()
	{
		bankTabSync.sync(bankTab("Barrows"));
		bankTabSync.sync(bankTab("Zulrah"));
		sent.get(1).respond.accept(response(201));

		sent.get(0).respond.accept(ApiResponse.noResponse());

		assertEquals("Zulrah", bankTabSync.getStatus().getTag());
		assertEquals(BankTabSync.State.SYNCED, bankTabSync.getStatus().getState());
		assertTrue(scheduled.isEmpty());
	}

	@Test
	public void cancelStopsPendingRetries()
	{
		bankTabSync.sync(bankTab("Barrows"));
		sent.get(0).respond.accept(ApiResponse.noResponse());

		bankTabSync.cancel();
		scheduled.get(0).task.run();

		assertTrue(scheduled.get(0).future.isCancelled());
		assertEquals(1, sent.size());
	}

	private static RuneShareBankTab bankTab(String tag)
	{
		final RuneShareBankTab bankTab = new RuneShareBankTab();
		bankTab.setTag(tag);
		return bankTab;
	}

	private static ApiResponse response(int statusCode)
	{
		return new ApiResponse(statusCode, null);
	}
}
