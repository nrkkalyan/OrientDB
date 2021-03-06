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
package com.orientechnologies.orient.core.index;

import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map.Entry;

import com.orientechnologies.common.concur.resource.OSharedResource;
import com.orientechnologies.common.listener.OProgressListener;
import com.orientechnologies.orient.core.db.record.ODatabaseRecord;
import com.orientechnologies.orient.core.id.ORID;
import com.orientechnologies.orient.core.id.ORecordId;
import com.orientechnologies.orient.core.metadata.schema.OProperty;
import com.orientechnologies.orient.core.record.ORecord;
import com.orientechnologies.orient.core.record.impl.ODocument;
import com.orientechnologies.orient.core.record.impl.ORecordBytes;
import com.orientechnologies.orient.core.serialization.serializer.stream.OStreamSerializerListRID;
import com.orientechnologies.orient.core.serialization.serializer.stream.OStreamSerializerString;
import com.orientechnologies.orient.core.type.tree.OMVRBTreeDatabaseLazySave;

/**
 * Handles indexing when records change.
 * 
 * @author Luca Garulli
 * 
 */
public abstract class OPropertyIndexMVRBTreeAbstract extends OSharedResource implements OPropertyIndex {
	protected OProperty																						owner;
	protected OMVRBTreeDatabaseLazySave<String, List<ORecordId>>	map;

	public OPropertyIndexMVRBTreeAbstract() {
	}

	/**
	 * Constructor called when a new index is created.
	 * 
	 * @param iDatabase
	 *          Current Database instance
	 * @param iProperty
	 *          Owner property
	 * @param iClusterIndexName
	 *          Cluster name where to place the TreeMap
	 */
	public OPropertyIndexMVRBTreeAbstract(final ODatabaseRecord<?> iDatabase, final OProperty iProperty,
			final String iClusterIndexName) {
		owner = iProperty;
	}

	/**
	 * Creates the index.
	 * 
	 * @param iDatabase
	 *          Current Database instance
	 * @param iProperty
	 *          Owner property
	 * @param iClusterIndexName
	 *          Cluster name where to place the TreeMap
	 * @param iProgressListener
	 *          Listener to get called on progress
	 */
	public OPropertyIndex create(final ODatabaseRecord<?> iDatabase, final OProperty iProperty, final String iClusterIndexName,
			final OProgressListener iProgressListener) {
		owner = iProperty;
		map = new OMVRBTreeDatabaseLazySave<String, List<ORecordId>>(iDatabase, iClusterIndexName, OStreamSerializerString.INSTANCE,
				OStreamSerializerListRID.INSTANCE);
		rebuild(iProgressListener);
		return this;
	}

	public OPropertyIndex configure(final ODatabaseRecord<?> iDatabase, final OProperty iProperty, final ORID iRecordId) {
		owner = iProperty;
		init(iDatabase, iRecordId);
		return this;
	}

	@SuppressWarnings("unchecked")
	public List<ORecordId> get(Object iKey) {
		acquireSharedLock();

		try {
			final List<ORecordId> values = map.get(iKey);

			if (values == null)
				return Collections.EMPTY_LIST;

			return values;

		} finally {
			releaseSharedLock();
		}
	}

	public ORID getIdentity() {
		return map.getRecord().getIdentity();
	}

	public void rebuild() {
		rebuild(null);
	}

	/**
	 * Populate the index with all the existent records.
	 */
	public void rebuild(final OProgressListener iProgressListener) {
		Object fieldValue;
		ODocument doc;

		clear();

		acquireExclusiveLock();

		try {

			int documentIndexed = 0;
			int documentNum = 0;
			final int[] clusterIds = owner.getOwnerClass().getClusterIds();
			final long documentTotal = map.getDatabase().countClusterElements(clusterIds);

			if (iProgressListener != null)
				iProgressListener.onBegin(this, documentTotal);

			for (int clusterId : clusterIds)
				for (ORecord<?> record : map.getDatabase().browseCluster(map.getDatabase().getClusterNameById(clusterId))) {
					if (record instanceof ODocument) {
						doc = (ODocument) record;
						fieldValue = doc.field(owner.getName());

						if (fieldValue != null) {
							put(fieldValue.toString(), (ORecordId) doc.getIdentity());
							++documentIndexed;
						}
					}
					documentNum++;

					if (iProgressListener != null)
						iProgressListener.onProgress(this, documentNum, (float) documentNum * 100f / documentTotal);
				}

			lazySave();

			if (iProgressListener != null)
				iProgressListener.onCompletition(this, true);

		} catch (Exception e) {
			if (iProgressListener != null)
				iProgressListener.onCompletition(this, false);

			clear();

			throw new OIndexException("Error on rebuilding the index for property: " + owner, e);

		} finally {
			releaseExclusiveLock();
		}
	}

	public void remove(final Object key) {
		acquireSharedLock();

		try {
			map.remove(key);

		} finally {
			releaseSharedLock();
		}
	}

	public void load() {
		acquireExclusiveLock();

		try {
			map.load();

		} finally {
			releaseExclusiveLock();
		}
	}

	public void clear() {
		acquireExclusiveLock();

		try {
			map.clear();

		} finally {
			releaseExclusiveLock();
		}
	}

	public void delete() {
		clear();
		getRecord().delete();
	}

	public void lazySave() {
		acquireExclusiveLock();

		try {
			map.lazySave();

		} finally {
			releaseExclusiveLock();
		}
	}

	public ORecordBytes getRecord() {
		return map.getRecord();
	}

	public Iterator<Entry<String, List<ORecordId>>> iterator() {
		acquireSharedLock();

		try {
			return map.entrySet().iterator();

		} finally {
			releaseSharedLock();
		}
	}

	protected void init(final ODatabaseRecord<?> iDatabase, final ORID iRecordId) {
		map = new OMVRBTreeDatabaseLazySave<String, List<ORecordId>>(iDatabase, iRecordId);
		map.load();
	}

	public int getIndexedItems() {
		acquireSharedLock();

		try {
			return map.size();

		} finally {
			releaseSharedLock();
		}
	}

	@Override
	public String toString() {
		return getType().toString();
	}
}
