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
package com.orientechnologies.orient.core.metadata.schema;

import java.lang.reflect.Array;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.orientechnologies.orient.core.record.ORecord;

/**
 * Generic representation of a type.
 * 
 * @author Luca Garulli
 * 
 */
public enum OType {
	BOOLEAN("Boolean", 0, false, true, 1, new Class<?>[] { Boolean.TYPE, Boolean.class }, new Class<?>[] { Boolean.class }) {
	},
	INTEGER("Integer", 1, false, true, 4, new Class<?>[] { Integer.TYPE, Integer.class }, new Class<?>[] { Number.class }) {
	},
	SHORT("Short", 2, false, true, 2, new Class<?>[] { Short.TYPE, Short.class }, new Class<?>[] { Number.class }) {
	},
	LONG("Long", 3, false, true, 8, new Class<?>[] { Long.TYPE, Long.class }, new Class<?>[] { Number.class }) {
	},
	FLOAT("Float", 4, false, true, 4, new Class<?>[] { Float.TYPE, Float.class }, new Class<?>[] { Number.class }) {
	},
	DOUBLE("Double", 5, false, true, 8, new Class<?>[] { Double.TYPE, Double.class }, new Class<?>[] { Number.class }) {
	},
	DATE("Date", 6, false, true, 8, new Class<?>[] { Date.class }, new Class<?>[] { Date.class }) {
	},
	STRING("String", 7, false, false, 8, new Class<?>[] { String.class }, new Class<?>[] { String.class }) {
	},
	BINARY("Binary", 8, false, false, 8, new Class<?>[] { Byte.TYPE, Byte.class }, new Class<?>[] { Array.class }) {
	},
	EMBEDDED("Embedded", 9, true, false, 8, new Class<?>[] { Object.class }, new Class<?>[] {}) {
	},
	EMBEDDEDLIST("List", 10, true, false, 8, new Class<?>[] { List.class }, new Class<?>[] { Collection.class }) {
	},
	EMBEDDEDSET("Set", 11, true, false, 8, new Class<?>[] { Set.class }, new Class<?>[] { Collection.class }) {
	},
	EMBEDDEDMAP("Map", 12, true, false, 8, new Class<?>[] { Map.class }, new Class<?>[] { Map.class }) {
	},
	LINK("Link", 13, true, true, 8, new Class<?>[] { Object.class }, new Class<?>[] { ORecord.class }) {
	},
	LINKLIST("List", 14, true, false, 8, new Class<?>[] { List.class }, new Class<?>[] { Collection.class }) {
	},
	LINKSET("Set", 15, true, false, 8, new Class<?>[] { Set.class }, new Class<?>[] { Collection.class }) {
	},
	LINKMAP("Map", 16, true, false, 8, new Class<?>[] { Map.class }, new Class<?>[] { Map.class }) {
	};

	protected static final OType[]	TYPES	= new OType[] { BOOLEAN, INTEGER, SHORT, LONG, FLOAT, DOUBLE, DATE, STRING, BINARY,
			EMBEDDED, EMBEDDEDLIST, EMBEDDEDSET, EMBEDDEDMAP, LINK, LINKLIST, LINKSET, LINKMAP };

	protected String								name;
	protected int										id;
	protected boolean								complex;
	protected boolean								fixedSize;
	protected int										size;
	protected Class<?>[]						javaTypes;
	protected Class<?>[]						allowAssignmentFrom;

	private OType(final String iName, final int iId, final boolean iComplex, final boolean iFixedSize, final int iSize,
			final Class<?>[] iJavaTypes, final Class<?>[] iAllowAssignmentBy) {
		name = iName;
		id = iId;
		complex = iComplex;
		fixedSize = iFixedSize;
		size = iSize;
		javaTypes = iJavaTypes;
		allowAssignmentFrom = iAllowAssignmentBy;
	}

	/**
	 * Return the type by ID.
	 * 
	 * @param iId
	 *          The id to search
	 * @return The type if any, otherwise null
	 */
	public static OType getById(final int iId) {
		for (OType t : TYPES) {
			if (iId == t.id)
				return t;
		}
		return null;
	}

	/**
	 * Check if the value is assignable by the current type
	 * 
	 * @param iPropertyValue
	 *          Object to check
	 * @return true if it's assignable, otherwise false
	 */
	public boolean isAssignableFrom(final Object iPropertyValue) {
		if (iPropertyValue == null)
			return true;

		for (int i = 0; i < allowAssignmentFrom.length; ++i) {
			if (allowAssignmentFrom[i].isAssignableFrom(iPropertyValue.getClass()))
				return true;
		}
		return false;
	}

