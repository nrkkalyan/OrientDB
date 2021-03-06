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
package com.orientechnologies.orient.core.db.object;

import java.util.ArrayList;
import java.util.List;

import com.orientechnologies.common.log.OLogManager;
import com.orientechnologies.orient.core.config.OGlobalConfiguration;
import com.orientechnologies.orient.core.db.ODatabase;
import com.orientechnologies.orient.core.db.ODatabasePojoAbstract;
import com.orientechnologies.orient.core.db.OUserObject2RecordHandler;
import com.orientechnologies.orient.core.db.document.ODatabaseDocument;
import com.orientechnologies.orient.core.db.document.ODatabaseDocumentTx;
import com.orientechnologies.orient.core.db.record.ODatabaseRecordAbstract;
import com.orientechnologies.orient.core.dictionary.ODictionary;
import com.orientechnologies.orient.core.dictionary.ODictionaryWrapper;
import com.orientechnologies.orient.core.entity.OEntityManager;
import com.orientechnologies.orient.core.exception.ODatabaseException;
import com.orientechnologies.orient.core.id.ORID;
import com.orientechnologies.orient.core.iterator.OObjectIteratorCluster;
import com.orientechnologies.orient.core.iterator.OObjectIteratorMultiCluster;
import com.orientechnologies.orient.core.metadata.security.ODatabaseSecurityResources;
import com.orientechnologies.orient.core.metadata.security.ORole;
import com.orientechnologies.orient.core.record.ORecordSchemaAware;
import com.orientechnologies.orient.core.record.impl.ODocument;
import com.orientechnologies.orient.core.serialization.serializer.object.OObjectSerializerHelper;
import com.orientechnologies.orient.core.serialization.serializer.record.OSerializationThreadLocal;
import com.orientechnologies.orient.core.tx.OTransactionEntry;
import com.orientechnologies.orient.core.tx.OTransactionNoTx;

/**
 * Object Database instance. It's a wrapper to the class ODatabaseDocumentTx but handle the conversion between ODocument instances
 * and POJOs.
 * 
 * @see ODatabaseDocumentTx
 * @author Luca Garulli
 */
