package app.runeshare.api;

import app.runeshare.RuneShareConfig;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class RuneShareConnectionTest
{
	private static final String TOKEN = "current-token";

	private static final CurrentUser MIKE = new CurrentUser("mike", "osrs", new CurrentUser.ApiToken("RuneLite"), null);

	private String configuredToken;
	private int notifications;
	private RuneShareConnection connection;

	@Before
	public void setUp()
	{
		configuredToken = TOKEN;
		notifications = 0;
		connection = new RuneShareConnection(new RuneShareConfig()
		{
			@Override
			public String apiToken()
			{
				return configuredToken;
			}
		});
		connection.setListener(() -> notifications++);
	}

	@Test
	public void startsUnknown()
	{
		assertEquals(ConnectionStatus.UNKNOWN, connection.getStatus());
	}

	@Test
	public void successConnects()
	{
		connection.report(TOKEN, response(201));

		assertEquals(ConnectionStatus.CONNECTED, connection.getStatus());
	}

	@Test
	public void unauthorizedFromAnyEndpointInvalidatesTheToken()
	{
		connection.report(TOKEN, response(200));
		connection.report(TOKEN, response(401));

		assertEquals(ConnectionStatus.INVALID_TOKEN, connection.getStatus());
	}

	@Test
	public void forbiddenMeansTheApiIsDisabled()
	{
		connection.report(TOKEN, response(403));

		assertEquals(ConnectionStatus.API_DISABLED, connection.getStatus());
	}

	@Test
	public void transientFailuresMakeItUnavailable()
	{
		for (int statusCode : new int[] { ApiResponse.NO_RESPONSE, 429, 500, 503 })
		{
			connection.reset();
			connection.report(TOKEN, response(statusCode));

			assertEquals("status " + statusCode, ConnectionStatus.UNAVAILABLE, connection.getStatus());
		}
	}

	@Test
	public void successRecoversFromUnavailable()
	{
		connection.report(TOKEN, ApiResponse.noResponse());
		connection.report(TOKEN, response(201));

		assertEquals(ConnectionStatus.CONNECTED, connection.getStatus());
	}

	@Test
	public void requestErrorsLeaveTheStatusAlone()
	{
		connection.report(TOKEN, response(200));
		connection.report(TOKEN, response(404));
		connection.report(TOKEN, response(422));

		assertEquals(ConnectionStatus.CONNECTED, connection.getStatus());
	}

	@Test
	public void ignoresResponsesForAReplacedToken()
	{
		connection.report(TOKEN, response(200));
		configuredToken = "new-token";
		connection.reset();

		connection.report(TOKEN, response(401));

		assertEquals(ConnectionStatus.UNKNOWN, connection.getStatus());
	}

	@Test
	public void ignoresTheCurrentUserForAReplacedToken()
	{
		configuredToken = "new-token";

		connection.reportCurrentUser(TOKEN, response(200), MIKE);

		assertNull(connection.getCurrentUser());
	}

	@Test
	public void remembersTheCurrentUserUntilReset()
	{
		connection.reportCurrentUser(TOKEN, response(200), MIKE);
		connection.report(TOKEN, response(201));

		assertEquals(MIKE, connection.getCurrentUser());

		connection.reset();

		assertNull(connection.getCurrentUser());
		assertEquals(ConnectionStatus.UNKNOWN, connection.getStatus());
	}

	@Test
	public void notifiesOnlyWhenSomethingChanges()
	{
		connection.report(TOKEN, response(201));
		connection.report(TOKEN, response(201));
		connection.reportCurrentUser(TOKEN, response(200), MIKE);
		connection.reportCurrentUser(TOKEN, response(200), MIKE);

		assertEquals(2, notifications);
	}

	private static ApiResponse response(int statusCode)
	{
		return new ApiResponse(statusCode, null);
	}
}
