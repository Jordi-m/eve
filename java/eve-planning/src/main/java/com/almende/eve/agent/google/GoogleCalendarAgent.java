/**
 * @file GoogleCalendarAgent.java
 * 
 * @brief
 *        The GoogleCalendarAgent can connect to a single Google Calendar, and
 *        get, create, update, and delete events. The agent uses Goolges RESTful
 *        API v3
 *        to access a Google Calendar, and does not use any specific Java
 *        libraries
 *        for that. See:
 *        https://developers.google.com/google-apps/calendar/v3/reference/
 * 
 *        To setup authorization for a calendar agent, the method
 *        setAuthorization
 *        must be executed with valid authorization tokens. The agent will store
 *        the tokens and refresh them automatically when needed.
 *        To retrieve valid access tokens from google, the servlet
 *        GoogleAuth.java
 *        can be used. This servlet is typically running at /auth/google.
 *        Authorization needs to be setup only once for an agent.
 * 
 *        The GoogleCalendarAgent contains the following core methods:
 *        - getEvents Get all events in a given time window
 *        - getEvent Get a specific event by its id
 *        - createEvent Create a new event
 *        - updateEvent Update an existing event
 *        - deleteEvent Delete an existing event
 *        - getBusy Get the busy intervals in given time window
 *        - clear Delete all stored information
 * 
 * @license
 *          Licensed under the Apache License, Version 2.0 (the "License"); you
 *          may not
 *          use this file except in compliance with the License. You may obtain
 *          a copy
 *          of the License at
 * 
 *          http://www.apache.org/licenses/LICENSE-2.0
 * 
 *          Unless required by applicable law or agreed to in writing, software
 *          distributed under the License is distributed on an "AS IS" BASIS,
 *          WITHOUT
 *          WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See
 *          the
 *          License for the specific language governing permissions and
 *          limitations under
 *          the License.
 * 
 *          Copyright © 2012 Almende B.V.
 * 
 * @author Jos de Jong, <jos@almende.org>
 * @date 2012-07-03
 */

/**
 * 
 * DOCUMENTATION:
 * https://developers.google.com/google-apps/calendar/v3/reference/
 * https://developers.google.com/google-apps/calendar/v3/reference/events#
 * resource
 */
package com.almende.eve.agent.google;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import org.joda.time.DateTime;

import com.almende.eve.agent.Agent;
import com.almende.eve.agent.CalendarAgent;
import com.almende.eve.config.Config;
import com.almende.eve.entity.calendar.Authorization;
import com.almende.eve.rpc.annotation.Access;
import com.almende.eve.rpc.annotation.AccessType;
import com.almende.eve.rpc.annotation.Name;
import com.almende.eve.rpc.annotation.Optional;
import com.almende.eve.rpc.jsonrpc.JSONRPCException;
import com.almende.eve.rpc.jsonrpc.JSONRPCException.CODE;
import com.almende.eve.rpc.jsonrpc.jackson.JOM;
import com.almende.eve.state.State;
import com.almende.util.HttpUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * The Class GoogleCalendarAgent.
 */
@Access(AccessType.PUBLIC)
public class GoogleCalendarAgent extends Agent implements CalendarAgent {

	// note: config parameters google.client_id and google.client_secret
	// are loaded from the eve configuration
	private static final String	OAUTH_URI		= "https://accounts.google.com/o/oauth2";
	private static final String	CALENDAR_URI	= "https://www.googleapis.com/calendar/v3/calendars/";
	