@SuppressWarnings("unchecked")
public class ODatabaseObjectTx extends ODatabasePojoAbstract<ODocument, Object> implements ODatabaseObject,
		OUserObject2RecordHandler {

	private ODictionary<Object>	dictionary;
	private OEntityManager			entityManager;
	private boolean							saveOnlyDirty;

	public ODatabaseObjectTx(final String iURL) {
		super(new ODatabaseDocumentTx(iURL));
		underlying.setDatabaseOwner(this);
		entityManager = OEntityManager.getEntityManagerByDatabaseURL(iURL);
		saveOnlyDirty = OGlobalConfiguration.OBJECT_SAVE_ONLY_DIRTY.getValueAsBoolean();
	}

	public <T> T newInstance(final Class<T> iType) {
		return (T) newInstance(iType.getName());
	}

	/**
	 * Create a new POJO by its class name. Assure to have called the registerEntityClasses() declaring the packages that are part of
	 * entity classes.
	 * 
	 * @see #registerEntityClasses(String)
	 */
	public Object newInstance(final String iClassName) {
		checkOpeness();
		checkSecurity(ODatabaseSecurityResources.CLASS, ORole.PERMISSION_CREATE, iClassName);

		try {
			return entityManager.createPojo(iClassName);
		} catch (Exception e) {
			OLogManager.instance().error(this, "Error on creating object of class " + iClassName, e, ODatabaseException.class);
		}
		return null;
	}

	public <RET> OObjectIteratorMultiCluster<RET> browseClass(final Class<RET> iClusterClass) {
		return browseClass(iClusterClass, true);
	}

	public <RET> OObjectIteratorMultiCluster<RET> browseClass(final Class<RET> iClusterClass, final boolean iPolymorphic) {
		if (iClusterClass == null)
			return null;

		return browseClass(iClusterClass.getSimpleName(), iPolymorphic);
	}

	public <RET> OObjectIteratorMultiCluster<RET> browseClass(final String iClassName) {
		return browseClass(iClassName, true);
	}

	public <RET> OObjectIteratorMultiCluster<RET> browseClass(final String iClassName, final boolean iPolymorphic) {
		checkOpeness();
		checkSecurity(ODatabaseSecurityResources.CLASS, ORole.PERMISSION_READ, iClassName);

		return new OObjectIteratorMultiCluster<RET>(this, (ODatabaseRecordAbstract<ODocument>) getUnderlying().getUnderlying(),
				iClassName, iPolymorphic);
	}

	public <RET> OObjectIteratorCluster<RET> browseCluster(final String iClusterName) {
		checkOpeness();
		checkSecurity(ODatabaseSecurityResources.CLUSTER, ORole.PERMISSION_READ, iClusterName);

		return (OObjectIteratorCluster<RET>) new OObjectIteratorCluster<Object>(this,
				(ODatabaseRecordAbstract<ODocument>) getUnderlying().getUnderlying(), getClusterIdByName(iClusterName));
	}

	public ODatabaseObjectTx load(final Object iPojo) {
		return load(iPojo, null);
	}

	public ODatabaseObjectTx load(final Object iPojo, final String iFetchPlan) {
		checkOpeness();
		if (iPojo == null)
			return this;

		// GET THE ASSOCIATED DOCUMENT
		final ODocument record = getRecordByUserObject(iPojo, true);
		underlying.load(record);

		stream2pojo(record, iPojo, iFetchPlan);

		return this;
	}

	public Object load(final ORID iRecordId) {
		return load(iRecordId, null);
	}

	public Object load(final ORID iRecordId, final String iFetchPlan) {
		checkOpeness();
		if (iRecordId == null)
			return null;

		ODocument record = rid2Records.get(iRecordId);
		if (record == null) {
			// GET THE ASSOCIATED DOCUMENT
			record = underlying.load(iRecordId);
			if (record == null)
				return null;
		}

		Object result = records2Objects.get(record);
		if (result != null)
			// FOUND: JUST RETURN IT
			return result;

		result = stream2pojo(record, newInstance(record.getClassName()), iFetchPlan);
		registerPojo(result, record);
		return result;
	}

	/**
	 * If the record is new and a class was specified, the configured cluster id will be used to store the class.
	 */
	public ODatabaseObject save(final Object iContent) {
		return save(iContent, null);
	}

	/**
	 * Store the record on the specified cluster only after having checked the cluster is allowed and figures in the configured and
	 * the record is valid following the constraints declared in the schema.
	 * 
	 * @see ORecordSchemaAware#validate()
	 */
	public ODatabaseObject save(final Object iPojo, final String iClusterName) {
		checkOpeness();

		if (iPojo == null)
			return this;

		OSerializationThreadLocal.INSTANCE.get().clear();

		// GET THE ASSOCIATED DOCUMENT
		ODocument record = objects2Records.get(System.identityHashCode(iPojo));
		if (record == null)
			record = underlying.newInstance(iPojo.getClass().getSimpleName());

		// REGISTER BEFORE TO SERIALIZE TO AVOID PROBLEMS WITH CIRCULAR DEPENDENCY
		registerPojo(iPojo, record);

		pojo2Stream(iPojo, record);

		underlying.save(record, iClusterName);

		// RE-REGISTER FOR NEW RECORDS SINCE THE ID HAS CHANGED
		registerPojo(iPojo, record);

		return this;
	}

	public ODatabaseObject delete(final Object iContent) {
		checkOpeness();

		if (iContent == null)
			return this;

		ODocument record = getRecordByUserObject(iContent, false);
		if (record == null)
			record = (ODocument) underlying.load(OObjectSerializerHelper.getObjectID(this, iContent));

		underlying.delete(record);

		if (getTransaction() instanceof OTransactionNoTx)
			unregisterPojo(iContent, record);

		return this;
	}

	public long countClass(final String iClassName) {
		checkOpeness();
		return underlying.countClass(iClassName);
	}

	public long countClass(final Class<?> iClass) {
		checkOpeness();
		return underlying.countClass(iClass.getSimpleName());
	}

	public ODictionary<Object> getDictionary() {
		checkOpeness();
		if (dictionary == null)
			dictionary = new ODictionaryWrapper(this, underlying);

		return dictionary;
	}

	@Override
	public ODatabasePojoAbstract<ODocument, Object> commit() {
		// COPY ALL TX ENTRIES
		final List<OTransactionEntry<?>> entries;
		if (getTransaction().getEntries() != null) {
			entries = new ArrayList<OTransactionEntry<?>>();
			for (OTransactionEntry<?> entry : getTransaction().getEntries())
				entries.add(entry);
		} else
			entries = null;

		underlying.commit();

		if (entries != null) {
			// UPDATE ID & VERSION FOR ALL THE RECORDS
			Object pojo = null;
			for (OTransactionEntry<?> entry : entries) {
				pojo = records2Objects.get(entry.getRecord());

				switch (entry.status) {
				case OTransactionEntry.CREATED:
					rid2Records.put(entry.getRecord().getIdentity(), (ODocument) entry.getRecord());
					OObjectSerializerHelper.setObjectID(entry.getRecord().getIdentity(), pojo);

				case OTransactionEntry.UPDATED:
					OObjectSerializerHelper.setObjectVersion(entry.getRecord().getVersion(), pojo);
					break;

				case OTransactionEntry.DELETED:
					OObjectSerializerHelper.setObjectID(null, pojo);
					OObjectSerializerHelper.setObjectVersion(null, pojo);

					unregisterPojo(pojo, (ODocument) entry.getRecord());
					break;
				}
			}
		}

		return this;
	}

	@Override
	public ODatabasePojoAbstract<ODocument, Object> rollback() {
		// COPY ALL TX ENTRIES
		final List<OTransactionEntry<?>> newEntries;
		if (getTransaction().getEntries() != null) {
			newEntries = new ArrayList<OTransactionEntry<?>>();
			for (OTransactionEntry<?> entry : getTransaction().getEntries())
				if (entry.status == OTransactionEntry.CREATED)
					newEntries.add(entry);
		} else
			newEntries = null;

		underlying.rollback();

		if (newEntries != null) {
			Object pojo = null;
			for (OTransactionEntry<?> entry : newEntries) {
				pojo = records2Objects.get(entry.getRecord());

				OObjectSerializerHelper.setObjectID(null, pojo);
				OObjectSerializerHelper.setObjectVersion(null, pojo);
			}
		}

		objects2Records.clear();
		records2Objects.clear();
		rid2Records.clear();

		return this;
	}

	public OEntityManager getEntityManager() {
		return entityManager;
	}

	@Override
	public ODatabaseDocument getUnderlying() {
		return underlying;
	}

	/**
	 * Returns the version number of the object. Version starts from 0 assigned on creation.
	 * 
	 * @param iPojo
	 *          User object
	 */
	@Override
	public int getVersion(final Object iPojo) {
		checkOpeness();
		final ODocument record = getRecordByUserObject(iPojo, false);
		if (record != null)
			return record.getVersion();

		return OObjectSerializerHelper.getObjectVersion(iPojo);
	}

	/**
	 * Returns the object unique identity.
	 * 
	 * @param iPojo
	 *          User object
	 */
	public ORID getIdentity(final Object iPojo) {
		checkOpeness();
		final ODocument record = getRecordByUserObject(iPojo, false);
		if (record != null)
			return record.getIdentity();
		return OObjectSerializerHelper.getObjectID(this, iPojo);
	}

	public Object newInstance() {
		checkOpeness();
		return new ODocument(underlying);
	}

	public <DBTYPE extends ODatabase> DBTYPE checkSecurity(final String iResource, final byte iOperation) {
		return (DBTYPE) underlying.checkSecurity(iResource, iOperation);
	}

	public <DBTYPE extends ODatabase> DBTYPE checkSecurity(final String iResource, final int iOperation, Object... iResourcesSpecific) {
		return (DBTYPE) underlying.checkSecurity(iResource, iOperation, iResourcesSpecific);
	}

	protected ODocument pojo2Stream(final Object iPojo, final ODocument iRecord) {
		return OObjectSerializerHelper.toStream(iPojo, iRecord, getEntityManager(),
				getMetadata().getSchema().getClass(iPojo.getClass().getSimpleName()), this, this, saveOnlyDirty);
	}

	protected Object stream2pojo(final ODocument record, final Object iPojo, final String iFetchPlan) {
		return OObjectSerializerHelper.fromStream(record, iPojo, getEntityManager(), this, iFetchPlan);
	}

	public boolean isSaveOnlyDirty() {
		return saveOnlyDirty;
	}

	public void setSaveOnlyDirty(boolean saveOnlyDirty) {
		this.saveOnlyDirty = saveOnlyDirty;
	}
}
