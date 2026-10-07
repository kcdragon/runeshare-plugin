package app.runeshare.api;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class RetryPolicyTest
{
	@Test
	public void bankTabBacksOffThenGivesUp()
	{
		final ApiResponse unreachable = ApiResponse.noResponse();

		assertEquals(Long.valueOf(30_000), RetryPolicy.BANK_TAB.delayBeforeRetryMs(unreachable, 1));
		assertEquals(Long.valueOf(60_000), RetryPolicy.BANK_TAB.delayBeforeRetryMs(unreachable, 2));
		assertEquals(Long.valueOf(120_000), RetryPolicy.BANK_TAB.delayBeforeRetryMs(unreachable, 3));
		assertNull(RetryPolicy.BANK_TAB.delayBeforeRetryMs(unreachable, 4));
	}

	@Test
	public void bankTabRetriesServerErrorsAndRateLimits()
	{
		assertEquals(Long.valueOf(30_000), RetryPolicy.BANK_TAB.delayBeforeRetryMs(response(503), 1));
		assertEquals(Long.valueOf(30_000), RetryPolicy.BANK_TAB.delayBeforeRetryMs(response(429), 1));
	}

	@Test
	public void neverRetriesResponsesThatWouldRepeat()
	{
		for (int statusCode : new int[] { 401, 403, 404, 422 })
		{
			assertNull("status " + statusCode, RetryPolicy.BANK_TAB.delayBeforeRetryMs(response(statusCode), 1));
			assertNull("status " + statusCode, RetryPolicy.STOP_TASK_SESSION.delayBeforeRetryMs(response(statusCode), 1));
		}
	}

	@Test
	public void stopTaskSessionMakesThreeAttempts()
	{
		final ApiResponse serverError = response(500);

		assertEquals(Long.valueOf(5_000), RetryPolicy.STOP_TASK_SESSION.delayBeforeRetryMs(serverError, 1));
		assertEquals(Long.valueOf(5_000), RetryPolicy.STOP_TASK_SESSION.delayBeforeRetryMs(serverError, 2));
		assertNull(RetryPolicy.STOP_TASK_SESSION.delayBeforeRetryMs(serverError, 3));
	}

	@Test
	public void stopTaskSessionDoesNotRetryRateLimits()
	{
		assertNull(RetryPolicy.STOP_TASK_SESSION.delayBeforeRetryMs(response(429), 1));
	}

	private static ApiResponse response(int statusCode)
	{
		return new ApiResponse(statusCode, null);
	}
}