	/**
	 * Set access token and refresh token, used to authorize the calendar agent.
	 * These tokens must be retrieved via Oauth 2.0 authorization.
	 * 
	 * @param access_token
	 *            the access_token
	 * @param token_type
	 *            the token_type
	 * @param expires_in
	 *            the expires_in
	 * @param refresh_token
	 *            the refresh_token
	 * @throws IOException
	 *             Signals that an I/O exception has occurred.
	 */
	public void setAuthorization(
			@Name("access_token") final String access_token,
			@Name("token_type") final String token_type,
			@Name("expires_in") final Integer expires_in,
			@Name("refresh_token") final String refresh_token)
			throws IOException {
		final State state = getState();
		
		// retrieve user information
		final String url = "https://www.googleapis.com/oauth2/v1/userinfo";
		final Map<String, String> headers = new HashMap<String, String>();
		headers.put("Authorization", token_type + " " + access_token);
		final String resp = HttpUtil.get(url, headers);
		
		final ObjectNode info = JOM.getInstance().readValue(resp,
				ObjectNode.class);
		final String email = info.has("email") ? info.get("email").asText()
				: null;
		final String name = info.has("name") ? info.get("name").asText() : null;
		
		final DateTime expires_at = calculateExpiresAt(expires_in);
		final Authorization auth = new Authorization(access_token, token_type,
				expires_at, refresh_token);
		
		// store the tokens in the state
		state.put("auth", auth);
		state.put("email", email);
		state.put("name", name);
	}
	
	/**
	 * Calculate the expiration time from a life time
	 * 
	 * @param expires_in
	 *            Expiration time in seconds
	 * @return
	 */
	private DateTime calculateExpiresAt(final Integer expires_in) {
		DateTime expires_at = null;
		if (expires_in != null && expires_in != 0) {
			// calculate expiration time, and subtract 5 minutes for safety
			expires_at = DateTime.now().plusSeconds(expires_in).minusMinutes(5);
		}
		return expires_at;
	}
	
	/**
	 * Refresh the access token using the refresh token the tokens in provided
	 * authorization object will be updated
	 * 
	 * @param auth
	 * @throws Exception
	 */
	private void refreshAuthorization(final Authorization auth)
			throws Exception {
		final String refresh_token = (auth != null) ? auth.getRefreshToken()
				: null;
		if (refresh_token == null) {
			throw new Exception("No refresh token available");
		}
		
		final Config config = getAgentHost().getConfig();
		final String client_id = config.get("google", "client_id");
		final String client_secret = config.get("google", "client_secret");
		
		// retrieve new access_token using the refresh_token
		final Map<String, String> params = new HashMap<String, String>();
		params.put("client_id", client_id);
		params.put("client_secret", client_secret);
		params.put("refresh_token", refresh_token);
		params.put("grant_type", "refresh_token");
		final String resp = HttpUtil.postForm(OAUTH_URI + "/token", params);
		final ObjectNode json = JOM.getInstance().readValue(resp,
				ObjectNode.class);
		if (!json.has("access_token")) {
			// TODO: give more specific error message
			throw new Exception("Retrieving new access token failed");
		}
		
		// update authorization
		if (json.has("access_token")) {
			auth.setAccessToken(json.get("access_token").asText());
		}
		if (json.has("expires_in")) {
			final Integer expires_in = json.get("expires_in").asInt();
			final DateTime expires_at = calculateExpiresAt(expires_in);
			auth.setExpiresAt(expires_at);
		}
	}
	
	/**
	 * Remove all stored data from this agent.
	 */
	@Override
	public void onDelete() {
		final State state = getState();
		state.remove("auth");
		state.remove("email");
		state.remove("name");
		super.onDelete();
	}
	
	/**
	 * Get the username associated with the calendar.
	 * 
	 * @return name
	 */
	@Override
	public String getUsername() {
		return getState().get("name", String.class);
	}
	
	/**
	 * Get the email associated with the calendar.
	 * 
	 * @return email
	 */
	@Override
	public String getEmail() {
		return getState().get("email", String.class);
	}
	
	/**
	 * Get ready-made HTTP request headers containing the authorization token
	 * Example usage: HttpUtil.get(url, getAuthorizationHeaders());
	 * 
	 * @return
	 * @throws Exception
	 */
	private Map<String, String> getAuthorizationHeaders() throws Exception {
		final Authorization auth = getAuthorization();
		
		final String access_token = (auth != null) ? auth.getAccessToken()
				: null;
		if (access_token == null) {
			throw new Exception("No authorization token available");
		}
		final String token_type = (auth != null) ? auth.getTokenType() : null;
		if (token_type == null) {
			throw new Exception("No token type available");
		}
		
		final Map<String, String> headers = new HashMap<String, String>();
		headers.put("Authorization", token_type + " " + access_token);
		return headers;
	}
	
