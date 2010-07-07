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

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map.Entry;

import com.orientechnologies.orient.core.db.record.ODatabaseRecord;
import com.orientechnologies.orient.core.id.ORecordId;
import com.orientechnologies.orient.core.record.impl.ORecordBytes;
import com.orientechnologies.orient.core.serialization.serializer.stream.OStreamSerializerListRID;
import com.orientechnologies.orient.core.serialization.serializer.stream.OStreamSerializerString;
import com.orientechnologies.orient.core.type.tree.OTreeMapDatabaseLazySave;

/**
 * Handles indexing when records change.
 * 
 * @author Luca Garulli
 * 
 */
public class OIndex {
	private OTreeMapDatabaseLazySave<String, List<ORecordId>>	map;
	private boolean																						unique;

	public OIndex(final boolean iUnique, final ODatabaseRecord<?> iDatabase, final String iClusterIndexName, final ORecordId iRecordId) {
		unique = iUnique;
		map = new OTreeMapDatabaseLazySave<String, List<ORecordId>>(iDatabase, iClusterIndexName, iRecordId);
	}

	public OIndex(boolean iUnique, ODatabaseRecord<?> iDatabase, String iClusterIndexName) {
		unique = iUnique;
		map = new OTreeMapDatabaseLazySave<String, List<ORecordId>>(iDatabase, iClusterIndexName, OStreamSerializerString.INSTANCE,
				OStreamSerializerListRID.INSTANCE);
	}

	public void setUnique(boolean iUnique) {
		if (iUnique == unique)
			return;

		if (iUnique) {
			// CHECK FOR DUPLICATES
			List<ORecordId> values;
			for (Entry<String, List<ORecordId>> entry : map.entrySet()) {
				values = entry.getValue();
				if (values != null && values.size() > 1)
					throw new OIndexException(
							"Can't change the index from unique to not-unique since the index contains multiple values with the same key ('"
									+ entry.getKey() + "'). Remove duplicates and try again.");
			}
		}

		unique = iUnique;
	}

	public void load() throws IOException {
		map.load();
	}

	public void clear() {
		map.clear();
	}

	public void lazySave() {
		map.lazySave();
	}

	public void remove(Object key) {
		map.remove(key);
	}

	public void put(final String iKey, final ORecordId iSingleValue) {
		List<ORecordId> values = map.get(iKey);
		if (values == null)
			values = new ArrayList<ORecordId>();

		if (unique) {
			// UNIQUE
			if (values.size() > 0)
				values.set(0, iSingleValue);
			else
				values.add(iSingleValue);
		} else {
			// NOT UNIQUE
			int pos = values.indexOf(iSingleValue);
			if (pos > -1)
				// REPLACE IT
				values.set(pos, iSingleValue);
			else
				values.add(iSingleValue);
		}

		map.put(iKey, values);
	}

	@SuppressWarnings("unchecked")
	public List<ORecordId> get(String iKey) {
		final List<ORecordId> values = map.get(iKey);

		if (values == null)
			return Collections.EMPTY_LIST;

		return values;
	}

	public ORecordBytes getRecord() {
		return map.getRecord();
	}

	public boolean isUnique() {
		return unique;
	}
}