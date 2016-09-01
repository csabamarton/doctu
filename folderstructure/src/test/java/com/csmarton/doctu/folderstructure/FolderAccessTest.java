package com.csmarton.doctu.folderstructure;

import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.FileNotFoundException;
import java.net.URL;

import static org.junit.Assert.assertEquals;

public class FolderAccessTest {
	private FolderAccess folderAccess;
	private FolderAccess folderAccessForResult;

	@Before
	public void setUp() throws Exception
	{
		folderAccess = new FolderAccess();
		folderAccessForResult = new FolderAccess();
	}

	@Test
	public void shouldGetRootTreeItemWhenHavingAReadAbleRootWithWritableChildren()
			throws FileNotFoundException
	{
		folderAccess.getTreeBuilder().buildFromInputFile(getInputFile("folderaccess/input1.txt"));

		FolderAccess.TreeItem rootNode = folderAccess.rootNode;
		folderAccess.getTreeCutter().cutNonImportantParts();

		folderAccessForResult.getTreeBuilder().buildFromInputFile(getInputFile("folderaccess/result1.txt"));

		FolderAccess.TreeItem expectedRootNode = folderAccessForResult.rootNode;

		int expected = expectedRootNode.hashCode();
		int actual = rootNode.hashCode();

		assertEquals(expected, actual);
	}

	private File getInputFile(String path) throws FileNotFoundException
	{
		URL resource = this.getClass().getClassLoader().getResource(path);

		return new File(resource.getFile());
	}
}