	/**
	 * Retrieve authorization tokens
	 * 
	 * @return
	 * @throws Exception
	 */
	private Authorization getAuthorization() throws Exception {
		final Authorization auth = getState().get("auth", Authorization.class);
		
		// check if access_token is expired
		final DateTime expires_at = (auth != null) ? auth.getExpiresAt() : null;
		if (expires_at != null && expires_at.isBeforeNow()) {
			refreshAuthorization(auth);
			getState().put("auth", auth);
		}
		
		return auth;
	}
	
	/**
	 * Get the calendar agents version.
	 * 
	 * @return the version
	 */
	@Override
	public String getVersion() {
		return "0.5";
	}
	
	/**
	 * Get the calendar agents description.
	 * 
	 * @return the description
	 */
	@Override
	public String getDescription() {
		return "This agent gives access to a Google Calendar. "
				+ "It allows to search events, find free timeslots, "
				+ "and add, edit, or remove events.";
	}
	
	/**
	 * Convert the event from a Eve event to a Google event
	 * 
	 * @param event
	 */
	private void toGoogleEvent(final ObjectNode event) {
		if (event.has("agent") && event.get("agent").isTextual()) {
			// move agent url from event.agent to extendedProperties
			final String agent = event.get("agent").asText();
			event.with("extendedProperties").with("shared").put("agent", agent);
			
			// TODO: change location into a string
		}
	}
	
	/**
	 * Convert the event from a Google event to a Eve event
	 * 
	 * @param event
	 */
	private void toEveEvent(final ObjectNode event) {
		final ObjectNode extendedProperties = (ObjectNode) event
				.get("extendedProperties");
		if (extendedProperties != null) {
			final ObjectNode shared = (ObjectNode) extendedProperties
					.get("shared");
			if (shared != null && shared.has("agent")
					&& shared.get("agent").isTextual()) {
				// move agent url from extended properties to event.agent
				final String agent = shared.get("agent").asText();
				event.put("agent", agent);
				
				/*
				 * TODO: remove agent from extended properties
				 * shared.remove("agent"); if (shared.size() == 0) {
				 * extendedProperties.remove("shared"); if
				 * (extendedProperties.size() == 0) {
				 * event.remove("extendedProperties"); } }
				 */
				
				// TODO: replace string location with Location object
			}
		}
	}
	
	/**
	 * Retrieve a list with all calendars in this google calendar.
	 * 
	 * @return the calendar list
	 * @throws Exception
	 *             the exception
	 */
	@Override
	public ArrayNode getCalendarList() throws Exception {
		final String url = CALENDAR_URI + "users/me/calendarList";
		final String resp = HttpUtil.get(url, getAuthorizationHeaders());
		final ObjectNode calendars = JOM.getInstance().readValue(resp,
				ObjectNode.class);
		
		// check for errors
		if (calendars.has("error")) {
			final ObjectNode error = (ObjectNode) calendars.get("error");
			throw new JSONRPCException(error);
		}
		
		// get items from response
		ArrayNode items = null;
		if (calendars.has("items")) {
			items = (ArrayNode) calendars.get("items");
		} else {
			items = JOM.createArrayNode();
		}
		
		return items;
	}
	
	/**
	 * Get todays events. A convenience method for easy testing
	 * 
	 * @param calendarId
	 *            the calendar id
	 * @return the events today
	 * @throws Exception
	 *             the exception
	 */
	public ArrayNode getEventsToday(
			@Optional @Name("calendarId") final String calendarId)
			throws Exception {
		final DateTime now = DateTime.now();
		final DateTime timeMin = now.minusMillis(now.getMillisOfDay());
		final DateTime timeMax = timeMin.plusDays(1);
		
		return getEvents(timeMin.toString(), timeMax.toString(), calendarId);
	}
	
