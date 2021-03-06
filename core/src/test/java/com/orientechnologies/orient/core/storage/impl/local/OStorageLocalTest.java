package com.orientechnologies.orient.core.storage.impl.local;

import java.io.File;

import org.testng.Assert;
import org.testng.annotations.Test;

import com.orientechnologies.orient.core.db.document.ODatabaseDocumentTx;

@Test
public class OStorageLocalTest {

	public void withLegacyPath() {
		String dbPath = getDatabasePath();

		System.out.println("Using db = local:" + dbPath);
		File dbDir = new File(dbPath);
		System.out.println("Clean db directory for test...");
		delTree(dbDir);
		ODatabaseDocumentTx db = new ODatabaseDocumentTx("local:" + dbPath);
		db.create();
		db.close();

		System.out.println("Reopen it...");
		// Something was added to dbPath so the legacy situation was simulated
		dbPath += "/foo";
		db = new ODatabaseDocumentTx("local:" + dbPath).open("admin", "admin");
		db.close();
		db.delete();
		Assert.assertTrue(true);
	}

	public void withNormalPath() {
		String dbPath = getDatabasePath();

		System.out.println("Using db = local:" + dbPath);
		File dbDir = new File(dbPath).getParentFile();
		System.out.println("Clean db directory for test...");
		delTree(dbDir);
		ODatabaseDocumentTx db = new ODatabaseDocumentTx("local:" + dbPath);
		db.create();
		db.close();

		System.out.println("Reopen it...");
		db = new ODatabaseDocumentTx("local:" + dbPath).open("admin", "admin");
		db.close();
		Assert.assertTrue(true);
	}

	public void dbOperations() {
		String dbPath = getDatabasePath();

		System.out.println("Using db = local:" + dbPath);
		File dbDir = new File(dbPath);
		System.out.println("Clean db directory for test...");
		delTree(dbDir);
		ODatabaseDocumentTx db = new ODatabaseDocumentTx("local:" + dbPath);
		db.create();
		db.close();

		System.out.println("Create OK!");
		db = new ODatabaseDocumentTx("local:" + dbPath).open("admin", "admin");
		System.out.println("Open OK!");
		Assert.assertTrue(db.exists());
		System.out.println("Exists OK!");
		db.delete();
		System.out.println("Delete OK!");
	}

	private boolean delTree(File directory) {
		if (directory.exists()) {
			File[] files = directory.listFiles();
			for (int i = 0; i < files.length; i++) {
				if (files[i].isDirectory()) {
					delTree(files[i]);
				} else {
					files[i].delete();
				}
			}
		}
		return directory.delete();
	}

	private String getDatabasePath() {
		String dbPath = System.getProperty("java.io.tmpdir");
		if (!dbPath.endsWith(File.separator))
			dbPath += "/";
		dbPath += "orientdb" + "/" + "test";
		return dbPath;
	}
}
