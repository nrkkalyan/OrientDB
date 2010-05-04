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
package com.orientechnologies.orient.core.command;

import com.orientechnologies.orient.core.db.record.ODatabaseRecord;

/**
 * Internal specialization of generic OCommand interface.
 * 
 * @author luca
 * 
 * @param <T>
 */
public interface OCommandRequestInternal<DB extends ODatabaseRecord<?>> extends OCommandRequest {
	public String getText();

	public DB getDatabase();

	public OCommandRequestInternal<DB> setDatabase(final DB iDatabase);
}