	/**
	 * Get all events in given interval.
	 * 
	 * @param timeMin
	 *            start of the interval
	 * @param timeMax
	 *            end of the interval
	 * @param calendarId
	 *            optional calendar id. If not provided, the default calendar is
	 *            used
	 * @return the events
	 * @throws Exception
	 *             the exception
	 */
	@Override
	public ArrayNode getEvents(@Optional @Name("timeMin") final String timeMin,
			@Optional @Name("timeMax") final String timeMax,
			@Optional @Name("calendarId") String calendarId) throws Exception {
		// initialize optional parameters
		if (calendarId == null) {
			calendarId = getState().get("email", String.class);
		}
		
		// built url with query parameters
		String url = CALENDAR_URI + calendarId + "/events";
		final Map<String, String> params = new HashMap<String, String>();
		if (timeMin != null) {
			params.put("timeMin", new DateTime(timeMin).toString());
		}
		if (timeMax != null) {
			params.put("timeMax", new DateTime(timeMax).toString());
		}
		// Set singleEvents=true to expand recurring events into instances
		params.put("singleEvents", "true");
		url = HttpUtil.appendQueryParams(url, params);
		
		// perform GET request
		final Map<String, String> headers = getAuthorizationHeaders();
		final String resp = HttpUtil.get(url, headers);
		final ObjectMapper mapper = JOM.getInstance();
		final ObjectNode json = mapper.readValue(resp, ObjectNode.class);
		
		// check for errors
		if (json.has("error")) {
			final ObjectNode error = (ObjectNode) json.get("error");
			throw new JSONRPCException(error);
		}
		
		// get items from the response
		ArrayNode items = null;
		if (json.has("items")) {
			items = (ArrayNode) json.get("items");
			
			// convert from Google to Eve event
			for (int i = 0; i < items.size(); i++) {
				final ObjectNode item = (ObjectNode) items.get(i);
				toEveEvent(item);
			}
		} else {
			items = JOM.createArrayNode();
		}
		
		return items;
	}
	
	/**
	 * Get busy intervals of today. A convenience method for easy testing
	 * 
	 * @param calendarId
	 *            optional calendar id. If not provided, the default calendar is
	 *            used
	 * @param timeZone
	 *            Time zone used in the response. Optional. The default is UTC.
	 * @return the busy today
	 * @throws Exception
	 *             the exception
	 */
	public ArrayNode getBusyToday(
			@Optional @Name("calendarId") final String calendarId,
			@Optional @Name("timeZone") final String timeZone) throws Exception {
		final DateTime now = DateTime.now();
		final DateTime timeMin = now.minusMillis(now.getMillisOfDay());
		final DateTime timeMax = timeMin.plusDays(1);
		
		return getBusy(timeMin.toString(), timeMax.toString(), calendarId,
				timeZone);
	}
	
	/**
	 * Get all busy event timeslots in given interval.
	 * 
	 * @param timeMin
	 *            start of the interval
	 * @param timeMax
	 *            end of the interval
	 * @param calendarId
	 *            optional calendar id. If not provided, the default calendar is
	 *            used
	 * @param timeZone
	 *            Time zone used in the response. Optional. The default is UTC.
	 * @return the busy
	 * @throws Exception
	 *             the exception
	 */
	@Override
	public ArrayNode getBusy(@Name("timeMin") final String timeMin,
			@Name("timeMax") final String timeMax,
			@Optional @Name("calendarId") String calendarId,
			@Optional @Name("timeZone") final String timeZone) throws Exception {
		// initialize optional parameters
		if (calendarId == null) {
			calendarId = getState().get("email", String.class);
		}
		
		// build request body
		final ObjectNode request = new ObjectNode(JsonNodeFactory.instance);
		request.put("timeMin", new DateTime(timeMin).toString());
		request.put("timeMax", new DateTime(timeMax).toString());
		
		if (timeZone != null) {
			request.put("timeZone", timeZone);
		}
		
		final ArrayNode node = request.putArray("items");
		node.addObject().put("id", calendarId);
		
		final String url = "https://www.googleapis.com/calendar/v3/freeBusy";
		
		// perform POST request
		final ObjectMapper mapper = JOM.getInstance();
		final String body = mapper.writeValueAsString(request);
		final Map<String, String> headers = getAuthorizationHeaders();
		headers.put("Content-Type", "application/json");
		final String resp = HttpUtil.post(url, body, headers);
		final ObjectNode response = mapper.readValue(resp, ObjectNode.class);
		
		// check for errors
		if (response.has("error")) {
			final ObjectNode error = (ObjectNode) response.get("error");
			throw new JSONRPCException(error);
		}
		
		// get items from the response
		ArrayNode items = null;
		if (response.has("calendars")) {
			final JsonNode calendars = response.get("calendars");
			if (calendars.has(calendarId)) {
				final JsonNode calendar = calendars.get(calendarId);
				if (calendar.has("busy")) {
					items = (ArrayNode) calendar.get("busy");
				}
			}
		}
		
		if (items == null) {
			items = JOM.createArrayNode();
		}
		
		return items;
	}
	
