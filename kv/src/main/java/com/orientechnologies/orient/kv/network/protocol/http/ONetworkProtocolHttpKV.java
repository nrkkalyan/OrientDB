/*
 * Copyright 1999-2010 Luca Garulli (l.garulli--at--orientechnologies.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.orientechnologies.orient.kv.network.protocol.http;

import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.util.Map;

import com.orientechnologies.common.log.OLogManager;
import com.orientechnologies.orient.enterprise.channel.text.OChannelTextServer;
import com.orientechnologies.orient.server.network.protocol.ONetworkProtocolException;
import com.orientechnologies.orient.server.network.protocol.http.OHttpUtils;
import com.orientechnologies.orient.server.network.protocol.http.ONetworkProtocolHttpAbstract;

public abstract class ONetworkProtocolHttpKV extends ONetworkProtocolHttpAbstract {
	protected abstract Map<String, String> getBucket(String dbName, String bucket);

	protected abstract String getKey(String key);

	@Override
	public void doGet(final String iURI, final String iContent, final OChannelTextServer iChannel) throws ONetworkProtocolException {
		if (OHttpUtils.URL_SEPARATOR.equals(iURI) || iURI.startsWith("/www")) {
			directAccess(iURI);
			return;
		}

		final String parts[] = getDbBucketKey(iURI, 2);

		final String dbName = parts[0];
		final String bucket = parts[1];
		final String key = getKey(iURI);

		try {
			String value;

			// SEARCH THE BUCKET
			final Map<String, String> bucketMap = getBucket(dbName, bucket);
			if (key != null)
				// SEARCH THE KEY
				value = bucketMap.get(key);
			else {
				// BROWSE ALL THE KEYS
				final StringBuilder buffer = new StringBuilder();
				for (String k : bucketMap.keySet()) {
					buffer.append(k + "\n");
				}
				value = buffer.toString();
			}

			final int code = value == null ? 404 : OHttpUtils.STATUS_OK_CODE;
			final String reason = value == null ? "Not Found" : OHttpUtils.STATUS_OK_DESCRIPTION;
			final String content = value == null ? "The key '" + key + "' was not found in database '" + dbName + "'" : value.toString();

			sendTextContent(code, reason, "text/plain", content);
		} catch (SocketException e) {
			connectionError();

		} catch (SocketTimeoutException e) {
			timeout();

		} catch (Exception e) {
			OLogManager.instance().error(this, "Error on retrieving key '" + key + "' from database '" + dbName + "'", e,
					ONetworkProtocolException.class);
		}
	}

	@Override
	public void doPut(final String iURI, final String iContent, final OChannelTextServer iChannel) throws ONetworkProtocolException {
		final String parts[] = getDbBucketKey(iURI, 3);

		final String dbName = parts[0];
		final String bucket = parts[1];
		final String key = getKey(iURI);

		try {
			final Map<String, String> bucketMap = getBucket(dbName, bucket);

			final int code;
			final String reason;
			final String content;

			if (bucketMap.containsKey(key)) {
				code = OHttpUtils.STATUS_OK_CODE;
				reason = OHttpUtils.STATUS_OK_DESCRIPTION;
				content = null;

				bucketMap.put(key, iContent);
			} else {
				code = 503;
				reason = "Entry not exists. Use HTTP POST instead.";
				content = "The entry with key: " + key + " not exists in the bucket '" + bucket + "'";
			}

			sendTextContent(code, reason, "text/plain", content);

		} catch (SocketTimeoutException e) {
			timeout();

		} catch (SocketException e) {
			connectionError();

		} catch (Exception e) {
			OLogManager.instance().error(this, "Error on retrieving key '" + key + "' from database '" + dbName + "'", e,
					ONetworkProtocolException.class);
		}
	}

	@Override
	public void doPost(final String iURI, final String iContent, final OChannelTextServer iChannel) throws ONetworkProtocolException {
		final String parts[] = getDbBucketKey(iURI, 3);

		final String dbName = parts[0];
		final String bucket = parts[1];
		final String key = getKey(iURI);

		try {
			Map<String, String> bucketMap = getBucket(dbName, bucket);

			final int code;
			final String reason;
			final String content;

			if (bucketMap.containsKey(key)) {
				code = 503;
				reason = "Entry already exists";
				content = "The entry with key: " + key + " already exists in the bucket '" + bucket + "'";
			} else {
				code = OHttpUtils.STATUS_OK_CODE;
				reason = OHttpUtils.STATUS_OK_DESCRIPTION;
				content = null;

				bucketMap.put(key, iContent);
			}

			sendTextContent(code, reason, "text/plain", content);
		} catch (SocketTimeoutException e) {
			timeout();

		} catch (SocketException e) {
			connectionError();

		} catch (Exception e) {
			OLogManager.instance().error(this, "Error on retrieving key '" + key + "' from database '" + dbName + "'", e,
					ONetworkProtocolException.class);
		}
	}

	@Override
	public void doDelete(String iURI, String iContent, OChannelTextServer iChannel) throws ONetworkProtocolException {
		final String parts[] = getDbBucketKey(iURI, 3);

		final String dbName = parts[0];
		final String bucket = parts[1];
		final String key = getKey(iURI);

		try {
			final Map<String, String> bucketMap = getBucket(dbName, bucket);

			final int code;
			final String reason;
			final String content;

			if (!bucketMap.containsKey(key)) {
				code = 503;
				reason = "Key not found";
				content = "The entry with key: " + key + " was not found in the bucket '" + bucket + "'";
			} else {
				code = OHttpUtils.STATUS_OK_CODE;
				reason = OHttpUtils.STATUS_OK_DESCRIPTION;
				content = bucketMap.remove(key);
			}

			sendTextContent(code, reason, OHttpUtils.CONTENT_TEXT_PLAIN, content);
		} catch (SocketTimeoutException e) {
			timeout();

		} catch (SocketException e) {
			connectionError();

		} catch (Exception e) {
			OLogManager.instance().error(this, "Error on retrieving key '" + key + "' from database '" + dbName + "'", e,
					ONetworkProtocolException.class);
		}
	}

	public static String[] getDbBucketKey(String iParameters, final int iMin) {
		if (iParameters == null || iParameters.length() < 5)
			throw new ONetworkProtocolException("Requested URI '" + iParameters + "' is invalid. Expected db/bucket/key");

		// REMOVE THE FIRST /
		if (iParameters.startsWith(OHttpUtils.URL_SEPARATOR))
			iParameters = iParameters.substring(1);

		if (iParameters.endsWith(OHttpUtils.URL_SEPARATOR))
			iParameters = iParameters.substring(0, iParameters.length() - 1);

		final String[] pars = iParameters.split(OHttpUtils.URL_SEPARATOR);

		if (pars == null || pars.length < iMin)
			throw new ONetworkProtocolException("Requested URI '" + iParameters + "' is invalid. Expected db/bucket[/key]");

		return pars;
	}
}