	/**
	 * Return the correspondent type by checking the "assignability" of the class received as parameter.
	 * 
	 * @param iClass
	 *          Class to check
	 * @return OType instance if found, otherwise null
	 */
	public static OType getTypeByClass(final Class<?> iClass) {
		if (iClass == null)
			return null;

		for (OType type : TYPES)
			for (int i = 0; i < type.javaTypes.length; ++i) {
				if (type.javaTypes[i] == iClass)
					return type;
			}

		return null;
	}

	/**
	 * Return the correspondent type by checking the "assignability" of the class received as parameter.
	 * 
	 * @param iClass
	 *          Class to check
	 * @return OType instance if found, otherwise null
	 */
	public static OType getTypeByAssignability(final Class<?> iClass) {
		if (iClass == null)
			return null;

		for (OType type : TYPES)
			for (int i = 0; i < type.allowAssignmentFrom.length; ++i) {
				if (type.allowAssignmentFrom[i].isAssignableFrom(iClass))
					return type;
			}

		return null;
	}

	public boolean isComplex() {
		return complex;
	}

	/**
	 * Convert the input object to an integer.
	 * 
	 * @param iValue
	 *          Any type supported
	 * @return The integer value if the conversion succeed, otherwise the IllegalArgumentException exception
	 */
	public int asInt(final Object iValue) {
		if (iValue instanceof Integer)
			return ((Integer) iValue).intValue();
		else if (iValue instanceof Long)
			return ((Long) iValue).intValue();
		else if (iValue instanceof String)
			return Integer.valueOf((String) iValue);
		else if (iValue instanceof Float)
			return ((Float) iValue).intValue();
		else if (iValue instanceof Double)
			return ((Double) iValue).intValue();
		else if (iValue instanceof Boolean)
			return ((Boolean) iValue) ? 1 : 0;

		throw new IllegalArgumentException("Can't convert value " + iValue + " to int for the type: " + name);
	}

	/**
	 * Convert the input object to a long.
	 * 
	 * @param iValue
	 *          Any type supported
	 * @return The long value if the conversion succeed, otherwise the IllegalArgumentException exception
	 */
	public long asLong(final Object iValue) {
		if (iValue instanceof Long)
			return ((Long) iValue).longValue();
		else if (iValue instanceof Integer)
			return ((Integer) iValue).longValue();
		else if (iValue instanceof String)
			return Long.valueOf((String) iValue);
		else if (iValue instanceof Float)
			return ((Float) iValue).longValue();
		else if (iValue instanceof Double)
			return ((Double) iValue).longValue();
		else if (iValue instanceof Boolean)
			return ((Boolean) iValue) ? 1 : 0;

		throw new IllegalArgumentException("Can't convert value " + iValue + " to long for the type: " + name);
	}

	/**
	 * Convert the input object to a float.
	 * 
	 * @param iValue
	 *          Any type supported
	 * @return The float value if the conversion succeed, otherwise the IllegalArgumentException exception
	 */
	public float asFloat(final Object iValue) {
		if (iValue instanceof Float)
			return ((Float) iValue).intValue();
		else if (iValue instanceof String)
			return Float.valueOf((String) iValue);
		else if (iValue instanceof Integer)
			return ((Integer) iValue).floatValue();
		else if (iValue instanceof Double)
			return ((Double) iValue).floatValue();
		else if (iValue instanceof Long)
			return ((Long) iValue).floatValue();

		throw new IllegalArgumentException("Can't convert value " + iValue + " to float for the type: " + name);
	}

	/**
	 * Convert the input object to a double.
	 * 
	 * @param iValue
	 *          Any type supported
	 * @return The double value if the conversion succeed, otherwise the IllegalArgumentException exception
	 */
	public double asDouble(final Object iValue) {
		if (iValue instanceof Double)
			return ((Double) iValue).doubleValue();
		else if (iValue instanceof String)
			return Double.valueOf((String) iValue);
		else if (iValue instanceof Float)
			return ((Float) iValue).doubleValue();
		else if (iValue instanceof Integer)
			return ((Integer) iValue).doubleValue();
		else if (iValue instanceof Long)
			return ((Long) iValue).doubleValue();

		throw new IllegalArgumentException("Can't convert value " + iValue + " to double for the type: " + name);
	}

	/**
	 * Convert the input object to a string.
	 * 
	 * @param iValue
	 *          Any type supported
	 * @return The string if the conversion succeed, otherwise the IllegalArgumentException exception
	 */
	public String asString(final Object iValue) {
		return iValue.toString();
	}

	public static boolean isSimpleType(final Class<?> iType) {
		if (iType.isPrimitive() || Number.class.isAssignableFrom(iType) || String.class.isAssignableFrom(iType)
				|| Boolean.class.isAssignableFrom(iType) || Date.class.isAssignableFrom(iType))
			return true;
		return false;
	}

	public static boolean isMultiValue(final Class<?> iType) {
		return (iType.isArray() || Collection.class.isAssignableFrom(iType) || Map.class.isAssignableFrom(iType));
	}
}