	/**
	 * Get a single event by id.
	 * 
	 * @param eventId
	 *            Id of the event
	 * @param calendarId
	 *            Optional calendar id. the primary calendar is used by default
	 * @return the event
	 * @throws Exception
	 *             the exception
	 */
	@Override
	public ObjectNode getEvent(@Name("eventId") final String eventId,
			@Optional @Name("calendarId") String calendarId) throws Exception {
		// initialize optional parameters
		if (calendarId == null) {
			calendarId = getState().get("email", String.class);
		}
		
		// built url
		final String url = CALENDAR_URI + calendarId + "/events/" + eventId;
		
		// perform GET request
		final Map<String, String> headers = getAuthorizationHeaders();
		final String resp = HttpUtil.get(url, headers);
		final ObjectMapper mapper = JOM.getInstance();
		final ObjectNode event = mapper.readValue(resp, ObjectNode.class);
		
		// convert from Google to Eve event
		toEveEvent(event);
		
		// check for errors
		if (event.has("error")) {
			final ObjectNode error = (ObjectNode) event.get("error");
			final Integer code = error.has("code") ? error.get("code").asInt()
					: null;
			if (code != null && (code.equals(404) || code.equals(410))) {
				throw new JSONRPCException(CODE.NOT_FOUND);
			}
			
			throw new JSONRPCException(error);
		}
		
		// check if canceled. If so, return null
		// TODO: be able to retrieve canceled events?
		if (event.has("status")
				&& event.get("status").asText().equals("cancelled")) {
			throw new JSONRPCException(CODE.NOT_FOUND);
		}
		
		return event;
	}
	
	/**
	 * Create an event.
	 * 
	 * @param event
	 *            JSON structure containing the calendar event
	 * @param calendarId
	 *            Optional calendar id. the primary calendar is used by default
	 * @return createdEvent JSON structure with the created event
	 * @throws Exception
	 *             the exception
	 */
	@Override
	public ObjectNode createEvent(@Name("event") final ObjectNode event,
			@Optional @Name("calendarId") String calendarId) throws Exception {
		// initialize optional parameters
		if (calendarId == null) {
			calendarId = getState().get("email", String.class);
		}
		
		// built url
		final String url = CALENDAR_URI + calendarId + "/events";
		
		// convert from Google to Eve event
		toGoogleEvent(event);
		
		// perform POST request
		final ObjectMapper mapper = JOM.getInstance();
		final String body = mapper.writeValueAsString(event);
		final Map<String, String> headers = getAuthorizationHeaders();
		headers.put("Content-Type", "application/json");
		final String resp = HttpUtil.post(url, body, headers);
		final ObjectNode createdEvent = mapper
				.readValue(resp, ObjectNode.class);
		
		// convert from Google to Eve event
		toEveEvent(event);
		
		// check for errors
		if (createdEvent.has("error")) {
			final ObjectNode error = (ObjectNode) createdEvent.get("error");
			throw new JSONRPCException(error);
		}
		
		return createdEvent;
	}
	
	/**
	 * Quick create an event.
	 * 
	 * @param start
	 *            the start
	 * @param end
	 *            the end
	 * @param summary
	 *            the summary
	 * @param location
	 *            the location
	 * @param calendarId
	 *            the calendar id
	 * @return the object node
	 * @throws Exception
	 *             the exception
	 */
	public ObjectNode createEventQuick(@Optional @Name("start") String start,
			@Optional @Name("end") String end,
			@Optional @Name("summary") final String summary,
			@Optional @Name("location") final String location,
			@Optional @Name("calendarId") final String calendarId)
			throws Exception {
		final ObjectNode event = JOM.createObjectNode();
		
		if (start == null) {
			// set start to current time, rounded to hours
			DateTime startDate = DateTime.now();
			startDate = startDate.plusHours(1);
			startDate = startDate.minusMinutes(startDate.getMinuteOfHour());
			startDate = startDate.minusSeconds(startDate.getSecondOfMinute());
			startDate = startDate.minusMillis(startDate.getMillisOfSecond());
			start = startDate.toString();
		}
		final ObjectNode startObj = JOM.createObjectNode();
		startObj.put("dateTime", start);
		event.put("start", startObj);
		if (end == null) {
			// set end to start +1 hour
			final DateTime startDate = new DateTime(start);
			final DateTime endDate = startDate.plusHours(1);
			end = endDate.toString();
		}
		final ObjectNode endObj = JOM.createObjectNode();
		endObj.put("dateTime", end);
		event.put("end", endObj);
		if (summary != null) {
			event.put("summary", summary);
		}
		if (location != null) {
			event.put("location", location);
		}
		
		return createEvent(event, calendarId);
	}
	
	/**
	 * Update an existing event.
	 * 
	 * @param event
	 *            JSON structure containing the calendar event (event must have
	 *            an id)
	 * @param calendarId
	 *            Optional calendar id. the primary calendar is used by default
	 * @return updatedEvent JSON structure with the updated event
	 * @throws Exception
	 *             the exception
	 */
	@Override
	public ObjectNode updateEvent(@Name("event") final ObjectNode event,
			@Optional @Name("calendarId") String calendarId) throws Exception {
		// initialize optional parameters
		if (calendarId == null) {
			calendarId = getState().get("email", String.class);
		}
		
		// convert from Eve to Google event
		toGoogleEvent(event);
		
		// read id from event
		final String id = event.get("id").asText();
		if (id == null) {
			throw new Exception("Parameter 'id' missing in event");
		}
		
		// built url
		final String url = CALENDAR_URI + calendarId + "/events/" + id;
		
		// perform POST request
		final ObjectMapper mapper = JOM.getInstance();
		final String body = mapper.writeValueAsString(event);
		final Map<String, String> headers = getAuthorizationHeaders();
		headers.put("Content-Type", "application/json");
		final String resp = HttpUtil.put(url, body, headers);
		final ObjectNode updatedEvent = mapper
				.readValue(resp, ObjectNode.class);
		
		// check for errors
		if (updatedEvent.has("error")) {
			final ObjectNode error = (ObjectNode) updatedEvent.get("error");
			throw new JSONRPCException(error);
		}
		
		// convert from Google to Eve event
		toEveEvent(event);
		
		return updatedEvent;
	}
	
	/**
	 * Delete an existing event.
	 * 
	 * @param eventId
	 *            id of the event to be deleted
	 * @param calendarId
	 *            Optional calendar id. the primary calendar is used by default
	 * @throws Exception
	 *             the exception
	 */
	@Override
	public void deleteEvent(@Name("eventId") final String eventId,
			@Optional @Name("calendarId") String calendarId) throws Exception {
		// initialize optional parameters
		if (calendarId == null) {
			calendarId = getState().get("email", String.class);
		}
		
		// built url
		final String url = CALENDAR_URI + calendarId + "/events/" + eventId;
		
		// perform POST request
		final Map<String, String> headers = getAuthorizationHeaders();
		final String resp = HttpUtil.delete(url, headers);
		if (!resp.isEmpty()) {
			final ObjectNode node = JOM.getInstance().readValue(resp,
					ObjectNode.class);
			
			// check error code
			if (node.has("error")) {
				final ObjectNode error = (ObjectNode) node.get("error");
				final Integer code = error.has("code") ? error.get("code")
						.asInt() : null;
				if (code != null && (code.equals(404) || code.equals(410))) {
					throw new JSONRPCException(CODE.NOT_FOUND);
				}
				
				throw new JSONRPCException(error);
			} else {
				throw new Exception(resp);
			}
		}
	}
}
